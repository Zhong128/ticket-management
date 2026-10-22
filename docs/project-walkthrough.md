# 项目导读：从代码定位到售票闭环

本文按当前代码组织，而不是按开发时间讲解。项目入口位于 `ticket-management` 模块；仓库根目录的 `docs` 保存设计、迁移和验收证据。代码是纯后端，支付宝收银台由支付宝提供，项目没有自建支付前端。

## 技术与目录

| 位置 | 职责 | 先看什么 |
|---|---|---|
| `src/main/java/.../controller`、`dto`、`vo` | HTTP 入参、鉴权和返回值 | `SeckillController`、`PaymentController`、`OrderController` |
| `serviceimpl` | 演出维护、认证、订单、支付入口等业务编排 | `EventServiceImpl`、`TicketTierServiceImpl`、`SeckillServiceImpl`、`OrderServiceImpl`、`PaymentServiceImpl` |
| `serviceimpl` 交易主线 | Redis受理、可靠日志、异步建单与恢复 | `SeckillServiceImpl`、`StockServiceImpl`、`ReservationServiceImpl`、`OrderRecoveryService` |
| `trade/core` | 库存调整与暂用调用接口；旧实现不再注册运行Bean | `StockAdjustmentService`；旧实现仅待阶段5迁移测试后删除 |
| `trade/payment` | 支付流水、渠道查单／关单、退款与回调审计 | `PaymentCoordinator`、`RefundService`、`PaymentNotificationAudit`、`AlipayPaymentGateway` |
| `consumer`、`schedule` | 按业务消费与定时恢复，拓扑在原config | `SeckillOrderConsumer`、`PaymentTimeoutConsumer`、两个原schedule |
| `trade/messaging` | Outbox、截止时间、展示投影与通知任务辅助 | `OutboxPublisher`、`DeadlineHandler`、`StockProjection`、`NotificationTasks`；旧聚合消费/调度不再运行 |
| `mapper` | MyBatis SQL、条件更新和行锁 | `TradeCoreMapper`、`TradePaymentMapper`、`TradeOutboxMapper`、`TradeRefundMapper` |
| `src/main/resources/db/migration` | V1～V5 结构与回填 | V5新增Redis可靠受理、库存阶段和缓存代次 |
| `src/main/resources/lua` | 原子预扣、确认、释放、重建与展示投影 | `LuaScriptLoader`统一加载六个具名脚本 |
| `src/test/java/.../trade` | 交易单测及隔离中间件、HTTP 集成测试 | `TradeApplicationIT`、`TradeInfrastructureIT`、`PaymentTransactionTest` |

Redis Lua负责前置配额、限购和预扣，Redisson用户分段锁协调短受理；MySQL负责可靠申请、持久库存、订单、支付及最终约束。RabbitMQ承载异步建单、库存同步及截止事件。Sa-Token负责登录与角色；支付宝SDK和Spring Mail负责渠道交互。SQL统一MyBatis，Flyway显式迁移；H2和Testcontainers只用于测试。

项目还包含首页推荐、用户资料与地址、验证码、微信登录和 AI 客服等模块。它们可以从对应 Controller 和 Service 单独阅读；下面的可靠性说明聚焦售票交易主线，不把这些外围能力算作已通过真实渠道验收。

## 一笔订单如何走完

| 阶段 | 对外入口 | 核心代码与持久状态 |
|---|---|---|
| 上架 | 管理员创建演出、场次和票档 | `EventController`、`EventSessionController`、`TicketTierController`；票档有可售时间、状态与库存 |
| 抢票受理 | `POST /api/user/seckill` | `SeckillServiceImpl`取得分段锁并调用Lua预扣，再由`ReservationServiceImpl.accept`事务保存PENDING申请、受理日志、限购和Outbox；不在入口扣MySQL库存 |
| 异步落单 | 申请ID查询，或`/requests/by-key`找回结果 | `OutboxPublisher`发布V3事件，`SeckillOrderConsumer`调用事务建单；版本和库存条件扣减、写订单、转HELD及通知事件同事务提交 |
| 创建支付 | `POST /api/user/payments/create`，建议显式传 `orderId` | `PaymentCoordinator.create` 锁订单、复用活跃支付流水，再由 `AlipayPaymentGateway` 生成沙箱付款表单 |
| 支付确认 | `POST /api/public/payments/notify`，用户另可查 `/api/user/payments/status/{paymentNo}` | `PaymentServiceImpl` 验签并核对应用、交易号和金额；`PaymentCoordinator.settle` 更新流水、订单与预占，`PaymentNotificationAudit` 记录有效异步回调。同步回跳不直接入账 |
| 通知与恢复 | 后台调度，无用户入口 | 具名消费者保存通知任务；`NotificationTasks`发送邮件，原schedule和`OrderRecoveryService`恢复订单、孤立预扣、支付及退款 |

抢票接口的成功表示**申请受理**，不是订单已经创建。调用方应保存 `requestKey`，同一业务重试复用该键；拿到 `orderId` 后才能创建支付。具体状态和接口约定见[交易核心接口](transaction-core-api.md)。

## 三条关键异常分支

- **重复请求／消息／回调**：`requestKey`、唯一约束和行锁使同一购买意图收敛到同一预占；Outbox 可以重复投递，消费者按持久状态幂等落单；重复付款通知会逐次留下审计日志，但不重复扣库存或重复确认订单。
- **取消与超时**：订单先进入取消中；渠道查单或关单仍不确定时保留库存，确认关闭后才释放。没有支付流水的订单也能由截止时间恢复任务处理。库存释放对应的预占转为 `RELEASED`，同一笔不能二次回补。
- **迟到付款**：若库存已释放而渠道随后确认收款，支付流水保留，订单不重新变已支付；系统按持久退款单号发起全额退款。渠道结果不确定时先查同一退款号，再决定是否重试或转人工。

新协议按张数核对 `Redis配额 = available_stock - PENDING申请数量`，`total_stock = available_stock + HELD有效申请数量`，并单列孤立预扣与尚未同步事件。不能直接用数据库库存覆盖Redis配额。历史压测SQL需要在阶段6按新协议核对后使用，不能在原库套用隔离数据假设。

## 建议的讲解顺序

先讲分段锁、Lua原子限购、可靠受理日志和Outbox如何交接，再讲异步乐观扣库存与申请查询；接着解释PENDING/HELD不同释放、代次重建和旧事件幂等。支付强调验签、统一入账、渠道未知及迟到退款。细节见[新协议](redis-seckill-protocol.md)。历史44.51次/秒仅对应重构前版本，本分支尚无新吞吐成绩；双应用及真实渠道复验等待后续阶段。
