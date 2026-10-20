# 交易整改技术栈核查

核查日期：2026-10-19；对照基线：`8e52a22`。核查范围包括POM差异、新增业务类的import、Mapper实现、配置与JAR依赖。

## 结论

业务主技术栈沿用原项目，但不能说所有新增工具都是原来用过的。新增依赖共6项：2项Flyway迁移组件，H2及3项Testcontainers测试组件。未新增业务数据库、消息中间件或替代ORM。

| 用途 | 本轮实现 | 与基线关系 |
|---|---|---|
| Java与Web框架 | Java21、Spring Boot3.5.8、Spring MVC、Lombok | 原有，版本未因本轮整改替换 |
| 数据访问 | MyBatis3.0.5 Starter、具名注解Mapper、原SQL Provider | 原有；生产源已无JdbcTemplate及原生JDBC业务访问 |
| 数据与缓存 | MySQL、Spring Data Redis、Lettuce、Redisson、Lua | 原有；MySQL负责预占，Redis保留为带版本的库存投影 |
| 消息 | Spring AMQP、RabbitTemplate、RabbitListener、RabbitMQ confirm/return/TTL/DLX | 原有框架与中间件，补齐可靠性机制 |
| 事务与恢复 | Spring事务管理器、Transactional、TransactionTemplate、Scheduled | 同一Spring框架；TransactionTemplate是新增用法，用于明确支付前后短事务，不是新事务框架 |
| 认证、支付、邮件、JSON | Sa-Token、支付宝SDK、Spring Mail、Jackson | 原有，未引入替代SDK或渠道 |
| 迁移工具 | flyway-core、flyway-mysql（11.7.2） | **新增运行时/维护依赖**：记录DDL版本、校验与显式增量迁移，避免重跑初始化SQL；自动迁移默认关闭，不替代MyBatis或MySQL |
| 本地事务测试 | H2，scope=test | **新增测试依赖**：快速验证回滚与状态流转，不用于实际业务存储，不进入应用JAR |
| 真实中间件测试 | Testcontainers junit-jupiter/mysql/rabbitmq，scope=test | **新增测试依赖**：自动创建和回收Docker隔离环境，不替换业务中间件，不进入应用JAR |
| 单元测试 | JUnit5、Mockito、Spring Boot Test | 基线已有测试Starter提供，本轮增加用例，不新增另一套单测框架 |

## 新设计不等于新技术栈

Outbox、库存预占记录、幂等请求键、限购guard、取消中状态、补偿退款和租约重试是实现模式与业务设计，使用现有MySQL、MyBatis、RabbitMQ和Spring组件完成，没有再增加一个中间件。

`Clock`、UUID、Map和record均为JDK标准库/语言能力。`spring-jdbc`仍会作为MyBatis/Spring事务的底层依赖出现在JAR中，这不表示Service还在使用JdbcTemplate。原有hibernate-validator属于参数校验，也不表示新增Hibernate ORM。

## 可核对证据

- `git diff 8e52a22 -- ticket-management/pom.xml`：只新增上述6项依赖，没有替换原有业务依赖或版本。
- 生产源码扫描：无 `JdbcTemplate`、`GeneratedKeyHolder`、`JdbcTradeOperations`、`prepareStatement`、`EntityManager`、Hibernate ORM或jOOQ数据访问实现。
- 新增9个Trade Mapper使用具名方法和 `#{}` 参数绑定，无通用任意SQL透传。
- JAR依赖包含Flyway；不包含H2、Testcontainers。测试中的JdbcTemplate仅用于建表、准备数据和独立断言，业务行为由真实MyBatis代理执行。

后续沿用根 `AGENTS.md`：优先现有栈；新增依赖必须说明用途、作用域及明确收益。原业务层已统一MyBatis，新增工具也应明确列出，不能统称为“完全没有新技术”。

## 本轮Docker验收

2026-10-19 19:45:23 完整构建成功：91项中90通过、1项原个人环境测试跳过，含10项真实MySQL/Redis/RabbitMQ及完整HTTP集成测试。所有服务使用测试隔离容器，不动原数据卷；详见 `mybatis-unification-validation.md`。
