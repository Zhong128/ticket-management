# 交易可靠性整改使用说明

基线提交 `8e52a22`，开发分支 `codex/transaction-hardening`。保留 Java 21、Spring Boot、MyBatis、MySQL、Redis、RabbitMQ。所有业务数据访问统一使用 MyBatis Mapper：SQL放具名Mapper方法，事务、锁序和状态规则由Service编排，使用同一DataSource与Spring事务管理器。没有引入其他业务持久层或分布式服务。

## 解决的问题与代码入口

| 原问题 | 修改位置 | 现在的处理 / 验证 |
|---|---|---|
| Redis 扣减、数据库订单、MQ 之间存在丢单及多次回补窗口 | `trade/core/TradeOperationsImpl`、`TradeCoreMapper`、`OutboxStore`、`TradeOutboxMapper` | 同一 MySQL 事务写预占、扣库存、限购占位、Outbox；落单不再扣库存。并发、回滚、重复取消测试 |
| 同用户并发请求及重试可能重复购票 | `purchase_guard`、`stock_reservation`、`SeckillController` | 用户/票档行锁 + 请求键唯一约束；重复键返回原申请，参数不一致拒绝 |
| 无支付流水的订单漏关；延时队列提前消费 | `DeadlineHandler`、`TradeRecovery`、`TradeMessagingConfig` | 从预占时起固定30分钟；TTL无消费者等待队列死信转发；定时数据库恢复不依赖支付流水和MQ |
| 支付、取消覆盖状态；渠道失败仍释放库存 | `PaymentCoordinator`、`TradeOperationsImpl`、`OrderServiceImpl` | 短事务标记取消中→事务外查/关渠道→短事务释放。UNKNOWN保持占用；所有成功付款统一校验及落账 |
| 回调验签删掉sign；同步跳转被当作付款证据 | `AlipaySignatureUtil`、`PaymentServiceImpl`、`PaymentController` | 原参数验签，校验应用/流水/金额；跳转仅提示待确认；RSA签名及回调测试 |
| 取消已释放后迟到收款、第二笔收款无退款闭环 | `trade/payment/RefundService`、`RefundController` | 全额补偿退款，先保存唯一退款号再调渠道；不确定结果先查再重试；10次后人工处理 |
| Confirm/Return只打印日志，重启丢重试 | `OutboxPublisher` | 持久化状态、租约、confirm+mandatory return，失败持续退避重发；重复投递由业务幂等收敛 |
| 对账覆盖并发库存，后台编辑回写旧库存 | `StockProjection`、`StockAdjustmentService`、`TicketTierMapper` | Redis只投影带版本DB库存；普通编辑不写库存；下架且无未结预占后才可调整容量；删除票档改为下架保留历史 |
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
3. V1仅供空库建原表；V2增量加表/列/索引；V3回填截止时间、预占和唯一成功付款引用；V4为 Outbox 租约令牌建立索引，缩小批量发布完成时的锁定范围。迁移不调整原库存/金额、不删除流水。MySQL DDL不保证整体回滚，失败须检查Flyway记录及实际DDL，禁止盲目重跑初始化SQL。
4. 在副本验证后再对目标库安排同样维护步骤。启用 `TRADE_ENABLED=true`，保持自动迁移关闭。应用校验V4历史及必要列，缺失则拒绝启用。
5. v2使用 `trade.events.v2`、`trade.work.v2`、`trade.deadline.wait.v2`、`trade.deadline.ready.v2`。旧队列保留，不能直接重投没有数据库预占的旧创建消息；先人工对照订单/流水。需要桥接时仅显式启用 `trade.legacy-consumers-enabled`，不能并行运行旧版服务写同一库存。

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

检查 `message_outbox` 长期NEW/过期租约、`order_info.order_status=4`、过期未释放预占、退款 `MANUAL_REQUIRED`、邮件 `payment_compensation.task_status=3`。Outbox 发布器每批最多领取100条，先发送整批消息，再按各自的 Broker 确认批量标记成功；失败事件仍按原租约与退避机制重试。邮件任务每批最多50条，一次调度最多连续处理10批；发送仍逐条持有租约，失败按原规则重试或转人工。抢票事务默认同时最多6个，可用 `TRADE_RESERVATION_MAX_CONCURRENT` 调整；生产连接池上限为10，调整时要给建单、Outbox、通知等后台任务保留连接。Outbox重发可重复投递；订单、释放、退款均依赖数据库幂等。Redis不可用不应影响库存正确性，恢复后定时按版本重建投影。

原Git历史中已有旧凭据，本次只沿用基线后的环境变量/本地忽略配置，没有重写历史。对外分享仓库前应更换曾提交的凭据。
