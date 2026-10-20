# 交易持久层统一为 MyBatis

用户要求：沿用项目原有技术栈，交易整改中的 JdbcTemplate 数据访问全部迁移为 MyBatis；保留既有交易语义，不增加数据库迁移或改动原数据。

1. 将 SQL 按交易核心、支付/退款、可靠消息/任务、库存及Schema检查分配到具名 `@Mapper` 接口。沿用原项目注解SQL习惯，参数均用 `#{}` 绑定，不引入可执行任意SQL的通用Mapper。
2. 核心实现重命名为 `TradeOperationsImpl`；保留Spring事务管理器与短事务边界、FOR UPDATE锁序、条件更新、幂等约束、Outbox同事务和生成主键回填。
3. 迁移现有Service新增的直接SQL及启动检查。MyBatis测试使用同一DataSource的SqlSessionTemplate/SpringManagedTransactionFactory，避免测试误用独立连接导致事务失效。测试准备DDL及断言可使用JdbcTemplate，不属于应用持久层。
4. 更新单元及事务测试、真实MySQL/RabbitMQ/Redis与完整HTTP应用测试，并验证无JdbcTemplate生产引用、无动态SQL透传。保留既有失败/并发/回滚场景。
5. 更新当前设计说明，新增项目根AGENTS.md记录技术栈优先复用规则。运行构建、测试与diff检查，不提交/推送、不迁移原库。

分工接口：
- 核心：新增 `mapper.TradeCoreMapper`；`TradeOperationsImpl(TradeCoreMapper,PlatformTransactionManager,OutboxStore,Clock,boolean)`。主协调者维护共享测试fixture与旧Service适配。
- 支付：新增 `mapper.TradePaymentMapper` 与 `mapper.TradeRefundMapper`，PaymentRepository保留门面（参数改Mapper）；RefundService改依赖Mapper。支付测试由支付实现者适配，fixture增加 `mapper(Class<T>)` 供调用。
- 消息：新增 `mapper.TradeOutboxMapper`、`mapper.TradeTaskMapper`、必要期限/恢复mapper；OutboxStore签名固定为 `(TradeOutboxMapper,ObjectMapper,Clock)`。消息mock测试由消息实现者适配；两个真实infra/app IT与共享fixture由主协调者维护。
- 主协调者：库存/Schema Mapper、原Service适配、共同测试fixture和IT、POM及文档/AGENTS、统一Maven。

各Mapper方法必须保留清晰语义，不能构造一个Mapper执行任意SQL或拼接外部表名。Mapper返回Map用于本次保持已验证行为，列名保持snake_case；时间值兼容Timestamp与LocalDateTime。不得修改其他工作域、不运行并行Maven。

执行状态：代码、文档及AGENTS已更新。2026-10-19 19:45:23 Docker恢复后完整构建通过：91项中90通过、1项原个人环境测试跳过，包含全部10项真实IT；此前Docker阻塞已解除。详见 `mybatis-unification-validation.md`。
