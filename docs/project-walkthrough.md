# 项目导读：从代码定位到售票闭环

本文按当前代码组织，而不是按开发时间讲解。项目入口位于 `ticket-management` 模块；仓库根目录的 `docs` 保存设计、迁移和验收证据。代码是纯后端，支付宝收银台由支付宝提供，项目没有自建支付前端。

## 技术与目录

| 位置 | 职责 | 先看什么 |
|---|---|---|
| `src/main/java/.../controller`、`dto`、`vo` | HTTP 入参、鉴权和返回值 | `SeckillController`、`PaymentController`、`OrderController` |
| `serviceimpl` | 演出维护、认证、订单、支付入口等业务编排 | `EventServiceImpl`、`TicketTierServiceImpl`、`SeckillServiceImpl`、`OrderServiceImpl`、`PaymentServiceImpl` |
| `trade/core` | 库存预占、限购、异步落单、取消与支付状态机 | `TradeOperationsImpl` |
| `trade/payment` | 支付流水、渠道查单／关单、退款与回调审计 | `PaymentCoordinator`、`RefundService`、`PaymentNotificationAudit`、`AlipayPaymentGateway` |
| `trade/messaging` | Outbox 发布、RabbitMQ 消费、截止时间、库存投影和任务恢复 | `OutboxPublisher`、`TradeEventConsumer`、`TradeRecovery`、`NotificationTasks` |
| `mapper` | MyBatis SQL、条件更新和行锁 | `TradeCoreMapper`、`TradePaymentMapper`、`TradeOutboxMapper`、`TradeRefundMapper` |
| `src/main/resources/db/migration` | V1～V4 数据库结构与回填 | V1 旧结构、V2 交易表、V3 旧订单回填、V4 Outbox 索引 |
| `src/test/java/.../trade` | 交易单测及隔离中间件、HTTP 集成测试 | `TradeApplicationIT`、`TradeInfrastructureIT`、`PaymentTransactionTest` |

MySQL 是库存、订单、支付和任务状态的最终依据；Redis／Redisson用于缓存、库存投影及并发辅助，不决定最终库存。RabbitMQ 承载异步建单、截止时间等事件。Sa-Token 负责登录态与角色，支付宝 SDK 负责渠道交互，Spring Mail 负责通知。业务 SQL 保留在 MyBatis Mapper，Flyway 只负责显式数据库迁移；H2 和 Testcontainers 是测试工具。

项目还包含首页推荐、用户资料与地址、验证码、微信登录和 AI 客服等模块。它们可以从对应 Controller 和 Service 单独阅读；下面的可靠性说明聚焦售票交易主线，不把这些外围能力算作已通过真实渠道验收。

## 一笔订单如何走完

| 阶段 | 对外入口 | 核心代码与持久状态 |
|---|---|---|
| 上架 | 管理员创建演出、场次和票档 | `EventController`、`EventSessionController`、`TicketTierController`；票档有可售时间、状态与库存 |
| 抢票受理 | `POST /api/user/seckill` | `SeckillServiceImpl` 调用 `TradeOperationsImpl.reserve`；锁定用户／票档，检查限购及销售条件，同一 MySQL 事务扣可用库存、建 `stock_reservation`、写 `message_outbox` |
| 异步落单 | `GET /api/user/seckill/requests/{requestId}` 查询结果 | `OutboxPublisher` 发布事件，`TradeEventConsumer` 调用 `createOrder`；订单创建后预占从 `RESERVED` 转为 `ORDER_CREATED`，客户端取得 `orderId` |
| 创建支付 | `POST /api/user/payments/create`，建议显式传 `orderId` | `PaymentCoordinator.create` 锁订单、复用活跃支付流水，再由 `AlipayPaymentGateway` 生成沙箱付款表单 |
| 支付确认 | `POST /api/public/payments/notify`，用户另可查 `/api/user/payments/status/{paymentNo}` | `PaymentServiceImpl` 验签并核对应用、交易号和金额；`PaymentCoordinator.settle` 更新流水、订单与预占，`PaymentNotificationAudit` 记录有效异步回调。同步回跳不直接入账 |
| 通知与恢复 | 后台调度，无用户入口 | Outbox 投递付款事件；`NotificationTasks` 持久化并发送邮件；`TradeRecovery`、退款任务处理器恢复超时和失败任务 |

抢票接口的成功表示**申请受理**，不是订单已经创建。调用方应保存 `requestKey`，同一业务重试复用该键；拿到 `orderId` 后才能创建支付。具体状态和接口约定见[交易核心接口](transaction-core-api.md)。

## 三条关键异常分支

- **重复请求／消息／回调**：`requestKey`、唯一约束和行锁使同一购买意图收敛到同一预占；Outbox 可以重复投递，消费者按持久状态幂等落单；重复付款通知会逐次留下审计日志，但不重复扣库存或重复确认订单。
- **取消与超时**：订单先进入取消中；渠道查单或关单仍不确定时保留库存，确认关闭后才释放。没有支付流水的订单也能由截止时间恢复任务处理。库存释放对应的预占转为 `RELEASED`，同一笔不能二次回补。
- **迟到付款**：若库存已释放而渠道随后确认收款，支付流水保留，订单不重新变已支付；系统按持久退款单号发起全额退款。渠道结果不确定时先查同一退款号，再决定是否重试或转人工。

在隔离、初始库存等于总量且没有旧数据的票档，可用 `total_stock = available_stock + RESERVED／ORDER_CREATED／PAID 预占数量之和` 核对库存。该断言及其他交易检查见[压测断言 SQL](performance/assertions.sql)；不要在原数据库上套用隔离数据集的初始化假设。

## 建议的讲解顺序

先用管理员上架和用户抢票解释业务，再从 `TradeOperationsImpl.reserve` 说明“数据库事务里同时写库存、预占、Outbox”，接着讲异步落单和查询申请。支付部分强调异步回调验签、统一入账与同步回跳的边界；最后用取消时渠道未知、迟到付款退款和 Outbox 重试解释一致性。讲性能时只引用[已记录的隔离实测](performance/results-2026-10-21-outbox.md)，同时说明环境与目标值的区别。
