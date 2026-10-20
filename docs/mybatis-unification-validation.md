# MyBatis统一改造与验证记录

日期：2026-10-19（Asia/Shanghai）；分支：`codex/transaction-hardening`。MyBatis统一改造没有新增数据库迁移，也没有改动原数据；后续代码风格整理与完整复验见 [代码风格记录](code-style.md)。

## 最新Docker复验：已通过

2026-10-19 19:45:23，Docker引擎恢复后实际执行完整 `package`：**91项，90通过、0失败、0异常、1跳过，BUILD SUCCESS**。跳过的仍是原个人环境contextLoads测试；本轮完整应用启动和HTTP链路已由TradeApplicationIT实际通过。

| 真实集成测试 | 项数 | 结果 |
|---|---:|---|
| LegacyMigrationIT | 1 | MySQL历史迁移和Schema Mapper校验通过 |
| TradeInfrastructureIT | 7 | MySQL并发/幂等、支付/退款Mapper、Rabbit确认/退回及TTL/DLX、Redis乱序版本全部通过 |
| TradeApplicationIT | 2 | 完整Spring装配、公共HTTP、真实登录抢票到异步建单及同键重放通过 |

使用无主机数据挂载的隔离MySQL8、Redis7、RabbitMQ3.12容器；应用测试在本机JDK21 JVM执行，连接上述Docker服务。测试容器已自动回收，未使用原数据库或原数据卷；支付、邮件、AI外部调用仍为替身。日志：`ticket-management/target/docker-mybatis-recheck.log`，报告：`target/surefire-reports`。此前Docker阻塞已解除，无需再将这三类IT记为待复验。

## 代码变化

- `JdbcTradeOperations` 更名为 `TradeOperationsImpl`，全部SQL移入 `TradeCoreMapper`。
- 新增9个具名MyBatis Mapper：TradeCoreMapper、TradePaymentMapper、TradeRefundMapper、TradeOutboxMapper、TradeTaskMapper、TradeRecoveryMapper、TradeStockMapper、TradeOrderQueryMapper、TradeSchemaMapper。
- 支付、退款、Outbox、邮件任务、期限处理、旧消息桥接、恢复扫描、库存维护、订单备注及Schema检查均调用Mapper；SQL沿用项目注解方式并绑定参数。
- 业务事务、锁顺序、条件更新、生成主键回填、幂等键、退款租约和绝对截止时间保持原规则。没有使用任意SQL透传Mapper来包装另一套数据访问技术。
- 项目根 `AGENTS.md` 记录优先沿用原技术栈及分层习惯，只有优势明确时才引入替代技术并解释理由。

## 已执行并通过

1. `MyBatisPersistenceBoundaryTest` 先实际失败，定位到16处JdbcTemplate生产引用；迁移后通过。生产Java源扫描无JdbcTemplate、GeneratedKeyHolder、旧JdbcTradeOperations或prepareStatement引用；9个新增Mapper无`${}`SQL文本插值。
2. 最终执行 `mvn -B '-Dtest=*Test,*Tests' package`：81项，80通过、0失败、0异常、1跳过；2026-10-19 19:36:59构建成功。跳过的是原个人环境contextLoads测试，需要显式环境变量启用。
3. 回归使用真实MyBatis代理（SqlSessionTemplate + SpringManagedTransactionFactory），与Service使用同一DataSource和Spring事务管理器。H2只负责隔离事务验收，测试准备DDL及独立SQL断言仍可使用JdbcTemplate。
4. 保留预占并发、重复键、重复取消、Outbox失败回滚、支付/订单同事务回滚、迟到收款、重复退款等旧断言；增加退款租约恢复、绝对TTL及邮件次数耗尽回归。
5. `git diff --check`通过；独立只读复核未发现此次Mapper迁移引入的明确P1/P2阻塞问题。
6. 最终JAR：`ticket-management/target/ticket-management-0.0.1-SNAPSHOT.jar`，126922821字节。检查JAR中存在新Mapper及TradeOperationsImpl，不含旧JdbcTradeOperations或本地凭据配置。

日志：`ticket-management/target/mybatis-boundary-red.log`、`mybatis-first-tests.log`、`mybatis-unit-package.log`。构建产物及日志按原规则忽略，不进入Git。

## 此前Docker阻塞记录（现已恢复）

已尝试 `mvn '-Dtest=*Test,*Tests,*IT' ... package`，但Docker引擎未启动，三类IT在创建容器之前报环境错误，未执行测试体；不能当作通过或跳过成功。对应日志为 `target/mybatis-full-build.log`。

Docker Desktop后台启动报运行时socket错误：`AppData/Local/Docker/run/dockerInference` 系统无法访问。尝试启动与对残留运行时文件改名均未恢复；改名未成功，没有重置Docker、删除socket、改变Docker配置或访问原数据卷。

之后Docker恢复，LegacyMigrationIT、TradeInfrastructureIT、TradeApplicationIT已全部重跑通过。新增真实MySQL支付/退款Mapper用例也已实际通过，覆盖主键回填、全额补偿、租约完成及重复通知。原库仍保持不迁移、不启用新交易。

Windows当前JDK的集成验收仍需沿用已验证的临时目录参数：

```powershell
mvn -B '-Dtest=*Test,*Tests,*IT' '-DargLine=-Djdk.net.unixdomain.tmpdir=C:\Users\LIANXIANG\IdeaProjects\ticketManagement\ticket-management\target' package
```

从模块目录运行，使用JDK21。最新结果来自MyBatis版本本轮实际执行，不能把此前JdbcTemplate版本成绩或单独H2测试当作真实集成验收结果。JMeter持续压测及真实支付宝沙箱端到端验收仍未执行。
