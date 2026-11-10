# 历史订单集中到期对照

状态（2026-11-10）：基线 `42ff721` 加测试夹具，使用 Docker 28.5.1 完成三组独立五分钟压测。三组 Maven 均通过，未接入日常库。

| 组 | 新请求／订单 | 接口 P95 | 建单 P95（粗粒度） | 停压待建单／Outbox／邮件 |
| --- | ---: | ---: | ---: | ---: |
| A 无历史订单 | 2996／2996 | 89ms | 740ms | 0／0／0 |
| B 1000 笔未到期 | 2996／2996 | 95ms | 721ms | 0／0／0 |
| C 1000 笔集中到期 | 2996／2996 | 94ms | 754ms | 0／0／0 |

三组零失败、零排空等待。C 历史订单关闭 1000、预占释放 1000、数据库库存与 Redis 配额各恢复到 1000。以 C 的实际截止时点相对首样本偏移对齐三组，截止前一分钟及随后两分钟的接口 P95：A 为 53／91／104ms，B 为 93／97／93ms，C 为 92／96／95ms。此条件下没有观察到 C 相对 B 的明显退化，不触发额外两轮或业务优化。每组仅一轮，且没有完整阶段耗时时间线，不能推广到更高负载或其他到期分布。

本次组别日志位于模块 `target/expiry-{none,future,due}.log`；JTL 和断言摘要在隔离副本的 `ticket-management/target/load-acceptance/`，按运行时间与组别日志对应。失败轮次也保留，不能用后续组的成功覆盖。

在隔离副本的模块目录使用 JDK 21，设置 `JMETER_SCRIPT` 为本机 JMeter 启动脚本。每次 Maven 调用启动独立 Testcontainers；先运行 A、B、C 各一轮。请将 `argLine` 中的路径替换为存在的短临时目录。

```powershell
$env:RUN_JMETER_LOAD = 'true'
$env:LOAD_CAMPAIGN = 'false'
$env:LOAD_DIAGNOSTICS = 'true'
$env:LOAD_MODE = 'abundant'
$env:LOAD_DURATION_SECONDS = '300'
$env:LOAD_TARGET_PER_MINUTE = '600'
$env:LOAD_THREADS = '60'
foreach ($group in @('none', 'future', 'due')) {
    $env:LOAD_EXPIRY_HISTORY = $group
    mvn -B '-Dtest=TradeLoadIT' '-DargLine=-Djdk.net.unixdomain.tmpdir=C:/short/temp' test *> "target/expiry-$group.log"
    Write-Output "$group Maven exit=$LASTEXITCODE"
}
```

B、C 在独立票档走真实受理、Outbox、建单和通知任务；C 在 JMeter 进程启动后约两分钟到期。为加速测试，只在临时库调整订单和预占截止时间；已经发布的延迟消息不重写，因此本实验定位数据库扫描导致的共享资源争用，不替代延迟队列验收。

按各轮 `load.jtl`、`database-summary.txt`、`history-summary.txt` 和 SQL 诊断比对。采样须以实际 JTL 时间戳对齐，不把 JMeter 进程启动当作首个请求。现有采样还缺逐阶段时间线，归因前需补齐；不得用累计 SQL 耗时冒充连接等待或 Broker 确认耗时。没有稳定复现和证据前不改业务代码。
