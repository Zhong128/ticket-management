# JMeter 回归压测

2026-10-22：本分支已切换V5 Redis预扣协议，下面持续成绩属于重构前版本。阶段6须先适配预热、PENDING/HELD断言和双实例场景，再运行新版压测；未产生本轮新吞吐结果。

此目录提供可复用脚本，不代表已经取得新的性能成绩。原答辩材料的101样本、约99.8次/秒不能当作五分钟持续负载结果。

使用独立测试数据库和独立中间件实例，保留环境规格（CPU/内存/连接池/消费者数）、Git版本、JDK与JMeter版本。不开真实支付、邮件或AI网络调用。不要对原库存导入测试数据。

`TestDataController` 的 `/api/public/test/**` 用户生成和清理接口默认关闭。若在专用、隔离且不对外开放的压测实例中确实需要旧生成器，显式设置 `TEST_DATA_ENABLED=true`；压测结束即关闭。该接口可创建、导出和删除测试用户，不能在普通服务实例上启用。下列脚本只读取预先准备的本地 `test-users.csv`，无需开启该接口。

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

## 全新隔离环境一键验收

`TradeLoadIT` 是默认跳过、显式启用的测试入口。它用 Testcontainers 创建无主机数据挂载的 MySQL、Redis、RabbitMQ，在新库执行迁移并准备独立用户、Sa-Token 会话和库存，再调用本目录的 JMeter 脚本；压测完成后核对 Outbox、库存范围与库存恒等式。支付宝、邮件、AI 为测试替身。账号 Token、JTL、报告写入被 Git 忽略的模块 `target/load-acceptance`，不得提交。

将官方 JMeter 5.6.3 解压到本机，设置 `JMETER_SCRIPT` 为其 `bin/jmeter.bat` 的绝对路径，并在模块目录、JDK 21 环境下运行：

```powershell
$env:JMETER_SCRIPT='C:\path\to\apache-jmeter-5.6.3\bin\jmeter.bat'
$env:RUN_JMETER_LOAD='true'
$env:LOAD_DURATION_SECONDS='300'
$env:LOAD_TARGET_PER_MINUTE='6000'
$env:LOAD_THREADS='100'
mvn -B '-Dtest=TradeLoadIT' '-DargLine=-Djdk.net.unixdomain.tmpdir=C:\Users\LIANXIANG\IdeaProjects\ticketManagement\ticket-management\target' test
```

`LOAD_TARGET_PER_MINUTE` 是目标速率，不是成绩；必须以生成的 JTL 计算实际吞吐量和错误率。可运行 `./docs/performance/analyze-jtl.ps1 -Jtl ticket-management/target/load-acceptance/<本次目录>/load.jtl` 计算样本数、错误率与 P50/P95/P99。环境准备时间、JMeter 300 秒发送阶段和异步消息收敛时间应分开记录。独立运行既有脚本时也可传 `-Jtarget_per_minute=6000` 调整目标速率。实测记录见 [2026-10-20 隔离压测结果](results-2026-10-20.md)与 [2026-10-21 Outbox 整改压测](results-2026-10-21-outbox.md)。后者保留了邮件积压导致失败的历史轮次；最新五分钟复验有 **13,519 次请求、0 业务失败，实际 44.51 次/秒**，停压时 Outbox 和邮件待处理均为 0，隔离环境完整链路验收通过。真实支付宝沙箱与低频 SMTP 投递另见[沙箱验收](../alipay-sandbox-acceptance.md)和[邮件验收](../smtp-acceptance.md)，均不属于本轮性能成绩；100 次/秒目标尚未达成，也不在当前优化计划中。
