# masharding 架构设计文档

## 1. 项目介绍

masharding 是一个轻量级的 **数据库分库分表中间件**，基于 Spring Boot + MyBatis 实现，通过拦截 MyBatis 的 SQL 执行流程，将逻辑 SQL 路由到实际的物理数据库和表。

项目定位是教学向的最小可用实现，帮助开发者理解 ShardingSphere 等成熟框架的核心原理。

### 技术栈

| 层次 | 技术 |
|------|------|
| 框架 | Spring Boot 3.3.2 |
| ORM | MyBatis 3.0.3 (mybatis-spring-boot-starter) |
| 连接池 | Druid 1.2.23 |
| SQL 解析 | Druid SQL Parser |
| 分片表达式 | Apache Groovy 4.0.9 |
| 工具库 | Google Guava 33.0.0 |
| 语言 | Java 17 |
| 构建 | Maven |

---

## 2. 项目架构

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      Application Layer                       │
│              UserMapper / OrderMapper (MyBatis)              │
└──────────────────────────┬──────────────────────────────────┘
                           │ @MapperScan(factoryBean=ShardingMapperFactoryBean)
┌──────────────────────────▼──────────────────────────────────┐
│                  ShardingMapperFactoryBean                    │
│         (JDK Proxy 拦截 Mapper 方法调用，提前计算分片)          │
└──────────┬───────────────────────────────┬──────────────────┘
           │ engine.sharding(sql, params)   │ ShardingContext.set(result)
┌──────────▼──────────┐         ┌──────────▼──────────────────┐
│  StandardSharding   │         │      ShardingContext         │
│      Engine         │         │   (ThreadLocal 传递结果)      │
│  ┌───────────────┐  │         └──────────┬──────────────────┘
│  │ SQL Parser    │  │                    │
│  │ (Druid)       │  │         ┌──────────▼──────────────────┐
│  └───────────────┘  │         │    SqlStatementInterceptor   │
│  ┌───────────────┐  │         │  (MyBatis Plugin 替换逻辑SQL) │
│  │HashSharding   │  │         └──────────┬──────────────────┘
│  │Strategy       │  │                    │
│  │(Groovy表达式) │  │         ┌──────────▼──────────────────┐
│  └───────────────┘  │         │     ShardingDataSource       │
└─────────────────────┘         │  (路由到目标物理数据源)        │
                                └──────────┬──────────────────┘
                                           │
                          ┌────────────────┴────────────────┐
                          │                                  │
                     ┌────▼────┐                       ┌────▼────┐
                     │   ds0   │                       │   ds1   │
                     │ (db0)   │                       │ (db1)   │
                     └─────────┘                       └─────────┘
```

### 2.2 核心概念

#### 逻辑表 vs 物理表

> 类比：逻辑表是"快递单号"，物理表是"实际仓库货架"。你只需要知道单号，系统自动找到货架。

- **逻辑表**：代码中操作的表名，如 `user`、`t_order`
- **物理表**：数据库中真实存在的表，如 `ds0.user0`、`ds1.t_order1`
- **actualDataNodes**：逻辑表到物理节点的映射声明

#### 分库策略 vs 分表策略

> 类比：先决定把货物放哪个仓库（分库），再决定放仓库里哪个货架（分表）。

- **databaseStrategy**：根据分片列决定路由到哪个数据源（`ds0` 或 `ds1`）
- **tableStrategy**：根据分片列决定路由到哪张物理表（`user0`、`user1`...）

#### 分片表达式

> 类比：一个简单的数学公式，输入 id，输出目标位置。

```yaml
algorithmExpression: ds${id % 2}   # id=3 → ds1
algorithmExpression: user${id % 3} # id=3 → user0
```

表达式由 Groovy 动态执行，支持任意 Groovy 语法。

### 2.3 数据流图

```
Mapper.insert(user)
    │
    ▼
ShardingMapperFactoryBean (JDK Proxy)
    │  1. 从 MappedStatement 获取 BoundSql
    │  2. 提取参数列表
    │  3. 调用 engine.sharding(sql, params)
    │
    ▼
StandardShardingEngine
    │  1. Druid 解析 SQL → 获取逻辑表名 + 分片列值
    │  2. databaseStrategy.doSharding() → 目标 ds
    │  3. tableStrategy.doSharding()   → 目标 table
    │  4. 替换 SQL 中的逻辑表名为物理表名
    │  返回 ShardingResult(targetDs, targetSql)
    │
    ▼
ShardingContext.set(result)   ← ThreadLocal 存储
    │
    ▼
method.invoke(proxy, args)    ← 继续执行原始 MyBatis 流程
    │
    ▼
SqlStatementInterceptor (MyBatis Plugin)
    │  从 ShardingContext 取出 result
    │  用 Unsafe 将 BoundSql.sql 替换为 targetSql
    │
    ▼
ShardingDataSource (AbstractRoutingDataSource)
    │  determineCurrentLookupKey() 返回 targetDs
    │  路由到对应的 Druid 连接池
    │
    ▼
物理数据库执行
```

### 2.4 设计模式

| 模式 | 应用位置 | 说明 |
|------|----------|------|
| **代理模式 (Proxy)** | `ShardingMapperFactoryBean` | JDK 动态代理包装 MyBatis Mapper，在方法调用前插入分片逻辑 |
| **策略模式 (Strategy)** | `ShardingStrategy` / `HashShardingStrategy` | 分片算法抽象为接口，支持替换不同策略 |
| **模板方法 (Template Method)** | `AbstractRoutingDataSource` | Spring 提供的抽象类，子类只需实现 `determineCurrentLookupKey()` |
| **拦截器模式 (Interceptor)** | `SqlStatementInterceptor` | MyBatis Plugin 机制，拦截 `StatementHandler.prepare` 替换 SQL |
| **ThreadLocal 上下文传递** | `ShardingContext` | 在同一线程的 Proxy → Plugin → DataSource 调用链中传递分片结果 |
| **工厂 Bean (FactoryBean)** | `ShardingMapperFactoryBean` | 扩展 MyBatis 的 `MapperFactoryBean`，定制 Mapper 实例创建过程 |

---

## 3. Quick Start

### 3.1 准备数据库

创建两个数据库 `db0`、`db1`，并在各库中建表：

```sql
-- db0 和 db1 中分别执行
CREATE TABLE user0 (id INT PRIMARY KEY, name VARCHAR(50), age INT);
CREATE TABLE user1 (id INT PRIMARY KEY, name VARCHAR(50), age INT);
CREATE TABLE user2 (id INT PRIMARY KEY, name VARCHAR(50), age INT);

CREATE TABLE t_order0 (id INT PRIMARY KEY, uid INT, price DOUBLE);
CREATE TABLE t_order1 (id INT PRIMARY KEY, uid INT, price DOUBLE);
```

### 3.2 配置 application.yml

```yaml
spring:
  sharding:
    datasources:
      ds0:
        url: jdbc:mysql://127.0.0.1:3306/db0?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
        username: root
        password:
      ds1:
        url: jdbc:mysql://127.0.0.1:3306/db1?...
        username: root
        password:
    tables:
      user:
        actualDataNodes: ds0.user0,ds0.user1,ds0.user2,ds1.user0,ds1.user1,ds1.user2
        databaseStrategy:
          shardingColumn: id
          algorithmExpression: ds${id % 2}   # 偶数→ds0，奇数→ds1
        tableStrategy:
          shardingColumn: id
          algorithmExpression: user${id % 3} # 余数决定 user0/1/2
      t_order:
        actualDataNodes: ds0.t_order0,ds0.t_order1,ds1.t_order0,ds1.t_order1
        databaseStrategy:
          shardingColumn: uid
          algorithmExpression: ds${uid % 2}
        tableStrategy:
          shardingColumn: id
          algorithmExpression: t_order${id % 2}
```

### 3.3 启动类配置

```java
@SpringBootApplication
@Import(ShardingAutoConfiguration.class)
@MapperScan(
    value = "your.mapper.package",
    factoryBean = ShardingMapperFactoryBean.class  // 关键：替换默认 FactoryBean
)
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### 3.4 编写 Mapper（与普通 MyBatis 完全一致）

```java
@Mapper
public interface UserMapper {
    @Insert("insert into user (id, name, age) values (#{id}, #{name}, #{age})")
    int insert(User user);

    @Select("select * from user where id = #{id}")
    User findById(int id);
}
```

### 3.5 运行

```bash
mvn spring-boot:run
```

---

## 4. 优点与不足

### 优点

- **实现极简**：核心代码不足 300 行，逻辑清晰，适合学习分库分表原理
- **无侵入性**：Mapper 接口与普通 MyBatis 写法完全相同，业务代码零改动
- **表达式灵活**：分片规则使用 Groovy 表达式，支持任意计算逻辑，无需实现 Java 接口
- **标准扩展点**：充分利用 Spring（`AbstractRoutingDataSource`）和 MyBatis（Plugin、FactoryBean）的官方扩展机制，而非 hack 框架内部
- **ThreadLocal 隔离**：分片上下文通过 ThreadLocal 传递，线程安全，不影响并发

### 不足

- **不支持多表 JOIN**：`StandardShardingEngine` 明确抛出异常拒绝多表查询
- **不支持范围查询路由**：只支持等值条件分片，`WHERE id > 100` 无法精确路由
- **不支持广播表/绑定表**：没有全局表、关联表等高级概念
- **ThreadLocal 未清理**：`ShardingContext` 使用后未调用 `remove()`，在线程池场景下存在内存泄漏风险
- **Groovy 脚本缓存非线程安全**：`InlineExpressionParser.SCRIPTS` 使用 `HashMap` 而非 `ConcurrentHashMap`，高并发下存在竞态条件
- **SQL 替换使用 Unsafe**：`SqlStatementInterceptor` 通过 `sun.misc.Unsafe` 修改 `BoundSql` 的 final 字段，依赖 JVM 内部实现，在高版本 JDK 中可能受模块系统限制
- **仅支持单分片键**：每个策略只能配置一个 `shardingColumn`，不支持复合分片键
- **无读写分离**：没有主从路由能力
- **缺少连接管理**：没有分布式事务支持