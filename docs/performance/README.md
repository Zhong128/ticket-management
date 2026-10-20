# JMeter 回归压测

此目录提供可复用脚本，不代表已经取得新的性能成绩。原答辩材料的101样本、约99.8次/秒不能当作五分钟持续负载结果。

使用独立测试数据库和独立中间件实例，保留环境规格（CPU/内存/连接池/消费者数）、Git版本、JDK与JMeter版本。不开真实支付、邮件或AI网络调用。不要对原库存导入测试数据。

1. 准备可售票档，至少40000张可用票（用于持续受理场景）。准备至少31000个不同测试用户的有效登录token，存为本地忽略文件 `test-users.csv`，首行为 `token,tierId`。同一票档一个用户只能持一单，重复使用同一token会把限购拒绝误当吞吐量。不要提交token文件。
2. 用JMeter5.6.3或兼容版本在仓库根目录执行：

```powershell
jmeter -n -t docs/performance/reservation.jmx -Jhost=localhost -Jport=8080 -Jusers=test-users.csv -Jthreads=100 -Jduration=300 -l ticket-management/target/load.jtl -e -o ticket-management/target/load-report
```

目标吞吐量6000次/分钟，总时长300秒、爬升10秒。定时器是目标速率，不保证服务达到100RPS；实际值必须从JTL统计。脚本校验业务code及requestId，HTTP200业务失败也计失败。CSV耗尽会停止线程，报告必须确认有效负载持续时长和样本数。

3. 停止请求，等待落单/Outbox收敛，保存申请数、订单数、DB与Redis版本/库存。执行 `assertions.sql`（只适合全新压测数据集，初始库存等于total且没有旧订单）；数据库为最终依据。再在超过30分钟后执行过期断言，确认所有未支付预占释放且回补一次。
4. 单独跑库存100/101买家竞争、同用户不同键并发、同键重试、MQ暂不可用后恢复、Redis不可用后恢复、重复消费、消费者退出重启、回调重复和支付取消竞争。失败注入仅针对隔离服务。不要将“被拒绝也返回200”统计成零错误。
5. 记录吞吐量、P50/P95/P99、业务/传输错误率、Outbox最长积压和收敛时间、库存恒等式断言结果，并保留JTL、报告及SQL结果。未完成这些证据前不写新的简历性能数字。

代码中的 `TradeInfrastructureIT` 检查真实MySQL竞争、confirm/return、TTL/DLX和Redis乱序投影；`TradeApplicationIT` 检查HTTP异步链路。它们属于功能验收，不能替代本压测。
