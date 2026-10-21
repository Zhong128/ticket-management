# 完整售票流程隔离验收

日期：2026-10-20（Asia/Shanghai）。测试入口：`TradeApplicationIT.fullSaleJourneyCoversPaymentCancellationTimeoutAndRefund`。

## 环境与边界

Spring Boot 应用以 JDK 21 在测试 JVM 的随机 HTTP 端口启动。Testcontainers 为本次验收新建 MySQL 8、Redis 7、RabbitMQ 3.12 容器，不挂载原中间件数据卷；测试配置不导入个人配置。Flyway 在空白测试库执行 V1～V4，分类、城市及测试账号只写入该库。测试结束由 Testcontainers 回收容器。

业务读写仍走实际 Controller、Service、MyBatis Mapper、事务和 RabbitMQ 消费者。测试中的 `JdbcTemplate` 仅用于隔离库数据准备、推进超时截止时间及独立断言。支付宝签名验证、支付/退款渠道、邮件和 AI 使用测试替身；没有实际转账、退款或发送邮件。

## 覆盖的业务链路

1. 管理员登录后通过 HTTP 创建演出、场次和票档；用户登录，通过 HTTP 抢票并查询申请，等待 Outbox 消息发布及 RabbitMQ 消费者异步建单。随后通过用户订单列表与详情、管理员订单详情核对新订单。
2. 用户通过 HTTP 创建支付流水，用模拟的已验签支付宝异步通知付款；重复通知仍只确认一次付款，通过 HTTP 查询订单详情和支付状态，核对订单、流水及预占状态保持一致，库存不重复变化。
3. 第二位用户创建订单和支付流水，渠道确认关单后取消；检查订单已取消、预占已释放、库存恢复。
4. 第三位用户遇到渠道状态未知，取消处于处理中且库存继续占用；渠道随后确认关闭，再次取消才释放库存。
5. 第四位用户未创建支付流水；在隔离库推进订单绝对截止时间后触发恢复任务，检查超时订单及预占释放。
6. 第五位用户取消后收到迟到付款通知；检查补偿退款任务生成，测试网关返回退款成功，用户退款查询显示成功，库存没有重复回补。
7. 等待 Redis 库存投影、付款成功 Outbox 和付款邮件任务收敛；最终核对五笔订单、库存和未结预占数量。

这里的“完整”指上述业务分支闭环，并非真实支付宝沙箱、SMTP 投递或持续并发压测。回调验签由单独测试覆盖；此 HTTP 集成测试用签名组件替身，验证验签后的业务处理。超时通过测试库修改截止时间触发，未实际等待 30 分钟。

## 运行与结果

从 `ticket-management` 目录，使用 JDK 21、可用的 Docker 引擎，运行：

```powershell
mvn -B '-Dtest=*Test,*Tests,*IT' '-DargLine=-Djdk.net.unixdomain.tmpdir=C:\Users\LIANXIANG\IdeaProjects\ticketManagement\ticket-management\target' package
```

Windows 当前环境需要指定 `jdk.net.unixdomain.tmpdir`，原因及参数说明见 [交易整改说明](transaction-hardening.md)。测试日志和 Surefire 报告写入模块 `target` 目录，不纳入 Git。

2026-10-20 20:43（Asia/Shanghai）完整执行结果：**92 项测试，91 项通过、0 失败、0 异常、1 项跳过，`BUILD SUCCESS`**。其中 `TradeApplicationIT` 的 3 项均通过，新增完整售票链路测试通过。跳过的是需显式启用个人环境的原 `TicketManagementApplicationTests.contextLoads`；隔离环境中的实际应用启动由 `TradeApplicationIT` 验证。

本次构建日志为 `ticket-management/target/full-sale-regression.log`，Surefire 报告位于 `ticket-management/target/surefire-reports`。测试后检查无存活的 Testcontainers 容器；登录时完整 Token 输出及测试用户 Token 片段日志已移除，并检查构建日志未出现这两种 Token 输出。测试数据生成与清理 Controller 默认关闭，应用启动测试会核对它未加载；仅隔离压测实例可以显式开启，说明见 [JMeter 回归压测](performance/README.md)。上述日志与构建产物均由 Git 忽略。

这段记录保留 2026-10-20 的历史结果。2026-10-21 的五分钟 JMeter 复验已另行通过，实测 13,519 次请求、0 业务失败、44.51 次/秒；详见 [Outbox 整改压测记录](performance/results-2026-10-21-outbox.md)。真实支付宝沙箱仍未验收。

2026-10-21 16:24（Asia/Shanghai）再次执行全部单元与隔离集成测试并打包：**99 项测试，97 项通过、0 失败、0 异常、2 项按条件跳过，`BUILD SUCCESS`**，生成 `ticket-management/target/ticket-management-0.0.1-SNAPSHOT.jar`。本次新增真实 HTTP 的用户订单列表与详情、管理员订单详情和用户支付状态断言，`TradeApplicationIT` 的 3 项均通过。跳过项为依赖个人环境的 `TicketManagementApplicationTests.contextLoads` 和需显式启用 JMeter 的 `TradeLoadIT.sustainedReservationsUseFreshUsersAndStock`；后者已在前述五分钟复验中单独运行通过。本次日志位于被 Git 忽略的 `ticket-management/target/closure-full-regression.log`。

后续支付回调审计修复后，`TradeApplicationIT` 的 3 项又在新的隔离容器中全部通过；真实支付宝沙箱通知和真实 SMTP 投递也已分别完成专项验收。当前总览及边界见[验收清单](acceptance-checklist.md)，本段历史测试数字不代表后续新增测试的总数。
