# Redis抢票协同重构进度

计划：[执行计划](high-concurrency-refactor-plan.md)。基线：`726217a`；阶段1文档提交：`f5af2ca`；分支：`codex/redis-seckill-coordination`。本轮用户授权连续执行2～3个阶段，遇到需要决策的问题再确认。

## 阶段2：Lua、分段锁与可靠受理

- 新增6个外置Lua：预扣、确认、释放、分批重建、开放代次、展示投影。统一由LuaScriptLoader加载；删除未再使用的decrStock.lua，展示投影不再内嵌Lua。
- SeckillServiceImpl编排用户分段锁与Redis预扣，ReservationServiceImpl持久化受理日志、申请、限购占位及Outbox。入口不扣MySQL库存。
- 新增V5迁移：申请库存阶段、token、代次，受理日志、缓存代次状态；扩展补偿表允许不绑定订单的消息恢复任务。
- 真实Redis失败基线：旧加载器不存在reserveStock.lua，5项中4项因缺少协议报错。
- HTTP失败基线：暂停消费者后，旧入口仍将数据库库存从10扣到8，断言失败。
- 实现后阶段2专项：8项通过，包含幂等、旧token保护、封存孤立请求、PENDING重建和旧代次释放。原日志：模块target/stage2-lua-red.log、stage2-admission-red.log、stage2-protocol.log。

## 阶段3：异步建单与消费者

- 原SeckillOrderConsumer、SeckillDeadLetterConsumer、PaymentTimeoutConsumer、PaymentSuccessConsumer重新承担具名消费入口；新增订单通知、Redis状态消费者。
- V3拓扑由原RabbitMQConfig、PaymentRabbitMQConfig声明；等待队列无消费者，TTL到期死信转发。删除职责已迁走的TradeMessagingConfig。
- 数据库扣库存使用版本与充足库存条件；与建单、HELD阶段和通知事件同事务。乐观冲突3次新事务重试，再进入持久化恢复。
- PENDING释放不增加MySQL库存；HELD释放先事务回补再可靠同步Redis。确认与释放乱序时以申请终态为准。
- MessageRecoveryService与补偿表保存消费退避，最多10次后转人工；数据库不可用时保留Broker消息，非法建单消息持久隔离。
- RabbitMQUtil统一承接Outbox实际发送，不将send方法返回当作Broker确认；编号工具统一现有UUID格式。
- 首轮完整流程发现测试固定ID冲突，以及旧启动断言仍要求TradeEventConsumer Bean；已改为不冲突数据与具名消费者断言，不修改业务条件掩盖失败。

## 阶段4：恢复与交易接入

- StockServiceImpl通过数据库共享/排他代次屏障暂停票档、恢复新代次、重建有效用户占位，配额为数据库库存减PENDING张数，含无申请的历史有效订单限购。
- 原对账Service不再覆盖抢票配额；区分孤立预扣和瞬时差额，持续差额经暂停后恢复。展示投影保留独立键。
- 原StockReconciliationSchedule每5秒扫描全局最多100条孤立预扣；PaymentCompensationSchedule调度订单、支付和退款恢复。旧TradeRecovery已取消Bean注册，避免双调度。
- 付款、取消与库存释放取得代次屏障，延续当前统一入账、渠道UNKNOWN保留库存、迟到付款退款与审计规则。
- 启动SchemaGuard要求V5且只读检查，不对原库执行迁移。配置增加分段数、等待、消费并发/预取和恢复间隔；移除实际入口的6并发限制配置。
- 19项专项通过：LuaStockProtocolIT 6项（含两个真实Redisson客户端的watchdog测试）、TradeApplicationIT 8项、TradeMessageSupportTest 3项、TradeTopologyTest 2项。完整HTTP含付款、重复回调、取消、未知渠道、无支付流水超时、迟到付款退款。
- 19项记录对应target/stage4-protocol.log；随后新增故障注入，并完成以下最终全量回归。

- 故障注入覆盖：真实提交后响应丢失、Outbox写后异常回滚、消费重试上限与回滚、重建中断后恢复及旧任务隔离。跨票档复用同一请求键曾遗留额外预扣，已先复现再修复；专项20项全部通过，见target/stage4-fault-green.log。

## 全量回归与未完成项

最终全量命令沿用基线的 `-Dtest=*Test,*Tests,*IT package`，显式关闭个人环境上下文、JMeter和真实SMTP。日志：模块target/stage2-4-final-regression.log。2026-10-22执行122项，119项通过、0失败、0错误、3项按条件跳过，BUILD SUCCESS，生成可执行JAR。测试使用临时MySQL、Redis、RabbitMQ容器，结束后本轮临时容器已退出；没有迁移原库或使用原数据卷。

全量集合仍包含直接构造旧实现的历史回归，不能把122项全部解释为新协议测试；新链路由TradeApplicationIT的12项真实HTTP/故障测试、LuaStockProtocolIT的6项真实Redis测试及ConsumerRetryIT的2项隔离MySQL测试等验证。测试日志和JAR均为忽略的本地产物，不随源码提交。

阶段5仍需迁移并删除仅用于旧测试的TradeOperationsImpl、TradeEventConsumer、TradeRecovery及不再使用的LegacyMessageAdapter，清理过渡TradeOperations接口和关联旧测试；运行时它们已不参与主交易/调度（LegacyMessageAdapter无新消费者调用）。当前ReservationServiceImpl暂通过TradeOperations连接支付与旧测试，不能表述为trade包清理完成。

仍需后续故障细化验收：真正两套Spring应用共享中间件、更多屏障并发竞争、MQ故障恢复及压测中的任务收敛，以及真实支付宝沙箱/SMTP的本轮回归。真实Redisson双客户端不等于完整双应用验收；已有专项也不能替代计划中全部故障矩阵。

阶段6尚未执行，没有本轮新吞吐成绩。历史44.51次/秒仍只属于重构前MySQL预占版本；正常持续负载需分别核对受理、成功建单、库存和任务收敛，不能用接口成功率替代。

## 实施决策

- 本轮在原目录和独立本地分支实施，用户授权的新计划优先于旧transaction-hardening-contract中MySQL预占架构描述。
- 不修改V1～V4、不迁移原库、不创建额外工作树、不推送。V5尚只在本轮新建的测试容器执行。
- 运行时先停用旧Bean，再迁移旧测试和删除源码，避免一次性删除可靠性回归。过渡类不是最终交付结构，阶段5必须完成清理。
- 两阶段受理和建单在同一批修改集实现，不能把只受理不建单的中间代码交付为完成状态。

## 阶段5～6本轮进展（2026-10-22）

- 已删除旧TradeOperations/Impl、TradeEventConsumer、TradeRecovery、LegacyMessageAdapter及旧消息/拓扑适配入口，清理无调用的关联Mapper方法；真实业务接口迁回service包。详见[清理清单](trade-cleanup.md)。
- 原交易与支付27项用例迁到真实隔离MySQL、Redis及新状态机后全部通过；9项基础设施回归及双独立JVM竞争专项通过。
- 首轮全量103项，100通过、0失败、0错误、3按条件跳过，JAR构建成功；此后还有最后的清理和测试驱动修改，最终成绩以收尾回归为准。
- 本轮SMTP经真实售票、支付通知及重复回调验证发送；SMTP服务接受两封邮件，用户已确认两封实际收到。该测试的支付渠道为替身，不能当作支付宝验收。
- JMeter 15秒试跑283次受理、283订单，零业务失败，库存与任务收敛；29秒停压收敛说明不能用约19.67次/秒受理速度代替完整落单速度。
- 阶梯20次/秒档573次受理全部落单；40次/秒档1169次受理、1096订单，未通过全部落单检查；库存等式、占位和任务终态收敛。保留失败证据并补充终态原因核对，不以100次/秒为门槛，也不把这档记录为通过。
- 三轮持续测试、故障场景、同环境基线对照和新协议真实支付宝回归尚在执行，当前没有最终性能结论。

### 压测中发现的问题与修复复现

- 第一次约40次/秒阶梯受理1169、建单1096；再次复测受理1147、建单1146。约20次/秒也曾出现590受理、589订单，约10次/秒五分钟2997受理、2992订单。失败申请均受控释放，库存一致，但不能标为完整落单通过。
- 确认版本竞争耗尽立即重试后，被消费者和扫描任务归入普通建单失败，错误消耗三次业务失败次数。修复为明确异常类型：立即尝试仍为三次，随后交给既有持久消息重试/下轮扫描，业务异常仍使用原三次上限，绝对截止时间不变。
- 两项修复前测试真实失败：消费者错误调用creationFailed；重建后旧配额/token键仍存在。日志stage56-review-red.log，2项失败、0错误。
- 独立审查确认旧代次键永久累积。补充retireStockEpochs.lua：新代次READY及游标匹配才按显式KEYS有界UNLINK旧键，独立游标跨重建保留，每次最多100个退役代次；进程中断后分钟对账继续，活跃键不设TTL。
- 上述改动后必须重新测量；修复前的阶梯、短跑和被中止的持续轮次只作为历史失败/诊断证据，不能作为新版本持续通过成绩。

## 2026-10-23恢复验收

- 从`07ff689`恢复，无业务代码变更。新增故障测试及UNLINK真实Redis验证通过；全量111项中108通过、3按开关跳过，0失败、0错误，JAR构建成功。
- 40次/秒五分钟复验失败：11965请求中319次业务拒绝，11646受理，180秒时8700订单且仍有积压。保留失败证据，不能宣称该档已通过或库存最终收敛。
- 10次/秒连续三轮五分钟通过：8993次请求全部受理并建单，零业务失败，11/17/28秒收敛，最终库存、占位及任务断言通过。
- 售罄、重复请求、混合支付取消、双独立JVM、暂停消费者后恢复、40分段及关闭分段锁的30秒专项全部通过；不将短测当作高负载持续证明。
- 旧基线同环境三轮对照完成，两版各8993次请求全部落单；旧版停压收敛0秒、建单等待P95约0.7秒，V5为11/17/28秒及约7～15秒，当前未证明吞吐提升。
- 新域名已确认进入18080隔离实例，V5真实支付宝沙箱正在等待人工首笔付款；SMTP沿用上轮用户确认收件记录。
- 具体指标、限制与本地证据路径见[本轮实测记录](performance/results-2026-10-23-v5.md)，继续步骤见[暂停及恢复检查点](refactor-pause-checkpoint.md)。
