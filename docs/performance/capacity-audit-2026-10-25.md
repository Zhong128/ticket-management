# 容量失败代码审查与下一步（2026-10-25）

本次只审查和整理，不修改业务实现。实测数据见[容量记录](capacity-2026-10-25.md)，实现起点为`c3fabbc`。以下区分已证实的现象与仍需实验的原因。

## 已定位的调用链

| 位置（模块src/main/java/org/example/ticketmanagement下） | 实际职责与本次发现 |
| --- | --- |
| `serviceimpl/SeckillServiceImpl.reserveApplication/acceptUnderLock` | 入口分段锁覆盖Lua预扣、可靠受理事务和提交后Redis确认；不是只覆盖一条Lua。没有在入口扣MySQL库存，也没有锁内调用支付宝或SMTP。 |
| `serviceimpl/SeckillLockServiceImpl.executeWithSegmentLock`、`util/RedissonLockUtil.executeWithWatchdog` | 按票档和用户取模分段，默认20段、等待200ms、watchdog。未取得锁会返回本轮JTL中的“抢票请求繁忙”，40持续档68次拒绝可定位至此。未证明提高等待时间就能提升完成建单吞吐。 |
| `serviceimpl/ReservationServiceImpl.accept` | 事务校验代次、锁受理日志与购买占位、检查售卖、写申请及三个事件；受理日志和Outbox不能为了少写SQL直接删除。该事务耗时会延长入口锁持有。 |
| `trade/messaging/OutboxPublisher.publishDue/publishBatch` | 每批最多100条，连续最多10批；先领取租约和整批发送，再共享5秒确认期限，最后批量标成功。当前已不是逐条等待确认，不能再把旧问题当成现状。 |
| `mapper/TradeOutboxMapper.findDueEvents` | 按到期时间选NEW或过期租约事件，LIMIT 100；应以真实执行计划和数据规模检查OR条件、排序及索引，不凭SQL外观直接改索引。 |
| `serviceimpl/ReservationServiceImpl.createOrder/createWithVersionRetry/createInTransaction` | 消费者和扫描调用同一Service；事务外票档协调，事务内锁申请、库存充足及版本CAS、建单和事件提交。最多三次立即冲突重试，每次新事务。 |
| `serviceimpl/OrderCreationCoordinator.execute` | 同票档建单协调锁默认等待2000ms，等待不占数据库事务；watchdog续期。热点建单事务受串行协调约束，独立票档可并行；不能因此断言它是全部吞吐损失的原因。 |
| `serviceimpl/OrderRecoveryService.recover`、`mapper/TradeRecoveryMapper` | 游标每次至多10批，每批100；仅扫描RESERVED及已到期ORDER_CREATED，未到期活跃订单不占申请扫描批次。另有到期/取消中订单查询，不带LIMIT，值得单独测历史订单集中到期时的负载。 |

## 证据支持到哪里

- 40持续档父实例库存版本冲突为0，仍有严重积压，当前不能继续把全部问题归因于乐观锁冲突。混合交易短测出现3次冲突并正常收敛，CAS仍有实际防线作用。
- 40持续档CREATE_ORDER从事件创建到首发确认标记P95为61.866秒。这包含排队、发布及数据库标记等时间，不是Broker Confirm网络耗时，也不能单凭此认定RabbitMQ慢。
- 同档`sql-timings.tsv`：Outbox插入49,211次、累计458,471.863ms；待发布查询896次、累计126,300.7373ms、单次最大2655.7298ms；标成功885次、累计70,019.8106ms。它们说明持久化调用值得分解，但累计并发耗时不是墙钟时间，也没有独立分离连接等待、数据库执行、锁等待、主机资源与采样开销。
- 同批历史数据持续保留，自动降档的10次/秒第二轮出现5次入口拒绝且被主动中断；当时已跨越早期订单截止窗口。后台到期处理是待验证变量，不能写成已找到根因。独立新数据集10次/秒三轮通过也不覆盖这个缺口。
- 本轮未采集完整CPU、磁盘、数据库锁等待和连接池占用时间线；没有足够证据将瓶颈定性为单机硬件上限或流程必然缺陷。

## 建议下一批小阶段

1. **先复现后台叠加变量**：固定资源和新数据集，分别无历史订单、有未到期历史订单、有集中到期历史订单，保持同样新请求负载。保存入口锁等待/持有、受理事务、建单事务、连接池、发布各阶段、后台关单数量的时间线。历史到期场景必须核对释放、通知及两条库存等式，不能只跑新单。
2. **再定位持久化与发布等待**：对`findDueEvents`及恢复查询在隔离MySQL执行计划核查；区分获取连接、SQL、事务提交、事件待领、发送确认、消费等待。先证实高占比，再做单变量优化；必要索引使用新迁移，不改历史V1～V5。
3. **只修已复现热点并回归**：候选包括受理事务冗余读取、Outbox扫描、关单有界批次，但现在均未批准为确定实现。保持受理唯一键、代次屏障、库存CAS、支付锁序、可靠通知和事务语义。锁参数调整另做对照，不用加长等待掩盖积压。
4. **复测而非承诺成绩**：先短阶梯，再候选负载三轮五分钟，包含历史到期混合场景；最终180秒内收敛且无业务失败才通过。独立20次/秒三轮尚未执行，不能由共享批次的失败推断其必然失败；不以100次/秒为门槛。

本次未恢复两套并行交易入口，也未移除分段锁、Lua或乐观锁。优化应继续沿原有Service、MyBatis、Redis和RabbitMQ协同链路开展。
