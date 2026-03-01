# Masharding - 数据库分片框架

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Masharding 是一个轻量级的数据库分片框架，基于 Spring Boot 和 MyBatis 实现，提供透明化的分库分表解决方案。

## 🌟 核心特性

- **透明分片**：业务代码无需感知分片逻辑，框架自动完成路由和 SQL 改写
- **灵活策略**：支持 Groovy 表达式定义分片规则
- **完整链路**：从 Mapper 代理 → 分片引擎 → SQL 拦截 → 数据源路由的全流程自动化
- **易于集成**：基于标准 Spring Boot 和 MyBatis 生态

## 🏗️ 架构设计

```
业务代码调用
    ↓
Mapper 代理层 (ShardingMapperFactoryBean)
    ↓
分片引擎 (StandardShardingEngine)
    ↓
SQL 拦截器 (SqlStatementInterceptor)
    ↓
数据源路由 (ShardingDataSource)
    ↓
物理数据库执行
```

## 🚀 快速开始

### 环境要求

- Java 17+
- Maven 3.6+
- MySQL 8.0+

### 配置示例

```yaml
spring:
  sharding:
    datasources:
      ds0:
        url: jdbc:mysql://localhost:3306/db0
        username: root
        password: password
      ds1:
        url: jdbc:mysql://localhost:3306/db1
        username: root
        password: password
    database-strategy:
      sharding-column: id
      algorithm-expression: "ds${id % 2}"
    table-strategies:
      user:
        sharding-column: id
        algorithm-expression: "user${id % 3}"
    actual-tables:
      user: [user0, user1, user2]
```

### 业务代码使用

```java
@RestController
public class UserController {
    
    @Autowired
    private UserMapper userMapper;
    
    @PostMapping("/user")
    public User createUser(@RequestBody User user) {
        // 业务代码完全透明，无需关心分片逻辑
        userMapper.insert(user);
        return user;
    }
    
    @GetMapping("/user/{id}")
    public User getUser(@PathVariable Long id) {
        return userMapper.findById(id);
    }
}
```

## 📖 版本演进

### v1.0 - 多数据源配置
- 基础的多数据源配置和手动切换功能

### v2.0 - 分片上下文
- 引入 ThreadLocal 传递分片结果

### v3.0 - 分片策略
- Groovy 表达式引擎支持动态分片规则

### v4.0 - SQL 解析
- Druid SQL Parser 解析 SQL 提取分片键

### v5.0 - MyBatis 拦截器
- SQL 语句自动改写功能

### v6.0 - Mapper 代理集成 ⭐ (**当前版本**)
- **完整链路打通**：业务代码完全透明化
- JDK 动态代理自动注入分片逻辑
- 一站式分片解决方案

## 🔧 核心组件

### ShardingMapperFactoryBean
MyBatis Mapper 代理工厂，负责：
- 拦截 Mapper 方法调用
- 提取 SQL 参数和 BoundSql
- 调用分片引擎计算路由
- 写入分片上下文

### StandardShardingEngine
分片引擎核心，负责：
- SQL 解析和分片键提取
- 调用分片策略计算目标数据源和表
- 返回完整的分片结果

### SqlStatementInterceptor
MyBatis 拦截器，负责：
- 从分片上下文获取目标 SQL
- 替换 BoundSql 中的逻辑 SQL 为物理 SQL

### ShardingDataSource
动态数据源路由，负责：
- 从分片上下文获取目标数据源
- 路由到正确的物理数据库连接

## 🧪 测试验证

### API 测试

```bash
# 插入数据（自动分片）
curl -X POST "http://localhost:8080/user" \
  -H "Content-Type: application/json" \
  -d '{"id":3,"name":"ma","age":20}'

# 查询数据（自动路由）
curl -X GET "http://localhost:8080/user/3"
```

### 预期分片规律

| ID | 数据源 | 物理表 | 计算规则 |
|----|--------|--------|----------|
| 1  | ds1    | user1  | ds${1%2}, user${1%3} |
| 2  | ds0    | user2  | ds${2%2}, user${2%3} |
| 3  | ds1    | user0  | ds${3%2}, user${3%3} |
| 4  | ds0    | user1  | ds${4%2}, user${4%3} |

## 📁 项目结构

```
src/main/java/com/malinghan/masharding/
├── config/                 # 配置类
│   ├── ShardingAutoConfiguration.java
│   └── ShardingProperties.java
├── context/                # 分片上下文
│   ├── ShardingContext.java
│   └── ShardingResult.java
├── controller/             # 控制器
│   └── TestController.java
├── datasource/             # 数据源
│   └── ShardingDataSource.java
├── engine/                 # 分片引擎
│   ├── ShardingEngine.java
│   └── StandardShardingEngine.java
├── interceptor/            # 拦截器
│   └── SqlStatementInterceptor.java
├── mapper/                 # Mapper 相关
│   ├── ShardingMapperFactoryBean.java
│   └── UserMapper.java
├── model/                  # 数据模型
│   └── User.java
├── strategy/               # 分片策略
│   ├── HashShardingStrategy.java
│   ├── InlineExpressionParser.java
│   └── ShardingStrategy.java
├── MashardingApplication.java
└── TestRunner.java
```

## 🛠️ 技术栈

- **核心框架**：Spring Boot 3.3.2
- **持久层**：MyBatis 3.5.14
- **连接池**：Druid 1.2.23
- **SQL 解析**：Druid SQL Parser
- **脚本引擎**：Groovy 4.0.24
- **构建工具**：Maven 3.6+

## 📝 开发规范

### 分片策略表达式
```groovy
// 数据源分片：按 ID 奇偶分布
"ds${id % 2}"

// 表分片：按 ID 取模分布
"user${id % 3}"

// 复合分片：组合多个字段
"ds${userId % 2}_table${orderId % 4}"
```

### 异常处理
- 主键冲突返回 409 Conflict 状态码
- 分片计算异常记录详细日志
- 网络异常具备重试机制

## 🤝 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

## 📞 联系方式

- 作者：马凌汉
- 邮箱：[linghan.ma@gmail.com]
- 项目地址：[您的 GitHub 地址]

---
> 💡 **提示**：更多详细文档请参考 [DESIGN.md](DESIGN.md) 和各版本说明文件