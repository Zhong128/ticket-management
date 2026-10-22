# Redis预扣售票协议：当前实现与运行说明

本说明对应分支 `codex/redis-seckill-coordination` 的阶段2～4实现，替换原来的入口MySQL扣库存协议。原始基线 `8e52a22`，重构前可运行基线 `726217a`；实施记录与验证边界见[进度](high-concurrency-refactor-progress.md)。尚未完成最终trade旧类清理、新版JMeter、真实支付宝和SMTP复验，不将历史成绩套用于本版。

## 调用关系

1. `SeckillController → SeckillServiceImpl`：校验用户与数量，按票档和用户取模取得Redisson短锁，等待默认200ms、20段、watchdog续期。
2. `StockServiceImpl → LuaScriptLoader → reserveStock.lua`：原子检查缓存代次、幂等键、用户占位和可售配额，建立token与待确认记录。库存或缓存问题拒绝请求；必要时查持久日志返回历史同键结果，不承诺所有拒绝均零SQL。
3. `ReservationServiceImpl.accept`：共享代次屏障、受理日志唯一键、用户/票档限购行锁、售卖校验、PENDING申请和Outbox同一短事务提交。这里不扣ticket_tier库存，不持票档行锁。
4. `OutboxPublisher → RabbitMQUtil → trade.events.v3 → SeckillOrderConsumer`：异步事务内按版本和库存条件扣MySQL，写订单、HELD阶段和通知Outbox。重复消息不重复扣减。
5. 支付仍由 `PaymentServiceImpl → PaymentCoordinator` 协调；取得代次屏障后再锁订单/流水，保持统一入账、回调审计和退款。外部渠道网络请求不放在这些数据库短事务中。
6. `PaymentTimeoutConsumer` 和 `PaymentCompensationSchedule → OrderRecoveryService` 共用关单规则；`StockReconciliationSchedule` 每5秒处理最多100个孤立预扣，按分钟对账。

`ReservationService` 位于原service包，唯一实现为ReservationServiceImpl。旧交易、统一消费、恢复及消息适配入口已经删除；职责和保留类见[清理清单](trade-cleanup.md)。

## Lua与Redis数据

所有脚本只放 `src/main/resources/lua/`，由LuaScriptLoader加载；Java类不内嵌脚本文本。原decrStock.lua已删除。预扣、确认、释放、分批重建、开放代次与展示投影分别使用具名文件。修改脚本同步加载器、调用方和真实Redis测试。

票档 `T` 的元数据键：`seckill:{T}:meta`；代次 `E` 的配额、用户占位、请求、token、待确认键分别为同一hash tag下的 `quota:E`、`users:E`、`requests:E`、`tokens:E`、`pending:E`。活跃占位不依赖TTL自动过期。展示库存仍使用原 `seckill:stock:T` 及版本键，不能写入抢票配额。

同一requestKey必须复用原token、创建时间和30分钟截止时间。不同票档或数量复用键会拒绝，并释放本次额外预扣。同键历史结果来自受理日志，取消后新购买必须使用新键。

库存阶段与对外业务状态不同：

| 库存阶段 | 含义 | 释放 |
| --- | --- | --- |
| PENDING | 只扣Redis，申请和Outbox已可靠提交 | 不增加MySQL库存，可靠通知Redis释放 |
| HELD | 消费者已扣MySQL，订单或支付继续占用 | 事务内恢复MySQL，再同步Redis |
| RELEASED | 申请已释放 | 重复消息不再归还 |

稳定后按张数验证 `Redis配额 = MySQL可售库存 - PENDING数量`；`总库存 = MySQL可售库存 + HELD有效申请数量`。孤立预扣与未同步释放会造成暂时差额，必须区分并等待恢复，不能直接用数据库库存覆盖Redis。

## 故障处理

- Lua预扣后未写库：超过60秒由扫描封存同键拒绝终态，再幂等回补；迟到受理事务不能重新接受。
- 提交响应未知：查询/封存同一受理日志。已经接受则返回原申请，无法判定则返回UNKNOWN并保留预扣，不盲目释放。
- Outbox写失败：申请、占位和事件一起回滚，另一个事务确定拒绝终态后才释放Redis。
- 乐观冲突：最多3次即时新事务尝试；持续冲突进入持久建单重试。建单失败累计3次后释放，缓存暂停不计作业务失败。
- 消费失败：消息恢复任务与退避Outbox同事务，最多10次后人工处理；该恢复任务不必绑定订单。数据库无法保存恢复任务时不ACK。建单非法死信原始载荷以Base64保存在补偿任务中供核查。
- 消费确认/释放乱序：先查当前申请终态，再调用幂等脚本，确认不能复活已释放token。
- 缓存重建：数据库排他屏障暂停并等待在途变更，恢复新代次（含PENDING和历史有效订单限购），完整后才开放；中断后保持关闭，60秒超时后可接管。旧代次的初始化和释放不得覆盖新配额。
- 当前回补和重建采用至少一次执行及业务幂等，不声明跨Redis/MySQL/MQ的原子分布式事务或消息恰好一次。

## 队列、超时与配置

建单、库存、订单通知、支付通知分别使用 `trade.order.v3`、`trade.stock.v3`、`trade.notify.order.v3`、`trade.notify.payment.v3`；持久失败队列保留异常。等待队列 `trade.deadline.wait.v3` 无消费者，按原截止时间设置TTL，死信转发到 `trade.deadline.ready.v3`。TTL可能受队头影响，数据库每分钟扫描兜底；不承诺毫秒级准时关闭。

配置来自application.yml及.env.example：分段数20、等待200ms，消费并发3～10、预取10，孤立扫描5秒、订单恢复60秒。入口的6并发限制已移除。参数不是性能成绩，应在新版持续测试后再调优。

## 接口变化

- 抢票成功继续返回requestId/requestStatus/orderNo/expireTime，增加实际requestKey；失败也返回键与REJECTED或UNKNOWN。HTTP200中的业务失败仍是code=0。
- 新增 `GET /api/user/seckill/requests/by-key?requestKey=...`，只查询登录用户的日志；未知返回UNKNOWN，已拒绝返回REJECTED，已接受返回原申请。键不能为空且最多64字符。
- 原申请ID、订单、支付、退款接口继续使用。资格与库存预查询使用Redis配额，查询结果仍不能代替最终原子预扣。
- 受理后尚未建单的申请会重新检查售卖状态；演出、场次、票档下架或归属改变时终止并释放。已经建立的订单继续按支付/取消状态机处理。

## 迁移与切换

1. 先停止旧交易实例/消费者并保留队列及数据库备份。V2和V3库存协议不能并行写同一库。
2. 在数据库副本或全新隔离库执行现有 `scripts/trade-migrate.ps1 migrate`、`validate`，确认V5成功。只有已有旧表且没有Flyway历史时才按原说明使用baseline-existing；不能重复初始化已有库。
3. V5新增受理日志、缓存代次、申请库存阶段/token/代次，历史已占库存申请回填HELD、已释放回填RELEASED。MESSAGE_RETRY任务允许order_no/user_id为空，原业务记录不被删除或改写。
4. 启动校验要求V5及新增列；默认TRADE_ENABLED=false、自动迁移关闭。本轮未对原库执行V5，不能直接让原库上的旧实例与本分支混跑。
5. 不启用legacy-consumers-enabled重投旧消息。旧队列需按申请/订单/支付核对后隔离处理，不能直接发送到V3。
6. 回退先停受理并处理本协议未完成申请，保留新表与财务记录；不能直接让旧版接管PENDING。

当前专项与全量验证、尚未验收的双应用/真实渠道/压测见[进度](high-concurrency-refactor-progress.md)。
