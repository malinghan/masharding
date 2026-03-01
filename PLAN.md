# masharding 从 0 到 1 开发计划

> 每个版本聚焦一个核心功能点，版本间递进，每版本均可独立运行验证。

---

## 版本路线图总览

```
v1.0  多数据源配置与切换          ← 地基：能连多个库
v2.0  分片上下文 ThreadLocal      ← 管道：线程内传递路由结果
v3.0  分片策略与表达式引擎         ← 大脑：计算去哪个库/表
v4.0  SQL 解析与引擎集成           ← 眼睛：读懂 SQL 拿到分片值
v5.0  MyBatis 拦截器替换 SQL       ← 手术刀：偷偷换掉逻辑 SQL
v6.0  MapperFactoryBean 代理集成   ← 完整闭环：全链路打通
──────────────────────────────────── 已有功能完成线
v7.0  ThreadLocal 内存泄漏修复     ← 稳定性
v8.0  Groovy 脚本缓存线程安全      ← 稳定性
v9.0  广播表支持                   ← 新功能
v10.0 范围查询路由                 ← 新功能
v11.0 读写分离                     ← 新功能
v12.0 分布式 ID 生成               ← 新功能
```

---

## v1.0 — 多数据源配置与动态切换

### 目标

> 类比：搭好"多个仓库"，并能按名字找到对应仓库的大门。

实现从 YAML 配置读取多个数据源，用 Spring 的 `AbstractRoutingDataSource` 管理，通过一个 key 动态切换到目标数据源。此版本没有任何分片逻辑，只是手动指定 key 来切换数据源。

### 涉及文件

- `ShardingProperties`：读取 `spring.sharding.datasources` 配置
- `ShardingDataSource`：继承 `AbstractRoutingDataSource`，`determineCurrentLookupKey()` 从固定变量读 key
- `ShardingAutoConfiguration`：注册 Bean

### 功能流程图

```
application.yml
  spring.sharding.datasources:
    ds0: url/username/password
    ds1: url/username/password
        │
        ▼
ShardingProperties (读取配置)
        │
        ▼
ShardingDataSource (构造时创建 Druid 连接池 Map)
        │
        ▼
AbstractRoutingDataSource.determineCurrentLookupKey()
        │  返回 "ds0" 或 "ds1"（此版本硬编码）
        ▼
路由到对应物理数据库连接
```

### 测试流程

1. 准备 `db0`、`db1` 两个数据库，各建一张 `test` 表并插入不同数据
2. 启动应用，在 `ApplicationRunner` 中手动设置 key = "ds0"，执行查询，观察返回 db0 的数据
3. 切换 key = "ds1"，再次查询，观察返回 db1 的数据
4. 控制台打印 `determineCurrentLookupKey = ds0 / ds1` 确认路由正确

---

## v2.0 — 分片上下文：ThreadLocal 传递路由结果

### 目标

> 类比：给每个快递员（线程）发一张"便利贴"，上面写着"这次去哪个仓库"，全程揣在口袋里，不影响其他快递员。

引入 `ShardingContext`，用 ThreadLocal 在同一请求线程内传递分片结果，替代 v1.0 的硬编码 key。

### 涉及文件

- `ShardingResult`：封装 `targetDataSourceName` + `targetSqlStatement`
- `ShardingContext`：ThreadLocal 的 get/set 封装
- `ShardingDataSource`：`determineCurrentLookupKey()` 改为从 `ShardingContext` 读取

### 功能流程图

```
业务代码调用前
  ShardingContext.set(new ShardingResult("ds1", sql))
        │
        ▼  (同一线程内)
MyBatis 执行 SQL
        │
        ▼
ShardingDataSource.determineCurrentLookupKey()
        │  ShardingContext.get().getTargetDataSourceName()
        ▼
路由到 ds1
```

### 测试流程

1. 在 `ApplicationRunner` 中手动调用 `ShardingContext.set(new ShardingResult("ds0", ""))`
2. 执行 UserMapper 查询，观察路由到 ds0
3. 换成 "ds1" 再执行，观察路由到 ds1
4. 验证两个线程并发设置不同 key 时互不干扰（起两个线程分别设置 ds0/ds1 并查询）

---

## v3.0 — 分片策略与 Groovy 表达式引擎

### 目标

> 类比：制定"分拣规则"——快递单号末位是偶数去 A 仓库，奇数去 B 仓库。规则写成公式，随时可换。

实现 `ShardingStrategy` 接口和 `HashShardingStrategy`，用 Groovy 动态执行 `ds${id % 2}` 这类内联表达式，根据分片列的值计算出目标库/表名。

### 涉及文件

- `ShardingStrategy`：接口，`doSharding(availableTargets, logicTable, params) → String`
- `HashShardingStrategy`：读取 `shardingColumn` + `algorithmExpression`，调用 Groovy 执行
- `InlineExpressionParser`：封装 GroovyShell，解析并执行内联表达式

### 功能流程图

```
配置: algorithmExpression = "ds${id % 2}"
      shardingColumn = "id"
        │
        ▼
HashShardingStrategy.doSharding(
    availableTargets=["ds0","ds1"],
    params={"id": 3}
)
        │
        ▼
InlineExpressionParser
  1. handlePlaceHolder: "ds${id % 2}"
  2. evaluateClosure: {it -> "ds${id % 2}"}
  3. closure.setProperty("id", 3)
  4. closure.call() → "ds1"
        │
        ▼
返回 "ds1"
```

### 测试流程

1. 编写独立测试方法（无需数据库），直接 new `HashShardingStrategy`
2. 传入 `id=0,1,2,3,4,5`，验证 `ds${id%2}` 分别返回 `ds0,ds1,ds0,ds1,ds0,ds1`
3. 传入 `id=0~5`，验证 `user${id%3}` 返回 `user0,user1,user2,user0,user1,user2`
4. 控制台打印每次计算结果，肉眼确认规律正确

---

## v4.0 — SQL 解析与分片引擎

### 目标

> 类比：给中间件装上"眼睛"——能读懂 SQL，从中提取出"按哪个字段分片"以及"字段值是多少"。

实现 `ShardingEngine` 接口和 `StandardShardingEngine`，用 Druid SQL Parser 解析 SQL，提取逻辑表名和分片列值，结合 v3.0 的策略计算出目标库和目标表，返回 `ShardingResult`。

### 涉及文件

- `ShardingEngine`：接口，`sharding(sql, args) → ShardingResult`
- `StandardShardingEngine`：
  - INSERT：从 `SQLInsertStatement` 提取列名和参数值
  - SELECT/UPDATE/DELETE：用 `MySqlSchemaStatVisitor` 提取表名和 WHERE 条件值

### 功能流程图

```
engine.sharding("insert into user(id,name,age) values(?,?,?)", [3,"ma",20])
        │
        ▼
Druid SQLUtils.parseSingleMysqlStatement(sql)
        │
        ├─ INSERT → SQLInsertStatement
        │     提取列名列表: [id, name, age]
        │     与 args 对齐: {id:3, name:"ma", age:20}
        │
        └─ SELECT/UPDATE/DELETE → MySqlSchemaStatVisitor
              提取表名: user
              提取条件: {id:3}
        │
        ▼
databaseStrategy.doSharding(["ds0","ds1"], "user", {id:3}) → "ds1"
tableStrategy.doSharding(["user0","user1","user2"], "user", {id:3}) → "user0"
        │
        ▼
ShardingResult(
  targetDataSourceName = "ds1",
  targetSqlStatement   = "insert into user0(id,name,age) values(?,?,?)"
)
```

### 测试流程

1. 直接 new `StandardShardingEngine(properties)`，不启动 Spring
2. 测试 INSERT：`sharding("insert into user(id,name,age) values(?,?,?)", [3,"ma",20])`，验证返回 `ds1` + `user0`
3. 测试 SELECT：`sharding("select * from user where id=?", [4])`，验证返回 `ds0` + `user1`
4. 打印 `ShardingResult`，确认 targetSql 中逻辑表名已被替换为物理表名

---

## v5.0 — MyBatis 拦截器替换逻辑 SQL

### 目标

> 类比：SQL 发出去之前，有个"审查员"把快递单上的"逻辑地址"偷偷换成"实际地址"，司机（数据库驱动）只看到真实地址。

实现 `SqlStatementInterceptor`，作为 MyBatis Plugin 拦截 `StatementHandler.prepare`，从 `ShardingContext` 取出目标 SQL，用 `Unsafe` 替换 `BoundSql` 中的 sql 字段。

### 涉及文件

- `SqlStatementInterceptor`：`@Intercepts` 注解声明拦截点，`intercept()` 方法替换 SQL
- `ShardingAutoConfiguration`：注册 `SqlStatementInterceptor` Bean（MyBatis 自动识别 Interceptor Bean）

### 功能流程图

```
MyBatis 准备执行 SQL
        │
        ▼
SqlStatementInterceptor.intercept(invocation)
        │
        ├─ ShardingContext.get() → ShardingResult
        │
        ├─ 若 result != null 且 sql 不同:
        │     BoundSql boundSql = handler.getBoundSql()
        │     Unsafe.putObject(boundSql, sqlFieldOffset, targetSql)
        │     ← BoundSql.sql 字段被替换为物理表 SQL
        │
        ▼
invocation.proceed()  ← 继续执行，数据库收到的是物理 SQL
```

### 测试流程

1. 在 `ApplicationRunner` 中手动：
   - `ShardingContext.set(new ShardingResult("ds0", "select * from user1 where id=?"))`
   - 调用 `userMapper.findById(1)`（原 SQL 是 `select * from user where id=?`）
2. 开启 MyBatis SQL 日志，观察实际执行的 SQL 是否变成了 `user1` 版本
3. 验证不设置 ShardingContext 时，SQL 不被替换，走原始逻辑

---

## v6.0 — MapperFactoryBean 代理：全链路打通

### 目标

> 类比：在 Mapper 方法的"门口"加一个保安，每次调用前先算好"去哪个仓库哪个货架"，写在便利贴上，然后放行。

实现 `ShardingMapperFactoryBean`，用 JDK 动态代理包装 MyBatis Mapper，在方法调用前提取 BoundSql 参数、调用 Engine 计算分片结果、写入 ShardingContext，完成 v1.0~v5.0 所有组件的串联。

### 涉及文件

- `ShardingMapperFactoryBean`：继承 `MapperFactoryBean`，重写 `getObject()` 返回代理
- `MashardingApplication`：`@MapperScan` 指定 `factoryBean = ShardingMapperFactoryBean.class`

### 功能流程图（完整链路）

```
userMapper.insert(new User(3, "ma", 20))
        │  (JDK Proxy 拦截)
        ▼
ShardingMapperFactoryBean 内部 InvocationHandler
  1. mapperId = "UserMapper.insert"
  2. MappedStatement → BoundSql → sql="insert into user..."
  3. getParams(boundSql, args) → Object[]{3, "ma", 20}
  4. engine.sharding(sql, params) → ShardingResult("ds1", "insert into user0...")
  5. ShardingContext.set(result)
        │
        ▼
method.invoke(originalProxy, args)  ← 触发真实 MyBatis 执行
        │
        ▼
SqlStatementInterceptor              ← 替换 BoundSql.sql 为 "insert into user0..."
        │
        ▼
ShardingDataSource                   ← determineCurrentLookupKey() = "ds1"
        │
        ▼
ds1 数据库执行 "insert into user0(id,name,age) values(3,'ma',20)"
```

### 测试流程

1. 启动完整应用，执行 `ApplicationRunner` 中的 `testUser(3)`
2. 观察控制台输出：
   - `target db.table = ds1.user0`（Engine 计算结果）
   - `determineCurrentLookupKey = ds1`（DataSource 路由）
   - MyBatis 日志显示实际 SQL 为 `insert into user0`
3. 用 MySQL 客户端连接 db1，查询 `user0` 表，确认数据写入正确
4. 对 id=1~6 循环测试，验证分布到 ds0/ds1 的 user0/user1/user2 符合预期规律

---

## v7.0 — 修复 ThreadLocal 内存泄漏

### 目标

在 `ShardingMapperFactoryBean` 的代理方法执行完成后（无论成功或异常），调用 `ShardingContext.remove()` 清理 ThreadLocal，避免线程池复用时上一次请求的分片结果污染下一次请求。

### 实现要点

```java
// ShardingContext 新增
public static void remove() {
    LOCAL.remove();
}

// ShardingMapperFactoryBean InvocationHandler 中
try {
    ShardingContext.set(result);
    return method.invoke(proxy, args);
} finally {
    ShardingContext.remove();  // 新增
}
```

### 测试流程

1. 用线程池提交 100 个并发任务，每个任务随机路由到 ds0 或 ds1
2. 验证每个任务路由结果与预期一致，无串扰
3. 在任务中故意抛出异常，验证 finally 块确保 ThreadLocal 被清理

---

## v8.0 — Groovy 脚本缓存线程安全

### 目标

将 `InlineExpressionParser.SCRIPTS` 从 `HashMap` 改为 `ConcurrentHashMap`，消除高并发下的竞态条件。

### 实现要点

```java
// 修改前
private static final Map<String, Script> SCRIPTS = new HashMap();

// 修改后
private static final Map<String, Script> SCRIPTS = new ConcurrentHashMap<>();
```

### 测试流程

1. 启动 100 个线程同时首次执行相同表达式（触发 parse + put）
2. 验证无 `ConcurrentModificationException` 或数据错乱
3. 验证缓存命中后性能无回退

---

## v9.0 — 广播表（全局表）支持

### 目标

> 类比：字典表就像"公告栏"，每个仓库都贴一份，查的时候随便找哪个仓库都行，写的时候要同时更新所有仓库。

支持配置广播表（如 `dict`、`province`），读操作路由到任意一个数据源，写操作广播到所有数据源。

### 实现要点

- `ShardingProperties` 新增 `broadcastTables: [dict, province]`
- `StandardShardingEngine` 识别广播表，写操作返回多个 `ShardingResult`
- `ShardingMapperFactoryBean` 对广播表写操作循环执行到所有数据源

### 功能流程图

```
dictMapper.insert(dict)
        │
        ▼
Engine 识别 dict 为广播表
        │
        ├─ 返回 [ShardingResult("ds0", sql), ShardingResult("ds1", sql)]
        │
        ▼
ShardingMapperFactoryBean 循环执行
  → ds0 执行 insert
  → ds1 执行 insert
```

---

## v10.0 — 范围查询路由（Range Sharding）

### 目标

> 类比：不只能按"门牌号精确找人"，还能按"某个街区范围找人"——`WHERE id BETWEEN 100 AND 200` 能路由到所有可能包含该范围的分片。

支持范围条件（`>`、`<`、`BETWEEN`）的多分片路由，返回候选分片列表，对每个候选分片执行查询后合并结果。

### 实现要点

- `ShardingEngine` 接口新增 `shardingMulti(sql, args) → List<ShardingResult>`
- `StandardShardingEngine` 对范围条件枚举所有命中分片
- `ShardingMapperFactoryBean` 对多结果执行并合并（List 类型返回值）

---

## v11.0 — 读写分离

### 目标

> 类比：写操作找"主厨"（主库），读操作找"帮厨"（从库），主厨忙的时候帮厨可以分担读的压力。

在分库分表路由之上叠加读写分离：写操作（INSERT/UPDATE/DELETE）路由到主库，读操作（SELECT）路由到从库。

### 实现要点

- `ShardingProperties` 新增每个 ds 的 master/slave 配置
- `StandardShardingEngine` 根据 SQL 类型在 `ShardingResult` 中标记 read/write
- `ShardingDataSource` 根据标记选择主库或从库连接

### 功能流程图

```
SQL 类型判断
  INSERT/UPDATE/DELETE → master
  SELECT              → slave (round-robin 负载均衡)
        │
        ▼
ShardingResult 携带 role=MASTER/SLAVE
        │
        ▼
ShardingDataSource 按 "ds1-master" / "ds1-slave0" 路由
```

---

## v12.0 — 分布式 ID 生成

### 目标

> 类比：分库分表后每个仓库自己编货架号会重复，需要一个"全局编号机"保证所有仓库的货架号唯一。

内置雪花算法（Snowflake）ID 生成器，在 INSERT 时若主键为 0 或 null，自动填充全局唯一 ID，避免分库后自增 ID 冲突。

### 实现要点

- 新增 `SnowflakeIdGenerator`：基于时间戳 + 机器 ID + 序列号生成 64 位 long
- `ShardingProperties` 支持配置 `workerId`
- `StandardShardingEngine` 在处理 INSERT 时检测主键，自动注入生成的 ID

---

## 版本依赖关系

```
v1.0 ──► v2.0 ──► v3.0 ──► v4.0 ──► v5.0 ──► v6.0
                                                 │
                              ┌──────────────────┤
                              │                  │
                             v7.0               v8.0
                              │
                    ┌─────────┼──────────┐
                    │         │          │
                   v9.0     v10.0      v11.0
                                         │
                                       v12.0
```

---

## 各版本新增文件速查

| 版本 | 新增/修改文件 |
|------|-------------|
| v1.0 | `ShardingProperties`, `ShardingDataSource`, `ShardingAutoConfiguration` |
| v2.0 | `ShardingResult`, `ShardingContext`，修改 `ShardingDataSource` |
| v3.0 | `ShardingStrategy`, `HashShardingStrategy`, `InlineExpressionParser` |
| v4.0 | `ShardingEngine`, `StandardShardingEngine` |
| v5.0 | `SqlStatementInterceptor`，修改 `ShardingAutoConfiguration` |
| v6.0 | `ShardingMapperFactoryBean`，修改启动类 |
| v7.0 | 修改 `ShardingContext`（+remove），修改 `ShardingMapperFactoryBean`（+finally） |
| v8.0 | 修改 `InlineExpressionParser`（HashMap→ConcurrentHashMap） |
| v9.0 | 修改 `ShardingProperties`、`StandardShardingEngine`、`ShardingMapperFactoryBean` |
| v10.0 | 修改 `ShardingEngine`（新增 shardingMulti）、`StandardShardingEngine`、`ShardingMapperFactoryBean` |
| v11.0 | 修改 `ShardingProperties`、`ShardingResult`（+role）、`ShardingDataSource`、`StandardShardingEngine` |
| v12.0 | 新增 `SnowflakeIdGenerator`，修改 `StandardShardingEngine` |