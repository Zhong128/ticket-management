# 真实 SMTP 通知验收

日期：2026-10-21（Asia/Shanghai）。结果：**订单创建与支付成功两类邮件均通过真实 SMTP 投递，测试收件箱确认收到两封**。

验收入口为显式启用的 `TradeSmtpIT`。Spring Boot 应用使用新的 Testcontainers MySQL、Redis、RabbitMQ，执行 V1～V4 迁移；测试账号、票档和订单只写入隔离库，结束后容器自动回收。收发邮箱均取 Git 忽略的 `.env` 中 `SPRING_MAIL_USERNAME`，授权码取 `SPRING_MAIL_PASSWORD`，不写入测试代码、报告或仓库。测试只向该邮箱发送两封邮件，没有给其他用户发信。

测试通过真实 HTTP 登录、抢票和异步建单，等待 `EMAIL_ORDER` 任务完成；随后使用测试替身模拟已验签支付回调，不调用真实支付宝渠道或真实资金，等待 `EMAIL_PAYMENT` 任务完成。两类任务的 `task_status` 都为 2；重复提交同一付款通知后，每类邮件任务仍各只有一条。Spring Mail 实际向 `smtp.qq.com:587` 使用 STARTTLS 发送，应用日志记录两次发送成功、零次发送失败。项目作者检查测试邮箱后确认收到了主题为“订单创建成功通知”和“支付成功通知”的两封邮件。

真实投递这轮 `TradeSmtpIT` 为 1 项通过、0 失败、0 跳过，耗时约 38 秒，原始运行日志位于 Git 忽略的 `ticket-management/target/smtp-acceptance-run.log`。随后清除邮件环境变量再次运行同一测试，门禁自检为 1 项跳过、耗时约 0.005 秒，没有启动隔离服务或发信；此轮覆盖了本地 Surefire 报告，真实投递结果以首次运行日志、数据库断言和收件箱确认为准。测试类默认不运行；重验时须显式设置 `RUN_SMTP_ACCEPTANCE=true`，从本地忽略配置提供上述两个环境变量，再用 JDK 21 执行 `-Dtest=TradeSmtpIT`。本机运行时在 Maven `argLine` 中指定已有 Byte Buddy Agent 和 `jdk.net.unixdomain.tmpdir`，没有修改项目运行依赖。

验收范围是低频真实投递与任务幂等，不代表 SMTP 服务的大批量发送能力。真实支付宝付款、回调和退款的独立验收见 [支付宝沙箱验收记录](alipay-sandbox-acceptance.md)；本测试的支付通知使用测试替身，不把它记为第二次真实支付宝付款。
