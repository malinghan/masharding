# ShardingJDBC 分库分表设计方案

## 一、核心原理

ShardingJDBC 是一个轻量级的 Java 框架，以 JDBC 驱动的形式提供分库分表能力，应用程序无需改变原有的数据库访问方式。

### 工作流程

```
SQL 输入
  ↓
SQL 解析（Parser）       → 生成 AST（抽象语法树）
  ↓
SQL 路由（Router）       → 根据分片键计算目标库/表
  ↓
SQL 改写（Rewriter）     → 将逻辑表名替换为物理表名
  ↓
SQL 执行（Executor）     → 并发执行到多个数据源
  ↓
结果归并（Merger）       → 聚合、排序、分页结果
  ↓
返回结果
```

### 核心机制

**1. SQL 解析**
- 基于 ANTLR4 构建词法/语法分析器
- 将 SQL 解析为 AST，提取表名、条件、列名等元数据
- 识别分片键所在的 WHERE 条件

**2. 分片路由**
- 根据分片键的值，通过分片算法计算出目标数据源和目标表
- 支持精确路由（`=`）、范围路由（`BETWEEN`、`>`、`<`）、广播路由（无分片键）

**3. SQL 改写**
- 将逻辑 SQL 中的逻辑表名（如 `order`）替换为物理表名（如 `order_0`、`order_1`）
- 处理 `LIMIT` 分页改写（扩大 offset 和 limit 以保证归并正确性）
- 处理 `AVG` 改写为 `SUM + COUNT`

**4. 并发执行**
- 多个分片的 SQL 并发执行，利用线程池提升性能
- 支持同步/异步两种执行模式

**5. 结果归并**
- 流式归并：适合排序、分组场景，内存占用低
- 内存归并：将所有结果加载到内存后归并
- 装饰者归并：多种归并策略组合使用

---

## 二、基础组件

### 2.1 DataSource 层

```
ShardingSphereDataSource
  ├── 多个真实 DataSource（ds_0, ds_1, ...）
  ├── ShardingRuleConfiguration（分片规则）
  └── Properties（执行属性）
```

### 2.2 分片规则（ShardingRule）

| 组件 | 说明 |
|------|------|
| `TableRule` | 单张逻辑表的分片配置（数据节点、分片策略） |
| `ShardingStrategy` | 分片策略（标准、复合、Hint、不分片） |
| `ShardingAlgorithm` | 分片算法（取模、哈希、范围、时间等） |
| `KeyGenerateStrategy` | 分布式主键生成策略（Snowflake、UUID） |
| `BindingTableRule` | 绑定表规则，避免关联查询笛卡尔积 |
| `BroadcastTable` | 广播表，全库全表复制 |

### 2.3 分片策略类型

```java
// 1. 标准分片策略 - 单分片键
StandardShardingStrategyConfiguration
  → PreciseShardingAlgorithm  // 处理 = 和 IN
  → RangeShardingAlgorithm    // 处理 BETWEEN、>、<

// 2. 复合分片策略 - 多分片键
ComplexShardingStrategyConfiguration
  → ComplexKeysShardingAlgorithm

// 3. Hint 分片策略 - 强制路由，不依赖 SQL 中的分片键
HintShardingStrategyConfiguration
  → HintShardingAlgorithm

// 4. 不分片策略
NoneShardingStrategyConfiguration
```

### 2.4 内置分片算法

| 算法 | 类型 | 适用场景 |
|------|------|----------|
| `MOD` | 取模 | 均匀分布，适合数值型 ID |
| `HASH_MOD` | 哈希取模 | 字符串类型分片键 |
| `BOUNDARY_RANGE` | 范围 | 按数值范围分片 |
| `AUTO_INTERVAL` | 自动时间范围 | 按时间自动分片 |
| `INLINE` | 行表达式 | 简单场景快速配置 |
| `CLASS_BASED` | 自定义类 | 复杂业务逻辑 |

---

## 三、设计思路

### 3.1 分库策略

**垂直分库**：按业务模块拆分，不同业务放不同数据库
```
用户库 (user_db)    → user, user_profile
订单库 (order_db)   → order, order_item
商品库 (product_db) → product, category
```

**水平分库**：同一业务数据按规则分散到多个库
```
order_db_0  → user_id % 2 == 0 的订单
order_db_1  → user_id % 2 == 1 的订单
```

### 3.2 分表策略

**水平分表**：同一张表的数据按规则分散到多张物理表
```
order_0, order_1, ..., order_15  → order_id % 16
```

**分库 + 分表组合**：
```
ds_0.order_0   ds_0.order_1   ds_0.order_2   ds_0.order_3
ds_1.order_0   ds_1.order_1   ds_1.order_2   ds_1.order_3
```
共 2 库 × 4 表 = 8 个物理分片

### 3.3 分片键选择原则

1. **高基数**：分片键的值域要足够大，避免数据倾斜
2. **均匀分布**：数据能均匀分散到各分片
3. **查询频繁**：主要查询条件包含分片键，避免全路由
4. **不可变**：分片键的值一旦确定不应修改（修改需迁移数据）
5. **业务相关**：优先选择 `user_id`、`order_id` 等业务核心字段

### 3.4 绑定表（Binding Table）

关联查询时，主表和子表使用相同的分片键，确保关联数据在同一分片，避免跨库 JOIN。

```
order      → 分片键: order_id
order_item → 分片键: order_id（与 order 相同）

绑定后：order JOIN order_item 只在同一分片内执行
```

### 3.5 广播表（Broadcast Table）

数据量小、变更少、需要与分片表关联的配置表，在所有分库中保存完整副本。

```
province, city, category, config 等字典表 → 广播表
```

---

## 四、使用案例

### 4.1 Maven 依赖

```xml
<dependency>
    <groupId>org.apache.shardingsphere</groupId>
    <artifactId>shardingsphere-jdbc-core</artifactId>
    <version>5.4.1</version>
</dependency>
```

### 4.2 YAML 配置（推荐）

```yaml
dataSources:
  ds_0:
    dataSourceClassName: com.zaxxer.hikari.HikariDataSource
    driverClassName: com.mysql.cj.jdbc.Driver
    jdbcUrl: jdbc:mysql://localhost:3306/order_db_0
    username: root
    password: root
  ds_1:
    dataSourceClassName: com.zaxxer.hikari.HikariDataSource
    driverClassName: com.mysql.cj.jdbc.Driver
    jdbcUrl: jdbc:mysql://localhost:3306/order_db_1
    username: root
    password: root

rules:
  - !SHARDING
    tables:
      order:
        actualDataNodes: ds_${0..1}.order_${0..3}
        databaseStrategy:
          standard:
            shardingColumn: user_id
            shardingAlgorithmName: db_mod
        tableStrategy:
          standard:
            shardingColumn: order_id
            shardingAlgorithmName: table_mod
        keyGenerateStrategy:
          column: order_id
          keyGeneratorName: snowflake

      order_item:
        actualDataNodes: ds_${0..1}.order_item_${0..3}
        databaseStrategy:
          standard:
            shardingColumn: user_id
            shardingAlgorithmName: db_mod
        tableStrategy:
          standard:
            shardingColumn: order_id
            shardingAlgorithmName: table_mod

    bindingTables:
      - order, order_item

    broadcastTables:
      - province
      - category

    shardingAlgorithms:
      db_mod:
        type: MOD
        props:
          sharding-count: 2
      table_mod:
        type: MOD
        props:
          sharding-count: 4

    keyGenerators:
      snowflake:
        type: SNOWFLAKE

props:
  sql-show: true
```

### 4.3 Java 代码使用

```java
// 加载配置，获取 DataSource（与普通 JDBC 完全一致）
DataSource dataSource = YamlShardingSphereDataSourceFactory
    .createDataSource(new File("sharding.yaml"));

// 正常使用，框架自动路由
try (Connection conn = dataSource.getConnection();
     PreparedStatement ps = conn.prepareStatement(
         "INSERT INTO order (order_id, user_id, amount) VALUES (?, ?, ?)")) {
    ps.setLong(1, 1001L);
    ps.setLong(2, 2001L);
    ps.setBigDecimal(3, new BigDecimal("99.99"));
    ps.executeUpdate();
}

// 查询 - 有分片键，精确路由到单个分片
try (Connection conn = dataSource.getConnection();
     PreparedStatement ps = conn.prepareStatement(
         "SELECT * FROM order WHERE user_id = ? AND order_id = ?")) {
    ps.setLong(1, 2001L);
    ps.setLong(2, 1001L);
    ResultSet rs = ps.executeQuery();
}

// Hint 强制路由
HintManager hintManager = HintManager.getInstance();
hintManager.addDatabaseShardingValue("order", 0);  // 强制路由到 ds_0
hintManager.addTableShardingValue("order", 1);      // 强制路由到 order_1
// 执行 SQL...
hintManager.close();
```

### 4.4 Spring Boot 集成

```yaml
# application.yml
spring:
  datasource:
    driver-class-name: org.apache.shardingsphere.driver.ShardingSphereDriver
    url: jdbc:shardingsphere:classpath:sharding.yaml
```

```java
// 直接注入使用，与普通 JPA/MyBatis 无差异
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);
}
```

---

## 五、与其他方案的对比

### 5.1 三种模式概览

```
┌─────────────────────────────────────────────────────────────────┐
│  应用层分片（ShardingJDBC）                                       │
│  App → [ShardingJDBC] → DB_0, DB_1, DB_2                        │
├─────────────────────────────────────────────────────────────────┤
│  Proxy 中间件模式（ShardingSphere-Proxy / MyCat）                 │
│  App → [Proxy Server] → DB_0, DB_1, DB_2                        │
├─────────────────────────────────────────────────────────────────┤
│  分布式数据库（TiDB / OceanBase / CockroachDB）                   │
│  App → [分布式数据库集群（对外表现为单一数据库）]                   │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 详细对比

| 维度 | ShardingJDBC（应用层） | Proxy 中间件 | 分布式数据库 |
|------|----------------------|-------------|-------------|
| **部署方式** | JAR 包，嵌入应用进程 | 独立服务进程 | 独立集群 |
| **语言限制** | 仅 Java | 无限制（任意语言） | 无限制 |
| **性能** | 最高（无网络跳转） | 中（多一次网络跳转） | 中（内部网络开销） |
| **运维复杂度** | 低（随应用部署） | 中（需维护 Proxy 集群） | 高（需专业 DBA） |
| **SQL 兼容性** | 有限制（不支持部分复杂 SQL） | 有限制 | 高度兼容 |
| **跨语言支持** | 不支持 | 支持 | 支持 |
| **强一致事务** | 支持（本地事务 + XA） | 支持（XA） | 原生支持 |
| **扩容方式** | 需修改配置 + 数据迁移 | 需修改配置 + 数据迁移 | 自动弹性扩容 |
| **数据迁移** | 需手动处理 | 需手动处理 | 自动 |
| **成本** | 低 | 中 | 高 |
| **适用规模** | 中小规模 | 中大规模 | 超大规模 |
| **典型产品** | ShardingSphere-JDBC | ShardingSphere-Proxy、MyCat | TiDB、OceanBase、CockroachDB |

### 5.3 ShardingJDBC vs Proxy 中间件

**ShardingJDBC 优势：**
- 无额外网络开销，性能最优
- 部署简单，随应用启动，无需额外运维
- 与 Spring Boot、MyBatis、JPA 无缝集成

**ShardingJDBC 劣势：**
- 仅支持 Java 应用
- 分片逻辑与业务代码耦合在同一进程
- 多个应用实例各自持有数据库连接，连接数较多
- 升级需要重新部署应用

**Proxy 中间件优势：**
- 语言无关，任何语言的应用都可接入
- 集中管理分片规则，升级无需重启应用
- 连接数集中管理，对数据库连接压力小
- 对应用完全透明，迁移成本低

**Proxy 中间件劣势：**
- 多一次网络跳转，延迟略高
- Proxy 本身成为单点，需要高可用部署
- 增加运维复杂度

### 5.4 ShardingJDBC vs 分布式数据库

**分布式数据库优势：**
- 对应用完全透明，使用方式与单机数据库完全一致
- 自动弹性扩容，无需手动数据迁移
- 原生支持分布式事务和强一致性
- SQL 兼容性最好，支持复杂查询

**分布式数据库劣势：**
- 成本高（商业版授权费用高，开源版运维复杂）
- 需要专业 DBA 团队维护
- 部分场景性能不如经过精心设计的分片方案
- 技术栈绑定，迁移成本高

### 5.5 选型建议

```
业务规模小、Java 技术栈、追求性能
  → ShardingJDBC

多语言技术栈、希望对应用透明、中大规模
  → Proxy 中间件（ShardingSphere-Proxy）

超大规模数据、需要弹性扩容、有足够预算
  → 分布式数据库（TiDB / OceanBase）

混合场景
  → ShardingSphere 同时提供 JDBC 和 Proxy 两种模式，
    可根据不同服务选择不同接入方式
```

---

## 六、常见问题与限制

### 6.1 不支持的 SQL

```sql
-- 不支持跨库 JOIN（无绑定表关系时）
SELECT * FROM order o JOIN user u ON o.user_id = u.id

-- 不支持子查询中包含分片表
SELECT * FROM order WHERE order_id IN (SELECT order_id FROM order_item WHERE ...)

-- 不支持 UNION
SELECT * FROM order WHERE user_id = 1 UNION SELECT * FROM order WHERE user_id = 2
```

### 6.2 分页问题

跨分片分页需要改写 SQL，性能随页码增大而下降：
```sql
-- 原始 SQL
SELECT * FROM order ORDER BY create_time LIMIT 100000, 10

-- 改写后（每个分片都要查 100010 条）
SELECT * FROM order_0 ORDER BY create_time LIMIT 0, 100010
SELECT * FROM order_1 ORDER BY create_time LIMIT 0, 100010
-- 归并后取第 100001~100010 条
```

**解决方案**：使用游标分页（`WHERE id > last_id LIMIT 10`）替代 offset 分页。

### 6.3 分布式事务

```java
// XA 事务（强一致，性能较低）
@ShardingTransactionType(TransactionType.XA)
@Transactional
public void createOrder() { ... }

// BASE 事务（最终一致，性能高）
@ShardingTransactionType(TransactionType.BASE)
@Transactional
public void createOrder() { ... }
```

### 6.4 数据倾斜

避免热点分片键（如 `status`、`type` 等低基数字段），优先使用 `user_id`、`order_id` 等高基数字段。

---

## 七、本项目实践

本项目（kksharding）实现了 ShardingJDBC 核心机制的简化版本，包含：

- **分片引擎**：SQL 解析 → 路由 → 改写 → 执行 → 归并的完整流程
- **分片策略**：取模、哈希、范围等内置算法，支持自定义扩展
- **拦截器**：基于 MyBatis 插件机制的 SQL 拦截与改写
- **测试用例**：覆盖主要分片场景的集成测试

参考实现路径：
- 分片引擎：`src/main/java/.../engine/`
- 分片策略：`src/main/java/.../strategy/`
- SQL 拦截器：`src/main/java/.../interceptor/`
