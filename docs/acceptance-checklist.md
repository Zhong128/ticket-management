# 售票项目验收清单

本清单区分**当前版本已实测**、**测试替身覆盖**和**尚未实测**。历史记录中的“当时未验收”保留原时间语境；判断当前状态以本页及各专项记录的最新段落为准。所有交易测试只用新建隔离库及临时中间件，不迁移、清空或重建原数据库。

## 已完成的验收

| 检查项 | 当前证据 | 边界 |
|---|---|---|
| 管理员上架、用户登录、抢票申请查询、RabbitMQ 异步建单、订单列表和详情 | `TradeApplicationIT` 真实 HTTP／MyBatis／MySQL／Redis／RabbitMQ 的 3 个用例通过；[完整流程记录](full-sale-isolated-acceptance.md) | 支付渠道、邮件和 AI 在该测试中使用替身 |
| 重复回调、取消、渠道未知、超时释放、迟到付款退款及库存恒等式 | `TradeApplicationIT`、`TradeInfrastructureIT` 与交易单测；审计改动后 `TradeApplicationIT` 3 项再次通过 | 测试中的超时通过隔离库推进截止时间触发，未自然等待 30 分钟 |
| 真实支付宝沙箱付款、有效异步通知与回调审计 | 2026-10-21 新版本收到一条 `TRADE_SUCCESS`：`notify_time` 非空，审计日志 1 条，订单与支付已成功；[沙箱记录](alipay-sandbox-acceptance.md) | 本次新版本没有重放该笔真实通知；重复送达由隔离集成测试覆盖 |
| 已存在未付款沙箱交易的项目关单；迟到付款退款及退款查询 | 项目取消接口后支付宝返回 `TRADE_CLOSED`；另一笔迟到付款形成退款单且渠道查询 `REFUND_SUCCESS`；[沙箱记录](alipay-sandbox-acceptance.md) | 迟到场景通过隔离库人为推进本地截止时间；前一次沙箱网关偶发 404 已在原记录保留 |
| 两类真实 SMTP 通知 | 订单创建和支付成功邮件各 1 封，SMTP 接受且项目作者确认收件箱收到；[邮件记录](smtp-acceptance.md) | 低频投递验收，不代表大批量 SMTP 吞吐 |
| 五分钟持续抢票与异步任务收敛 | 13,519 次请求、0 业务失败，实际 44.51 次／秒；停压时 Outbox、邮件待处理均为 0；[压测记录](performance/results-2026-10-21-outbox.md) | 单机隔离环境、独立买家、支付及邮件渠道替身；历史失败轮次同页保留，不能写成达到 100 次／秒 |
| JAR 构建与 Docker 启动命令 | Maven 构建已通过；Dockerfile 原 JVM 参数顺序错误已修正。本轮 `docker compose config --quiet` 与本地镜像构建通过，镜像入口经检查为 `java -Dspring.profiles.active=prod -jar app.jar` | 未用 Compose 连接原数据卷启动应用；镜像构建通过不等于业务接口已验收 |

## 复现时按这个顺序

1. 阅读[模块启动说明](../ticket-management/README.md)，准备 Java 21、Maven 和 Docker。默认 `TRADE_ENABLED=false`、自动迁移关闭；先在**空白隔离库**执行 V1～V4，或按[迁移说明](transaction-hardening.md)处理已有旧表，再启用交易。
2. 在模块目录执行 `mvn -B '-Dtest=*Test,*Tests,*IT' package`。`*IT` 需要 Docker；`TradeApplicationIT` 验证真实 HTTP 业务链路。若本机 JDK 的 Unix domain socket 临时目录报错，按[环境说明](transaction-hardening.md)设置测试 JVM 的临时目录，不要跳过集成测试冒充通过。
3. 核对五笔典型订单：付款、取消、渠道未知、超时、迟到付款。以 `order_info`、`stock_reservation`、`payment_record`、`refund_record`、`message_outbox` 和 `payment_compensation` 为依据；库存断言见[隔离 SQL](performance/assertions.sql)。
4. 真实支付宝验收只在隔离环境使用沙箱账号、沙箱 AppID 和公网通知地址。同步回跳不是付款凭据；检查异步回调、渠道查单、回调审计和数据库状态。不要把商户私钥、买家密码、Token 或完整回调参数写入报告或 Git。
5. SMTP 验收须显式启用 `TradeSmtpIT`，且只向自己控制的测试邮箱发送；JMeter 验收须显式启用 `TradeLoadIT`，保留 JTL、错误率、分位数、实际吞吐和任务收敛证据。两者都不属于默认测试的自动实网动作。

## 仍需如实说明

- 新版本的**真实支付宝重复通知重放**没有在最近一笔沙箱付款上再次执行；当前代码的重复回调由隔离 HTTP 测试验证，旧版本曾重放真实通知。若简历或答辩要声称“新版本真实签名通知重放”，应单独补证据。
- 自然等待 30 分钟的端到端超时、真实 SMTP 大批量发送、生产多实例容量均没有完成专项验收；现有故障注入和单机结果不能替代它们。
- 100 次／秒只是过去脚本的目标速率，实测 44.51 次／秒；目前不把吞吐优化纳入本阶段计划。
- 微信登录、AI 客服等外围服务没有被上述售票闭环验收覆盖。介绍项目时可以说明已实现模块，不要将售票主链路的测试结果套到这些外部服务上。
