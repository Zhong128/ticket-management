# JMeter隔离性能验收

本分支使用V5 Redis预扣协议。已完成本轮测量，失败档位与通过档位分开记录，不预设吞吐成绩，也不以100次/秒作为门槛。历史44.51次/秒只属于重构前版本。

本轮结果见[2026-10-23 V5恢复验收记录](results-2026-10-23-v5.md)：40次/秒持续档未通过；10次/秒三轮五分钟及场景专项通过。相同负载旧基线对照已完成，未证明重构后吞吐提升，V5异步建单等待明显增加。真实沙箱已独立通过，SMTP已有确认收件记录，均不混入JMeter成绩。

## 运行方式

后续[建单延迟诊断](diagnosis-2026-10-23.md)通过A/B/A隔离对照复现了热点版本竞争和重试消息放大；该诊断阶段仅改变测试变量，当时未修改生产并发策略，不作为吞吐提升成绩。

后续修复及同版本开关对照见[热点票档建单协调](order-coordination-2026-10-23.md)：保留入口分段锁、Lua和库存CAS，在异步建单事务外按票档协调，减少热点无效重试。该记录与上述历史失败档位分别保留，不把局部延迟改善表述为最大吞吐提升。

`TradeLoadIT`默认跳过。显式启用后创建无主机数据挂载的MySQL、Redis、RabbitMQ，执行V1～V5，预热票档，再生成真实Sa-Token会话并调用JMeter。不得连接原库或原数据卷。支付、邮件、AI不走真实外部渠道；沙箱和SMTP低频验收单独执行。

安装[JMeter官方二进制](https://downloads.apache.org/jmeter/binaries/)并校验官方SHA512后，在模块目录、JDK21下运行：

```powershell
$env:JMETER_SCRIPT='C:\path\to\apache-jmeter-5.6.3\bin\jmeter.bat'
$env:RUN_JMETER_LOAD='true'
$env:LOAD_DURATION_SECONDS='30'
$env:LOAD_TARGET_PER_MINUTE='1200'
$env:LOAD_THREADS='60'
mvn -B '-Dtest=TradeLoadIT' '-DargLine=-Djdk.net.unixdomain.tmpdir=C:/short/temp' test
```

Windows临时目录须存在且路径较短，避免JDK套接字初始化失败。JMeter本身也使用模块target作为短临时目录。`LOAD_MODE`可取abundant、soldout、duplicate、mixed、dual、recovery。dual会启动第二个独立JVM和HTTP端口，共享本次临时中间件；recovery暂停消息消费者后恢复，定时兜底仍运行。

设置`LOAD_CAMPAIGN=true`运行阶梯与完整场景：低负载逐级加压，出现落单失败则记录边界；同一候选负载连续三轮五分钟全部成功才作为持续通过记录，失败则降低候选负载重测。默认20段，对比40段及关闭分段锁；关闭锁仍执行Lua与持久化约束。`LOAD_SUSTAINABLE_PER_MINUTE`只用于恢复已有证据的档位，不能把跳过阶梯写成已测试。

## 判定口径

- 业务code及申请ID必须正确，不能只统计HTTP200。售罄场景的明确库存不足单列为预期拒绝，其余拒绝仍计失败。
- CSV字段为`token,tierId,requestKey,userIndex,quantity`。同一次重试复用同用户、同键、同数量；duplicate重复相同CSV行，其余用户独立。Token文件不提交。
- 入口只预扣Redis，PENDING不计入数据库已占库存；建单后HELD计入。最终同时检查总库存等式、Redis配额等式、有效用户占位、订单数量、Outbox、通知和人工任务。
- 达到重试上限而释放的申请单列`releasedWithoutOrder`，不能当成功订单。阶梯允许据此判定当前负载失败；最终持续通过要求该值为0。
- 正常停压后最多180秒收敛；未收敛失败。未来到期等待队列中尚未到期的消息不是应立即清空的积压。
- 受理吞吐来自JTL时间戳；完整批次落单速度另按客户端启动至收敛的实际耗时统计，不能混用。P50/P95/P99只统计抢票采样；订单等待分位数来自数据库时间字段，原字段存在秒级精度，标为粗粒度。
- 分段锁累计等待和乐观冲突使用项目已有Micrometer依赖记录；双JVM场景当前指标明确标为父实例，不假称双实例汇总。

Token、JTL、HTML报告、第二进程配置/日志及数据库摘要都位于Git忽略的`target/load-acceptance`。`analyze-jtl.ps1`可辅助分析，但混合场景需按label区分reserve/query/cancel，不能将全部HTTP样本作为抢票数。`assertions.sql`只适用于停止新请求后的全新隔离数据集。

## 历史记录

实测记录见 [2026-10-20 隔离压测结果](results-2026-10-20.md)与 [2026-10-21 Outbox 整改压测](results-2026-10-21-outbox.md)。后者保留了邮件积压导致失败的历史轮次；最新五分钟复验有 **13,519 次请求、0 业务失败，实际 44.51 次/秒**，停压时 Outbox 和邮件待处理均为 0，隔离环境完整链路验收通过。真实支付宝沙箱与低频 SMTP 投递另见[沙箱验收](../alipay-sandbox-acceptance.md)和[邮件验收](../smtp-acceptance.md)，均不属于本轮性能成绩；100 次/秒目标尚未达成，也不在当前优化计划中。
