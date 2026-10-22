# 高并发售票与交易可靠性协同重构执行计划

状态：用户已批准，分阶段实施。第 1 阶段记录见 [重构前基线](high-concurrency-refactor-baseline.md)。

## 目标与边界

恢复原有高并发设计的实际调用：Redis Lua 原子预扣与限购 → 申请及 Outbox 轻量落库 → 返回受理 → RabbitMQ 异步扣数据库库存并建单 → 支付或超时关闭 → 幂等释放与补偿。

- 沿用 Java 21、Spring Boot、MyBatis、MySQL、Redis/Redisson、RabbitMQ、Sa-Token，不新增中间件。
- 用户分段锁默认 20 段，等待 200ms，使用 watchdog；不做库存分桶。锁内不调用支付宝、SMTP 或等待 Broker Confirm。
- 入口不锁票档扣数据库库存；MySQL 仍提供持久化约束。订单、申请、资金和历史迁移均保留。
- 同用户同票档最多一个有效申请或订单，每单 1～5 张；取消释放后可再次申请，已支付继续占用资格。
- 不预设性能成绩，不以 100 次/秒为目标或验收门槛。真实支付宝沙箱和 SMTP 单独低频验收。
- 数据库业务访问统一 MyBatis，保持原有 Service 分层、4 空格缩进和简短中文逻辑阶段注释。
- 测试只使用隔离容器，不自动迁移原库，不操作原数据卷，不测试真实资金。只创建本地提交，不推送。
- 每阶段结束报告结果、剩余问题与下一步，并询问用户是否继续；这条用户要求优先于技能中的连续执行建议。

## 原代码复用和职责

| 入口 | 复用与修改 |
| --- | --- |
| SeckillServiceImpl | 参数、售卖校验、结果组装；恢复分段锁、Lua 和可靠受理 |
| StockServiceImpl | 键、库存查询、脚本调用；统一预扣、确认、释放，不直接加库存或删除限购 |
| SeckillLockServiceImpl / RedissonLockUtil | 用户分段、线程持有检查与 finally 解锁；支持毫秒等待、watchdog、中断和业务异常传播 |
| OrderServiceImpl | 权限、分页、详情与 VO；承接事务建单、取消、一次性释放 |
| PaymentServiceImpl | 支付入口、归属、标题描述；复用当前统一入账、渠道未知、审计和退款 |
| ReconciliationServiceImpl | 逐票档检查与统计；替换直接覆盖配额的算法 |
| EmailServiceImpl | 现有模板和发送方法，由持久通知任务调用 |
| RabbitMQUtil | 统一 JSON、持久化、业务头和关联确认封装，关键消息只能经 Outbox |
| OrderNoGenerator / PaymentNoGenerator | 收回散落编号逻辑；保持现行 UUID 格式，不恢复 Redis 日序列 |
| AlipaySignatureUtil | 复用已修复验签，不恢复删除 sign 的旧代码 |

RedisConfig 保留普通 JSON 模板；库存和脚本使用 StringRedisTemplate。RedissonConfig 与 Spring Redis 必须同环境。RabbitMQConfig 管公共配置、建单拓扑及监听工厂，PaymentRabbitMQConfig 管超时和支付拓扑；避免重复 Bean。沿用 Confirm、Return、mandatory、手动 ACK，消费并发初始 3～10、预取 10，外置配置。

原四个消费者分别承担建单、死信核验、到期关单、支付通知任务入库；订单通知与 Redis 状态同步使用独立路由和具名消费者。PaymentCompensationSchedule 调度超时扫描、查单、关单和退款；StockReconciliationSchedule 调度库存、孤立预扣和重建。所有入口调用同一 Service 状态方法。新协议使用独立版本队列，不修改已有队列参数、不启用旧协议重复消费。

AlipayConfig / AlipayProperties 保留环境变量及绝对截止时间。AsyncConfig 可用于非关键有界任务，不能以进程内 CompletableFuture 代替持久通知。

## Lua 统一管理

所有 Lua 源码放在 `ticket-management/src/main/resources/lua/`，加载、注册、缓存及结果类型集中于 LuaScriptLoader；业务类和工具类不内嵌 Lua 字符串。脚本增删或重命名同步加载器、调用方、测试及说明。

| 目标脚本 | 职责 |
| --- | --- |
| reserveStock.lua | 校验就绪代次、参数、幂等、限购、库存，保存 token 并原子预扣 |
| confirmReservation.lua | 幂等同步可靠受理结果 |
| releaseReservation.lua | 按 token、代次、状态一次性释放 |
| rebuildStockBatch.lua | 未开放代次的分批重建 |
| activateStockEpoch.lua | 核验完成标记并开放代次 |
| retireStockEpochs.lua | READY新代次下按游标有界退役旧键，保护活跃占位 |
| applyStockProjection.lua | 仅更新展示投影，保留版本顺序保护 |

原 decrStock.lua 迁移并通过回归后删除；StockProjection 的内嵌脚本外置。脚本使用同票档 hash tag，中文说明 KEYS / ARGV / 返回码与约束；先校验再写入，活跃占位不依赖 TTL 消失。加载器自动关闭资源并明确返回类型，Java 转为具名结果。执行未知时按 token 查询，不假定未扣减。

## 核心状态和失败恢复

### 可靠受理

取得分段锁 → Lua 预扣并写待确认索引 → 短事务保存受理日志、购买占位、申请和 Outbox → 提交后返回申请 ID → 同步 Redis 确认，失败由事件或扫描恢复 → finally 解锁。

受理日志以用户和 requestKey 唯一，保存 token、票档、数量、代次、结果、申请 ID。同键同参数复用结果，不同参数拒绝。只有数据库提交后才算受理成功；明确回滚后幂等释放，提交未知先查询。每 5 秒扫描最多 100 条，超过 60 秒未可靠受理的先写拒绝终态，再释放 Redis；清理与迟到事务必须通过同一唯一记录互斥决策。Redis 不可用或缓存未就绪时不自动切回数据库抢票。

### 建单和释放

申请增加库存阶段 PENDING（仅 Redis 扣减）、HELD（数据库已扣减）、RELEASED；外部状态 RESERVED / ORDER_CREATED / PAID / RELEASED 保持兼容。

锁定申请 → 检查幂等、期限、售卖状态并使用受理价格快照 → stock_version 与充足库存条件更新 → 扣库存、插订单、转 HELD、通知 Outbox 同事务提交。乐观冲突最多立即尝试 3 次，每次新事务读版本，然后进入持久化重试。临时建单失败累计最多三次后释放，明确永久失败直接终止。业务或重试任务提交后 ACK。

PENDING 释放不增加数据库库存，HELD 释放先事务恢复数据库，再以可靠事件回补 Redis。所有回补校验 token、代次和终态。不得以 redelivered 或本地改写消息头计数。

### 支付和超时

保留当前回调验签、金额与流水绑定、统一入账、回调审计、租约退款与查询。截止时间从首次 Redis 预扣起固定 30 分钟。取消采用短事务标记取消中 → 事务外查/关渠道 → 短事务释放；未知时保留库存。释放后收款或第二笔付款进入补偿退款。

无消费者等待队列 → 到期死信转发 → 到期队列，结合每分钟数据库扫描。提前消息按原期限重新调度；覆盖无支付流水订单。通知先入任务再 ACK，保留邮件至少一次发送的边界。不能恢复渠道失败仍本地关闭、重开支付延期、任意改支付状态或清理资金流水。

### 对账和重建

按张数验证：Redis配额 = 数据库可售库存 - PENDING申请；总库存 = 数据库可售库存 + HELD有效申请。孤立预扣单列并收敛。展示投影与配额不同键，不能直接覆盖。并发瞬时差额先复核。

缓存代次状态 PAUSED / READY；影响配额的数据库事务取共享代次屏障，暂停取排他屏障并等待在途事务。先关入口，再按数据库恢复新代次配额、token 和有效用户占位（含历史订单）。分批写完才开放；退出保持关闭，由恢复任务续作。旧代次事件不再修改新配额。后台调整库存仍要求下架且无未完成申请，调整后同流程恢复。

## 接口、迁移与清理

- 保留公共接口及状态，返回实际 requestKey，新增当前用户按键查受理结果；兼容不传键生成，客户端重试必须复用键，失败也返回键并区分提交未知和明确拒绝。
- 从 V5 增量迁移受理日志、申请 token / 代次 / 库存阶段、缓存恢复状态与索引。历史已占库存的有效申请回填 HELD、释放记录回填 RELEASED；冲突保留并禁止新购。
- 新旧库存协议不能混跑；回退前先停受理并处理新协议申请。不删除历史 Flyway 迁移。
- TradeOperations/Impl、TradeEventConsumer、TradeRecovery、LegacyMessageAdapter、TradeMessagingConfig 的职责迁移后删除；DeadlineHandler、StockProjection 如已合并则删除。实际仍使用的支付、退款、Outbox、审计、通知、库存调整、迁移与启动校验保留。
- 清理检查 Java 引用、Spring 扫描与条件 Bean、监听和调度、手动入口、配置脚本、Docker、文档、测试，以及失效的 Mapper / DTO / 常量。不能仅凭未被直接引用删除维护入口，不能仅为保留旧类留下空壳。

## 分阶段验收

- [x] 阶段 1：固定 Git 基线，建立当前调用链、脚本与配置清单，运行单元及隔离集成测试，构建 JAR。102 项中 99 通过、0 失败、3 按条件跳过；详见重构前基线记录。
- [x] 阶段 2：Lua、锁与可靠受理实现，限购幂等、脚本参数、孤立预扣封存、提交响应丢失及回滚专项通过。
- [x] 阶段 3：异步建单与阶段化释放实现，少库存竞争、重复建单、回补幂等、持久化消费重试与失败回滚专项通过。
- [x] 阶段 4：缓存恢复与交易接入实现，PENDING重建、重建中断恢复、旧代次隔离、无流水超时及现有HTTP交易回归通过。
- [ ] 阶段 5：HTTP 售票闭环、真实沙箱与 SMTP 独立回归，清理 trade 冗余，全量测试与 JAR；验证无重复 Bean/监听/调度。
- [ ] 阶段 6：库存充足、售罄竞争、重复请求、混合交易、双实例、积压恢复，以及不同分段数/无锁对照；实测报告和文档、本地提交。

阶段2～4勾选表示本批实现与专项完成，并非全部故障矩阵验收完成。最终全量122项中119通过、3按条件跳过；双应用、更多故障组合及真实渠道回归仍由后续阶段补齐，证据和边界见[进度记录](high-concurrency-refactor-progress.md)。

每阶段先补问题复现测试再实现。功能测试使用真实隔离 MySQL/Redis/RabbitMQ，保留原测试的独立断言。性能同环境、同资源和数据量对照，阶梯加压找可持续负载，五分钟测试重复三轮；分别报告受理/建单吞吐、延迟、建单耗时、锁等待、乐观冲突、积压和恢复耗时。正常停压后最多等待 180 秒收敛，不通过则如实记录；支付、邮件替身结果不算真实渠道验收。

交付调用链与面试导读、复用/删除清单、Lua说明、迁移切换说明、接口文档、测试/JAR/实测证据和简历事实清单；不得虚构组件使用、压测成绩或外部渠道结果。
