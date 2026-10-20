# 交易整改执行约定

基线：8e52a22；分支：codex/transaction-hardening。依据：用户已确认的完整整改计划。保留历史数据、同票档有效订单限一单、每单最多5张、MySQL可靠预占、30分钟绝对截止时间、晚到付款/重复收款全额补偿退款。禁止访问真实资金、重建现有数据库或推送。

## 共享接口（实现者不得自行改变）

包名前缀 `org.example.ticketmanagement.trade`。

`core.TradeOperations` 接口由库存订单实现者创建：

```java
Map<String,Object> reserve(long userId, long tierId, int quantity, String requestKey);
Map<String,Object> request(long reservationId, long userId);
Long createOrder(long reservationId);
boolean beginCancel(long orderId, Long userId); // null only for trusted internal calls; marks CANCELING(4)
boolean finishCancel(long orderId); // only CANCELING -> CANCELED; transactional, one release
boolean applyPaid(long orderId, long paymentId); // returns false for canceled or a different successful payment
void releaseUnordered(long reservationId, String reason);
void creationFailed(long reservationId, String error);
```

返回申请 Map 使用 camelCase：requestId, requestStatus, orderId, orderNo, expireTime, tierId, quantity, price, totalAmount。状态为 RESERVED, ORDER_CREATED, PAID, RELEASED。订单状态旧编号0/1/2/3不变，4=CANCELING；新增 order_info.expire_time, active_payment_id, cancel_reason。applyPaid 必须校验该支付属于该订单，并且额度匹配；参与调用者当前事务。

`payment.TradePaymentPort` 由支付实现者创建：

```java
enum CloseDecision { CLOSED, PAID, UNKNOWN }
CloseDecision closeForOrder(long orderId);
```

OrderServiceImpl 取消流程（无外层长事务）：beginCancel -> payment port -> CLOSED:finishCancel；PAID:不回库；UNKNOWN:保持CANCELING并保留恢复任务。用 ObjectProvider<TradePaymentPort> 延迟引用防止构造循环依赖。支付实现通过 TradeOperations.applyPaid 应用渠道确认付款。

`messaging.OutboxStore` 由消息实现者创建：

```java
void enqueue(String eventType, String businessKey, Object payload);
void enqueueAt(String eventType, String businessKey, Object payload, LocalDateTime availableAt);
```

OutboxStore 使用 TradeOutboxMapper（MyBatis）参与业务事务；Jackson序列化；business_key 唯一保证重复插入幂等。不单独提交，不吞掉写库异常。

事件约定：CREATE_ORDER / ORDER_DEADLINE / ORDER_CREATED: {reservationId:long}；STOCK_CHANGED:{tierId:long,version:long,stock:int}；PAYMENT_SUCCESS:{paymentNo:string}。事件消息头 eventId，JSON载荷含 eventType,payload；新交易统一交换机 trade.events.v2；延迟关单通过无消费者TTL等待队列死信转发。原消费者不再回补Redis或绕过统一状态入口。

## SQL 约定

`active_payment_id` 只记录已经成功并被订单接受的付款；创建待支付流水时不得设置。截止后才收到的成功回执，只要库存仍被该订单持有，仍可确认付款；只有订单已取消释放或另一笔付款已经成功时进入补偿退款。

现有表及列保持；ticket_tier 新增 stock_version BIGINT NOT NULL DEFAULT 0；所有库存增减同步递增版本。order_info 新增 expire_time DATETIME(6) NULL、active_payment_id BIGINT NULL、cancel_reason VARCHAR(255) NULL、remark VARCHAR(500) NULL。不能对历史流水执行DELETE。

stock_reservation：id BIGINT AUTO_INCREMENT PK；user_id,tier_id,event_id,session_id BIGINT NOT NULL；request_key VARCHAR(64)；order_no VARCHAR(32)；status VARCHAR(32)；quantity INT；unit_price,total_amount DECIMAL(10,2)；expires_at,create_time,update_time DATETIME(6)；order_id BIGINT NULL；release_reason VARCHAR(255) NULL；attempts INT DEFAULT 0；UNIQUE(user_id,request_key)，UNIQUE(order_no)。字段除明确NULL外必填。

purchase_guard：user_id,tier_id BIGINT 复合主键；reservation_id BIGINT NULL；create_time,update_time DATETIME(6) NOT NULL。先INSERT IGNORE占位，再SELECT FOR UPDATE。历史重复有效订单标记冲突并阻止对应新交易，不删除。

message_outbox：event_id VARCHAR(36) PK；business_key VARCHAR(160) UNIQUE；event_type VARCHAR(32)；payload JSON；publish_status VARCHAR(16) DEFAULT 'NEW'；next_attempt_at DATETIME(6)；attempts INT DEFAULT 0；lease_token VARCHAR(36) NULL；lease_until DATETIME(6) NULL；last_error VARCHAR(500) NULL；created_at,updated_at DATETIME(6)。状态 NEW,IN_FLIGHT,PUBLISHED；创建时间/下次执行时间由SQL或代码填入。

refund_record：id BIGINT AUTO_INCREMENT PK；refund_no VARCHAR(64) UNIQUE；payment_id BIGINT UNIQUE；payment_no VARCHAR(64)；order_id,user_id BIGINT；amount DECIMAL(10,2)；reason VARCHAR(64)；status VARCHAR(32)；attempts INT DEFAULT 0；next_attempt_at DATETIME(6)；lease_token VARCHAR(36) NULL；lease_until DATETIME(6) NULL；last_error VARCHAR(500) NULL；trade_no VARCHAR(64) NULL；response_data TEXT NULL；created_at,updated_at DATETIME(6)。状态 REQUESTED,PROCESSING,UNKNOWN,SUCCESS,RETRYABLE_FAILED,MANUAL_REQUIRED。

payment_compensation 保留历史结构，放宽 payment_no/order_id 为NULL，新增 task_type VARCHAR(32)、business_key VARCHAR(160)、payload JSON、lease_token VARCHAR(36) NULL、lease_until DATETIME(6) NULL；UNIQUE(task_type,business_key)。任务状态0待处理/1执行中/2完成/3人工处理。原字段 create_time/update_time、next_execute_time、retry_count/max_retry 保留。业务层可用独立Repository访问，禁止把缺少支付记录当作跳过关单理由。

## 执行与验证

- 主协调者拥有POM、迁移SQL、测试环境、集成验收、文档和跨模块集成修复。
- 库存订单实现者拥有trade/core、SeckillServiceImpl、OrderServiceImpl、SeckillController、订单DTO/VO/Mapper必要修改、核心测试。
- 支付实现者拥有trade/payment、PaymentServiceImpl、PaymentController、支付Service/DTO/VO/Mapper/常量、AlipaySignatureUtil、退款Controller、支付测试；不修改核心/消息模块。
- 消息实现者拥有trade/messaging、MQ配置、四个旧消费者、StockServiceImpl、StockInitializer、ReconciliationServiceImpl、定时任务和消息测试；不修改支付/核心模块。
- 不修改凭据文件，不输出凭据；不用git reset/clean，不自动提交或推送。
- 各自先写失败测试、实际运行后再实现。构建目录共享，运行Maven前与主协调者协调；主协调者统一执行全量测试。
- 业务持久层统一使用MyBatis Mapper，SQL放具名Mapper方法，Service保留事务与业务规则。不得在Service使用JdbcTemplate/原生JDBC另起数据访问层。测试准备DDL及独立断言可用JdbcTemplate。
- 时钟使用注入 java.time.Clock，默认系统时钟Bean由主协调者提供；固定时钟可用于期限和重试测试。
- 当前Docker未启动，主协调者已后台启动Docker Desktop，将使用隔离容器，不启动挂载原数据卷的Compose。
