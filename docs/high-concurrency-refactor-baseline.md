# Redis 抢票协同重构：第 1 阶段基线

## 版本与执行范围

- 原始源码整理基线：`8e52a22`。
- 本轮重构前基线：`726217ae65ecb18a1b2902c2b08cac43878caa1f`。
- 实施分支：`codex/redis-seckill-coordination`，从干净工作区创建；仍使用原项目文件夹，不创建额外工作树。
- 当前阶段只整理计划、调用链与配置，更新协作约定并验证现有代码；未切换抢票架构，未修改生产 Java、Lua、POM、数据库迁移或个人配置。
- 执行计划：[高并发售票协同重构](high-concurrency-refactor-plan.md)。

## 当前真实调用链

| 场景 | 当前路径 | 不能误称为已完成的新设计 |
| --- | --- | --- |
| 抢票 | SeckillController → SeckillServiceImpl → TradeOperations.reserve | 入口在 MySQL 事务内锁限购、票档、扣库存、写申请与 Outbox |
| 异步建单 | OutboxPublisher → trade.events.v2 → TradeEventConsumer → TradeOperations.createOrder | 建单使用已经扣减的申请，不能再次扣库存 |
| 创建支付 | PaymentController → PaymentServiceImpl → PaymentCoordinator → PaymentRepository / PaymentGateway | 支付协调现有短事务与渠道适配，属于应保留的可靠性能力 |
| 付款确认 | 回调、查单、关单发现付款 → PaymentCoordinator.settle → TradeOperations.applyPaid | 订单和支付统一核验，取消后收款走 RefundService |
| 取消 | OrderServiceImpl → beginCancel → TradePaymentPort.closeForOrder → finishCancel | 未知渠道不释放库存，短事务与外部网络调用分开 |
| 超时 | DeadlineHandler / TradeRecovery → 统一订单取消 | V2 等待队列无消费者，到期死信进入处理队列，数据库扫描补偿 |
| 库存同步 | StockProjection + ReconciliationServiceImpl | 目前 Redis 是 MySQL 版本投影，重构后必须与抢票配额分开 |
| 邮件 | NotificationTasks → EmailServiceImpl | 持久化任务、重试和人工处理，不能回退为仅进程内异步 |

## 原代码、Lua及配置清单

- SeckillLockServiceImpl / RedissonLockUtil / RedissonConfig 仍存在，但当前抢票入口不使用分段锁。原分段按用户取模，不是库存分桶。
- `resources/lua/decrStock.lua` 与原版相同：只检查库存并扣减；当前主链路未使用。LuaScriptLoader 只注册这个脚本。
- StockProjection 内嵌一段版本投影 Lua，是当前实际脚本入口。阶段 2 统一外置到 `.lua` 文件，再删除失效 decrStock.lua。
- StockServiceImpl 的旧直接扣减与回补主动抛 UnsupportedOperationException，旧限购设置/删除不再写 Redis；后续按新协议重写，不能只解除异常。
- 原四个 consumer 目前调用 LegacyMessageAdapter / NotificationTasks，并以 `trade.legacy-consumers-enabled=false` 默认停用。不能与 V2 消费者同时打开。
- PaymentCompensationSchedule 当前每分钟核查付款和退款；TradeRecovery 每分钟补建单/关单；StockReconciliationSchedule 每分钟重建版本投影。重构要合并重复职责，而非叠加调度。
- RabbitMQConfig 提供 correlated confirm、return、mandatory、JSON 和手动 ACK，监听工厂初始并发 3～10、预取 10；TradeMessagingConfig 目前声明 V2 工作/超时/失败拓扑和任务调度器。
- PaymentRabbitMQConfig 保留旧支付拓扑。新拓扑按职责迁回原配置，不改已有队列参数，不产生同名 Bean。
- RedisConfig 为普通 JSON 模板；StockProjection 使用 StringRedisTemplate；RedissonConfig 读取 redisson.config。隔离集成测试动态注入同一测试 Redis。
- OrderNoGenerator / PaymentNoGenerator 的原 Redis 日序列实现仍在，但当前业务编号使用 UUID；后续统一生成入口，保持当前格式。
- AsyncConfig 的有界线程池不等于可靠任务；不恢复原抢票流程 CompletableFuture.runAsync 的邮件发送。
- AlipayConfig / AlipayProperties / AlipaySignatureUtil 保留原入口；当前验签、绝对期限、审计和环境变量配置不回退。

## 验证方式和证据

环境：Windows、Oracle JDK 21.0.8、Maven 3.9.11、Docker Engine 28.5.1。Maven 不在 PATH，使用已有 Maven wrapper 缓存中的发行版，未向 POM 增加验证依赖。

本阶段显式关闭 `RUN_LOCAL_CONTEXT_TEST`、`RUN_JMETER_LOAD`、`RUN_SMTP_ACCEPTANCE`。实际 HTTP 和中间件验证由现有 TradeApplicationIT / TradeInfrastructureIT / LegacyMigrationIT 等完成，Testcontainers 创建独立 MySQL 8、Redis 7、RabbitMQ 3.12，不导入个人配置、原数据卷或真实支付/邮件。

在模块目录运行：

```powershell
mvn -B '-Dtest=*Test,*Tests,*IT' '-DargLine=-Djdk.net.unixdomain.tmpdir=C:/Users/LIANXIANG/IdeaProjects/ticketManagement/ticket-management/target' package
```

日志：`ticket-management/target/redis-coordination-stage1-baseline.log`；机器可读报告：模块 `target/surefire-reports`。运行数据、日志与 JAR 保持 Git 忽略。

2026-10-22 11:38（Asia/Shanghai）本次 Maven `package` 为 **BUILD SUCCESS**，耗时约 1 分 23 秒。Surefire XML 汇总 **102 项，99 项通过、0 失败、0 异常、3 项按条件跳过**。

| 验证入口 | 本次结果 |
| --- | --- |
| TradeApplicationIT | 3 项通过，含真实 HTTP 售票闭环、登录和异步建单 |
| TradeInfrastructureIT | 9 项通过，含真实 MySQL、Redis、RabbitMQ 的事务、幂等、确认返回和 TTL/DLX |
| LegacyMigrationIT | 1 项通过，隔离历史结构升级验证 |
| 其他单元及事务测试 | 86 项通过 |
| TicketManagementApplicationTests | 1 项按条件跳过，个人配置上下文不启用 |
| TradeLoadIT | 1 项按条件跳过，本阶段不重复 JMeter |
| TradeSmtpIT | 1 项按条件跳过，本阶段不发送真实邮件 |

`package` 成功验证 `ticket-management/target/ticket-management-0.0.1-SNAPSHOT.jar`，大小 126,928,783 字节；源码未变，Maven 复用现有归档，本次不宣称清理后全量重建。没有执行 `clean`，以保留用户此前的本地验收页面和报告。测试后 `docker ps --filter label=org.testcontainers=true` 无存活容器；没有启停项目原 Compose 服务或更改原数据卷。

这是现有 MySQL 预占版本的重构前证据，不能描述为 Redis 预扣、watchdog 分段锁或新 Lua 脚本已经验收。未新增覆盖率、静态扫描或吞吐成绩，未改 POM 引入这些工具。

## 决策与后续

- 已批准计划是本轮规范，执行顺序及阶段确认要求优先于技能的连续执行建议。
- 本阶段只验证基线和写说明，不创建额外工作树或迁移文件；当前代码使用独立本地分支，后续仍在用户熟悉的原目录实施。
- 原 docs/transaction-hardening-contract.md 中 MySQL 预占约定记录的是上轮架构；当前生产实现仍遵循它，本轮目标以新计划为准，阶段 2～5 随实现同步更新，不能提前把文档描述当作新架构已运行。
- 历史 13,519 次请求、44.51 次/秒仅属于重构前版本的持续负载记录；本阶段不重跑压测，也没有新吞吐成绩。
- 进入阶段 2 前汇报本次回归、剩余问题及 Lua/可靠受理的下一步；任何失败先定位，不把基线失败归因于尚未进行的重构。
