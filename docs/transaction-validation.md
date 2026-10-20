# 本轮构建与验证记录

> 本文件保留此前交易整改版本的验证记录。业务持久层随后已统一为MyBatis，最新结果及Docker环境阻塞见 [MyBatis统一验证记录](mybatis-unification-validation.md)。此前真实IT通过不能替代重构后的重新验收。

日期：2026-10-19；分支：`codex/transaction-hardening`；基线：`8e52a22`。本文件保留交易整改早期的验证过程，当前代码与最新结果以仓库现状及后续验证记录为准。

## 实际执行结果

| 验证 | 结果 |
|---|---|
| 包含单元、事务、全部IT的Maven package（13:52完成） | 85项：84通过、0失败、0异常、1跳过；JAR构建成功 |
| 最长历史支付流水号退款回归 | 先实际失败：回调返回failure；修复独立退款号后通过 |
| 最终单元/事务测试及package（13:53完成） | 77项：76通过、0失败、0异常、1跳过；JAR重新打包成功 |
| 最终Surefire报告合计（包含前次9项IT结果） | 86项：85通过、0失败、0异常、1跳过；不是一次执行86项的性能成绩 |
| git diff --check | 通过 |
| JMeter JMX XML解析 | 通过；未实际执行新一轮JMeter压测 |

最终产物：`ticket-management/target/ticket-management-0.0.1-SNAPSHOT.jar`，126915146字节。构建输出与报告位于忽略的 `target` 目录。

跳过的是原有依赖个人本地环境的 `TicketManagementApplicationTests.contextLoads`，只有显式设置 `RUN_LOCAL_CONTEXT_TEST=true` 才运行；完整应用启动已由隔离的 `TradeApplicationIT` 实际覆盖。

## 真实基础设施验收

使用本机缓存镜像 `mysql:8.0`、`redis:7-alpine`、`rabbitmq:3.12-management-alpine`。Testcontainers创建全新无主机数据挂载的容器，未连接原数据库或使用原中间件数据卷。

- `LegacyMigrationIT`：V1已有订单和支付历史升级V2/V3，保留记录与金额、回填截止时间、历史冲突guard、仅成功支付可作为订单接受的付款引用。
- `TradeInfrastructureIT`：24个买家竞争10张库存（每单2张）只受理5单；重复申请/落单/取消幂等；真实Rabbit确认和mandatory return；3秒缩短期限验证TTL/DLX；Redis乱序及大于2^53版本比较。
- `TradeApplicationIT`：真实Spring Boot随机端口启动，完整Bean装配、迁移先于启动校验，公共HTTP查询、真实登录/Redis会话、抢票受理、Outbox发布/消费、异步落单、查询结果与同键重放。

外部支付宝、邮件和AI使用可控替身，不调用真实资金或发送外部邮件。Windows JDK的8.3临时目录导致Selector失败，最小探针确认是环境问题；指定项目target为 `jdk.net.unixdomain.tmpdir` 后通过，详见迁移说明。

## 实际复现后修复的边界

初始验签删sign、死信回补和缺少Outbox测试先失败；后台metadata编辑覆盖已扣库存先失败；演出/场次物理删除级联清空票档的两个用例先失败；64字符历史流水号导致退款号过长用例先失败。对应修复均已通过回归。

支付事务测试覆盖取消释放后收款、重复回调、退款后再回调、合法付款晚通知、第二笔收款补偿、Outbox失败时支付/订单一起回滚；网关调用断言不持有数据库事务。

## 尚未执行

没有对原库做迁移或启用新交易，没有部署或推送；没有新的JMeter持续负载结果，也没有支付宝沙箱端到端人工付款/退款验收。已提供迁移脚本、接口说明、JMeter脚本和压测数据库断言；这两类外部验收不能用单元测试结果替代。

后续启动前阅读 `transaction-hardening.md`；性能回归参考 `performance/README.md`。静态独立复核未发现新增可确认的P1/P2，但这不代表整个原项目已经完成所有功能和安全审计。
