# 交易入口与代码清理

当前请求链：SeckillController → SeckillServiceImpl → StockServiceImpl / ReservationServiceImpl → Outbox → SeckillOrderConsumer → OrderServiceImpl → ReservationServiceImpl。

## 已删除的旧入口

| 删除部分 | 当前职责位置 |
| --- | --- |
| TradeOperations / TradeOperationsImpl | service.ReservationService / serviceimpl.ReservationServiceImpl；不再保留旧MySQL入口 |
| TradeEventConsumer | 原consumer包中的建单、到期、付款通知、死信消费者及独立通知、Redis状态消费者 |
| TradeRecovery | OrderRecoveryService与原schedule包 |
| LegacyMessageAdapter | 新协议不再读取旧消息；旧消息只允许隔离核对，不能直接恢复库存 |
| TradeMessagingConfig | RabbitMQConfig / PaymentRabbitMQConfig中的V3拓扑 |
| decrStock.lua | 6个按职责拆分的外置脚本，由LuaScriptLoader加载 |
| StockService裸扣减、裸恢复、单独设置/删除限购方法 | token、代次与申请状态约束的预扣、确认、释放 |
| 旧队列Bean和无调用的旧Mapper方法 | 不再声明旧拓扑，不再保留第二个锁票档扣库存入口 |

删除源码不会删除Broker上现存队列或消息；切换前仍须停止旧实例并隔离处置旧消息。原库没有在本轮执行迁移。

## 保留的trade职责

- StockAdjustmentService：后台受控库存调整，必须取得代次屏障。
- StockProjection：只维护展示库存，与抢票配额分离。
- DeadlineHandler：计算剩余期限并重新调度提前消息，实际由原到期消费者调用。
- OutboxStore / OutboxPublisher：事务事件持久化、租约领取、Confirm与Return处理。
- NotificationTasks：持久通知、退避与人工处理状态。
- PaymentCoordinator / PaymentRepository / PaymentGateway / AlipayPaymentGateway：统一渠道协调与入账。
- RefundService / PaymentNotificationAudit / TradePaymentPort：退款租约、渠道查询、审计及关单边界。
- TradeSchemaGuard / TradeMigrationTool / TradeInfrastructureConfiguration：只读启动检查、显式迁移工具、公共时钟与事务配置。

这些类有真实调用或维护入口，保留它们不表示存在第二套抢票流程。V1～V5历史迁移全部保留。

## 测试迁移

原TradeReservationTest和PaymentTransactionTest已改为新状态机、真实隔离MySQL及Redis；保留原来的付款金额和归属校验、回滚、权限、软删除、元数据与库存调整断言。它们不再构造旧实现，也不再使用H2代替交易SQL验证。测试fixture不模拟Mapper或事务；其中分段锁由真实HTTP、watchdog及双JVM测试覆盖。

删除了只验证旧6并发信号量和旧统一消费分派的测试；新消费者的持久重试、ACK/NACK、非法消息、V3路由及HTTP闭环由对应测试覆盖。测试总数变化不能单独表示覆盖增减。

RetiredTradeEntryTest防止旧实现重新进入产物；TradeApplicationIT中的双应用用例会启动两个独立JVM，共享临时MySQL、Redis、RabbitMQ，通过两个HTTP端口竞争库存及重复请求，不以两个Redisson客户端冒充完整双应用。
