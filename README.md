# Ticket Management：纯后端票务系统

这是一个以演出上架、用户抢票和订单支付为主线的 Java 后端项目。管理员维护城市、分类、演出、场次和票档；用户登录后发起抢票，系统异步建单，并处理付款、取消、超时释放、迟到付款退款及邮件通知。

项目使用 Java 21、Spring Boot 3.5.8、MyBatis、MySQL、Redis／Redisson、RabbitMQ 和 Sa-Token。支付宝 SDK 用于沙箱支付、查单、关单与退款；Spring Mail 用于通知。Flyway 是显式迁移工具，H2 和 Testcontainers 只用于测试；业务数据库访问统一走 MyBatis Mapper。仓库只有后端服务，没有前端售票页面。

## 从哪里开始

| 目的 | 文档 |
|---|---|
| 配置、构建和启动 | [模块 README](ticket-management/README.md) |
| 理解目录、模块职责和完整交易链路 | [项目导读](docs/project-walkthrough.md) |
| 按步骤复现售票与异常分支、核对当前验收范围 | [验收清单](docs/acceptance-checklist.md) |
| 阅读交易设计、迁移、状态与接口变化 | [交易整改说明](docs/transaction-hardening.md)、[核心接口](docs/transaction-core-api.md) |
| 查看隔离 HTTP、支付宝沙箱、SMTP 与持续压测证据 | [隔离售票](docs/full-sale-isolated-acceptance.md)、[支付宝](docs/alipay-sandbox-acceptance.md)、[邮件](docs/smtp-acceptance.md)、[压测](docs/performance/README.md) |
| 跟进 Redis/Lua 抢票协同重构（当前分支已接入，原数据库未迁移） | [协议与迁移](docs/redis-seckill-protocol.md)、[执行计划](docs/high-concurrency-refactor-plan.md)、[进度](docs/high-concurrency-refactor-progress.md)、[重构前基线](docs/high-concurrency-refactor-baseline.md) |

## 核心链路

1. 管理员创建可售的演出、场次和票档；用户登录并提交带 `requestKey` 的抢票请求。
2. Redisson用户分段锁协调短受理流程，Redis Lua原子预扣和限购；短事务保存申请及Outbox后返回编号。RabbitMQ消费者异步用版本和库存条件扣MySQL库存并创建订单；客户端通过申请查询取得订单ID。
3. 用户为订单创建支付流水。支付宝异步通知经过验签、应用 ID、流水和金额校验后才入账；浏览器同步回跳只提示查询支付状态。
4. 取消和超时处理先核对渠道。渠道状态未知时保留预占；确认关单后释放库存。已释放订单收到迟到付款时创建持久退款任务。
5. Outbox、Redis确认/释放、展示投影、邮件和退款任务通过幂等及恢复任务收敛。Redis负责前置配额，MySQL负责订单、持久库存与资金状态；缓存重建计入待建单申请，不能直接把数据库库存覆盖到配额。

## 当前验证边界

当前分支已开始V5 Redis预扣协议重构，专项与回归结果见[进度](docs/high-concurrency-refactor-progress.md)。下面2026-10-21的真实渠道和44.51次/秒属于重构前版本，不能直接作为本分支新架构的实测成绩；新版压测与真实渠道复验仍待后续阶段。

截至 2026-10-21，隔离 MySQL／Redis／RabbitMQ 中的完整售票 HTTP 流程、真实支付宝沙箱付款和异步回调、已存在未付款交易的项目关单、迟到付款退款与退款查询、两封真实 SMTP 邮件均已有验收记录。最新五分钟隔离压测处理 13,519 次请求、0 业务失败，实测 44.51 次／秒，停压时 Outbox 与邮件待处理为 0。**这不是 100 次／秒的成绩，也不是生产容量承诺。**具体条件、历史失败轮次和仍未实测的分支见[验收清单](docs/acceptance-checklist.md)。

交易写入和自动迁移默认关闭。新建环境与已有数据库的迁移步骤不同；启动前先阅读[模块 README](ticket-management/README.md)和[交易整改说明](docs/transaction-hardening.md)。不要在原数据库重跑初始化 SQL，也不要把 `.env`、Token、日志或构建产物提交到 Git。
