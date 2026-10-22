# 交易可靠性整改使用说明

2026-10-22本分支已接入V5 Redis预扣协议：入口受理、异步扣库和缓存代次规则以[当前协议](redis-seckill-protocol.md)为准；下面保留旧问题的修复背景，旧测试记录不能当作新版外部渠道或性能成绩。

基线提交 `8e52a22`，开发分支 `codex/transaction-hardening`。保留 Java 21、Spring Boot、MyBatis、MySQL、Redis、RabbitMQ。所有业务数据访问统一使用 MyBatis Mapper：SQL放具名Mapper方法，事务、锁序和状态规则由Service编排，使用同一DataSource与Spring事务管理器。没有引入其他业务持久层或分布式服务。

## 解决的问题与代码入口

| 原问题 | 修改位置 | 现在的处理 / 验证 |
|---|---|---|
| Redis、MySQL和MQ之间存在丢单及重复回补窗口 | `SeckillServiceImpl`、`StockServiceImpl`、`ReservationServiceImpl`、`OutboxStore` | Redis预扣后短事务可靠受理，异步扣库建单；按token、库存阶段和代次恢复 |
| 同用户并发请求及重试可能重复购票 | `purchase_guard`、`stock_reservation`、`SeckillController` | 用户/票档行锁 + 请求键唯一约束；重复键返回原申请，参数不一致拒绝 |
| 无支付流水订单漏关；延时队列提前消费 | `DeadlineHandler`、`OrderRecoveryService`、原consumer/schedule/config | 首次预扣起固定30分钟；无消费者等待队列死信转发，数据库扫描不依赖支付流水或MQ |
| 支付与取消竞争；渠道失败仍释放库存 | `PaymentCoordinator`、`ReservationServiceImpl`、`OrderServiceImpl` | 代次屏障、订单锁及短事务确认，UNKNOWN保持占用；付款统一校验入账 |
| 回调验签删掉sign；同步跳转被当作付款证据 | `AlipaySignatureUtil`、`PaymentServiceImpl`、`PaymentController` | 原参数验签，校验应用/流水/金额；跳转仅提示待确认；RSA签名及回调测试 |
| 真实回调已处理但通知时间和旧回调日志为空 | `PaymentNotificationAudit`、`TradePaymentMapper`、`PaymentNotifyLogMapper` | 验签并处理成功后写首次 `notify_time` 和每次送达的金额摘要；隔离 HTTP 测试与真实支付宝沙箱通知均已验证，详见[沙箱验收](alipay-sandbox-acceptance.md) |
| 取消已释放后迟到收款、第二笔收款无退款闭环 | `trade/payment/RefundService`、`RefundController` | 全额补偿退款，先保存唯一退款号再调渠道；不确定结果先查再重试；10次后人工处理 |
| Confirm/Return只打印日志，重启丢重试 | `OutboxPublisher` | 持久化状态、租约、confirm+mandatory return，失败持续退避重发；重复投递由业务幂等收敛 |
| 对账覆盖并发库存，后台编辑回写旧库存 | `StockServiceImpl`、`ReconciliationServiceImpl`、`StockAdjustmentService` | 展示投影与配额分离，恢复时扣除PENDING；普通编辑不写库存，下架无未完成申请后才能调整 |
| 邮件失败丢失、清理误删成功支付 | `NotificationTasks`、`PaymentRecordMapper` | 持久邮件任务三次重试后人工处理；保留支付流水。邮件是至少一次发送，进程在发送后崩溃可能重复邮件 |

## 接口变化

- `POST /api/user/seckill`：原 `tierId`、`quantity`，新增可选 `requestKey`（最多64字符）。同一业务重试必须复用原键。不传时服务端生成键，仅兼容旧客户端。成功代表申请已受理，`requestId` / `requestStatus` / `expireTime` 为准，订单可能尚未创建。
- `GET /api/user/seckill/requests/{requestId}`：仅申请人可查。`RESERVED`处理中、`ORDER_CREATED`待支付、`PAID`已付款、`RELEASED`已释放。取得 `orderId` 后才能发起支付。
- 原创建支付接口建议显式传 `orderId`，未传仅在恰好一个可支付订单时兼容。付款页面重试复用流水与绝对截止时间。
- 订单新增状态 `4` 取消中。渠道不确定时请求可能尚未完成，客户端应查询订单状态。已释放订单收到付款会退款；截止后到达的回调若库存仍被订单持有，可以正常确认付款。
- `GET /api/user/refunds` 查询本人的补偿退款；管理员 `GET /api/admin/refunds`、`POST /api/admin/refunds/{refundNo}/retry` 处理人工状态。重试仍先查询同一退款号，不能手工造第二个退款号。
- `POST /api/admin/ticket-tiers/{id}/stock-adjustments`，请求 `{"delta": 10}`：同时增加总量与可用量，减少则用负数；须先下架且无未结预占/待关单。普通票档编辑不能再传库存字段。

## 迁移与启用

默认 `TRADE_ENABLED=false`、`TRADE_MIGRATIONS_ENABLED=false`，交易写入入口关闭，旧消费者也关闭。启动不会自动改原库。不能把“启动成功”当作已完成迁移。

1. 停止旧实例和消费者，备份数据库及旧队列。先在备份副本执行 `transaction-preflight.sql`，核对库存、重复有效订单、重复支付和金额异常。重复历史记录保留，不自动删除或猜测资金归属；重复有效订单对应的用户/票档会阻止新购买。
2. 将 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 指向副本，先构建项目。Windows 从模块目录运行 `./scripts/trade-migrate.ps1 baseline-existing`（仅已有旧表且没有Flyway历史的库执行一次），随后 `migrate`、`validate`。空白新库只执行 `migrate`。已有Flyway V1历史也不重复baseline。
3. V1仅供空库建原表；V2增量加交易结构；V3回填历史状态；V4增加Outbox索引；V5增加Redis受理日志、库存阶段和代次屏障，保留资金和历史数据。MySQL DDL不能整体回滚，失败须检查Flyway记录与实际DDL，禁止盲目重跑初始化SQL。本轮只在新测试容器执行V5，没有迁移原库。
4. 副本验证后再安排目标库维护。启用 `TRADE_ENABLED=true`，保持自动迁移关闭。应用要求V5历史及必要列；数据库未迁移不能直接启动交易。
5. 当前使用`trade.events.v3`与具名工作/等待/失败队列，详见当前协议。旧V2队列保留并核对处理；不能通过启用legacy-consumers-enabled桥接旧消息，不并行运行两种库存协议。

非法JSON/事件结构会经 `trade.failures.v2` 转入 `trade.work.failed.v2` 或 `trade.deadline.failed.v2`，持久保留待检查，不无限重入。已有预览版v2队列若缺少DLX参数，须安排受控迁移或策略配置；不能清空队列来规避参数冲突。

回退须先停新消费者及交易写入。保留新增表与流水；新预占/取消中/退款未处理完时不能直接恢复旧版写入。基线用于代码对照，不代表可以忽略新交易状态直接回滚运行。

## 构建与验证

在 `ticket-management` 目录、JDK21下：

```powershell
./mvnw.cmd test
./mvnw.cmd '-Dtest=*Test,*IT' test
./mvnw.cmd -DskipTests package
```

若模块没有Maven wrapper，使用本机 `mvn`。`*IT`需启动Docker；测试自行创建无主机数据卷的MySQL、Redis、RabbitMQ容器，不读原数据库凭据。缺少Docker会失败而不会伪装通过。普通单元/事务测试使用mock或H2；真实渠道使用可控网关替身，不触达资金。原本依赖个人本地配置的 `TicketManagementApplicationTests` 仅在 `RUN_LOCAL_CONTEXT_TEST=true` 显式运行；隔离应用启动验收由 `TradeApplicationIT` 负责。

报告见模块 `target/surefire-reports`。性能压测是独立验收：参考 `performance/README.md`；功能单测通过不能作为吞吐量证据。

管理员建演出至付款、取消、超时释放和迟到付款退款的隔离 HTTP 验收，见 [完整售票流程隔离验收](full-sale-isolated-acceptance.md)。

本机Windows的JDK21遇到8.3短路径临时目录导致 `Selector.open()` 报 `Unable to establish loopback connection`。验收时通过测试JVM参数 `-DargLine=-Djdk.net.unixdomain.tmpdir=C:\Users\LIANXIANG\IdeaProjects\ticketManagement\ticket-management\target` 指定存在的工作目录解决；这是本机环境设置，不应改成跳过Redis验收。

## 运维检查

检查Outbox积压/过期租约、CANCELING订单、PENDING超期申请、孤立Redis预扣、PAUSED代次和退款/通知/消费人工任务。Outbox仍批量100条、邮件任务批量50条并有租约。入口6并发限制已移除，分段锁默认20段/等待200ms，消费并发3～10/预取10。Redis不可用暂停新抢票，恢复须按数据库与PENDING重建；展示投影不能覆盖配额。真实性能与连接池容量需新版实测，不能复用旧版44.51次/秒。

原Git历史中已有旧凭据，本次只沿用基线后的环境变量/本地忽略配置，没有重写历史。对外分享仓库前应更换曾提交的凭据。
