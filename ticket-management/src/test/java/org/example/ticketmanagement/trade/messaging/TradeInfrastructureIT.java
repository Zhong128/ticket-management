package org.example.ticketmanagement.trade.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ticketmanagement.config.RabbitMQConfig;
import org.example.ticketmanagement.constant.SeckillConstant;
import org.example.ticketmanagement.dto.CreatePaymentDTO;
import org.example.ticketmanagement.mapper.TradeCoreMapper;
import org.example.ticketmanagement.mapper.TradeOutboxMapper;
import org.example.ticketmanagement.mapper.TradePaymentMapper;
import org.example.ticketmanagement.mapper.TradeRefundMapper;
import org.example.ticketmanagement.trade.MapperTestSupport;
import org.example.ticketmanagement.trade.core.TradeOperationsImpl;
import org.example.ticketmanagement.trade.payment.PaymentCoordinator;
import org.example.ticketmanagement.trade.payment.PaymentGateway;
import org.example.ticketmanagement.trade.payment.PaymentRepository;
import org.example.ticketmanagement.trade.payment.RefundService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.amqp.core.*;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Explicitly run with -Dtest=TradeInfrastructureIT. All resources are disposable containers,
 * without host mounts.
 */
@Testcontainers
class TradeInfrastructureIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("bootstrap").withUsername("trade_it").withPassword("isolated-test-only");

    @Container
    static final RabbitMQContainer BROKER = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.12-management-alpine"));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static CachingConnectionFactory rabbitConnection;
    private static RabbitTemplate rabbit;
    private static LettuceConnectionFactory redisConnection;
    private static StringRedisTemplate redis;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper mapper = new ObjectMapper();
    private JdbcTemplate db;
    private TradeOperationsImpl core;
    private TradeOutboxMapper outboxMapper;
    private SqlSessionTemplate sessions;
    private OutboxStore outbox;
    private TransactionTemplate transactions;

    @BeforeAll
    static void connectIsolatedInfrastructure() {
        rabbitConnection = new CachingConnectionFactory(BROKER.getHost(), BROKER.getAmqpPort());
        rabbitConnection.setUsername(BROKER.getAdminUsername());
        rabbitConnection.setPassword(BROKER.getAdminPassword());
        // Exercise the actual production template configuration, including mandatory and correlated
        // confirms.
        rabbit = new RabbitMQConfig().rabbitTemplate(rabbitConnection);
        RabbitAdmin admin = new RabbitAdmin(rabbitConnection);
        Declarables topology = new TradeMessagingConfig().tradeTopology();
        for (Exchange exchange : topology.getDeclarablesByType(Exchange.class)) {
            admin.declareExchange(exchange);
        }
        for (Queue queue : topology.getDeclarablesByType(Queue.class)) {
            admin.declareQueue(queue);
        }
        for (Binding binding : topology.getDeclarablesByType(Binding.class)) {
            admin.declareBinding(binding);
        }
        redisConnection = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        redisConnection.afterPropertiesSet();
        redisConnection.start();
        redis = new StringRedisTemplate(redisConnection);
    }

    @AfterAll
    static void disconnect() {
        if (redisConnection != null) {
            redisConnection.destroy();
        }
        if (rabbitConnection != null) {
            rabbitConnection.destroy();
        }
    }

    @BeforeEach
    void migrateFreshDatabaseAndSeedSale() {
        // MYSQL is a freshly created Testcontainers server. Never connect to workspace
        // configuration or
        // original volumes.
        String schema = "trade_it_" + UUID.randomUUID().toString().replace("-", "");
        var adminDataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
        new JdbcTemplate(adminDataSource).execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + schema
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        var dataSource = new DriverManagerDataSource(url, "root", MYSQL.getPassword());
        var result = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        assertTrue(result.migrationsExecuted >= 3,
                "Fresh database must run legacy, durable trade, and backfill migrations");
        db = new JdbcTemplate(dataSource);
        var manager = new DataSourceTransactionManager(dataSource);
        transactions = new TransactionTemplate(manager);
        sessions = MapperTestSupport.sessions(dataSource);
        outboxMapper = sessions.getMapper(TradeOutboxMapper.class);
        outbox = new OutboxStore(outboxMapper, mapper, clock);
        core = new TradeOperationsImpl(sessions.getMapper(TradeCoreMapper.class), manager, outbox, clock, true);
        LocalDateTime now = LocalDateTime.now(clock);
        db.update("INSERT INTO city(id,name,code,province) VALUES(1,'Integration city','IT','Integration"
                + " province')");
        db.update("INSERT INTO event_category(id,name) VALUES(1,'Integration event')");
        db.update("INSERT INTO"
                + " event(id,name,category_id,city_id,venue,status,sale_start_time,sale_end_time,event_start_time,event_end_time)"
                + " VALUES(1,'Isolated sale',1,1,'Test venue',1,?,?,?,?)", now.minusDays(1), now.plusDays(1),
                now.plusDays(2), now.plusDays(3));
        db.update("INSERT INTO event_session(id,event_id,session_name,session_time,status) VALUES(1,1,'Test"
                + " session',?,1)", now.plusDays(2));
        db.update("INSERT INTO"
                + " ticket_tier(id,event_id,session_id,tier_name,original_price,current_price,total_stock,available_stock,status)"
                + " VALUES(1,1,1,'Test tier',100,100,10,10,1)");
    }

    private long reserve(long userId, String key) {
        return ((Number) core.reserve(userId, 1, 2, key).get("requestId")).longValue();
    }

    @Test
    void mysqlPaymentAndRefundMappersPreserveGeneratedIdsAndLeasedCompletion() {
        var gateway = Mockito.mock(PaymentGateway.class);
        Mockito.when(gateway.create(ArgumentMatchers.any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return "isolated-payment-form";
        });
        var refunds = new RefundService(sessions.getMapper(TradeRefundMapper.class), gateway, transactions, clock);
        var payments = new PaymentCoordinator(new PaymentRepository(sessions.getMapper(TradePaymentMapper.class)),
                gateway, core, outbox, refunds, transactions, clock);
        ReflectionTestUtils.setField(refunds, "enabled", true);
        ReflectionTestUtils.setField(payments, "enabled", true);
        long order = core.createOrder(reserve(1, "payment-mappers"));
        var intent = payments.create(1, new CreatePaymentDTO(order, 1)).payment();
        assertNotNull(intent.getId());
        assertTrue(intent.getId() > 0);
        assertTrue(core.beginCancel(order, 1L));
        assertTrue(core.finishCancel(order));
        assertFalse(payments.settle(intent.getPaymentNo(), "MYSQL-TRADE", intent.getTotalAmount()));
        String refundNo = db.queryForObject("SELECT refund_no FROM refund_record", String.class);
        var missing = new PaymentGateway.RefundResult(PaymentGateway.RefundState.NOT_FOUND, null, null);
        var success = new PaymentGateway.RefundResult(PaymentGateway.RefundState.SUCCESS, "MYSQL-TRADE", null);
        Mockito.when(gateway.queryRefund(intent.getPaymentNo(), refundNo, intent.getTotalAmount())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return missing;
        });
        Mockito.when(gateway.refund(intent.getPaymentNo(), refundNo, intent.getTotalAmount())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return success;
        });
        assertEquals(1, refunds.processDueRefunds());
        assertEquals(0, refunds.processDueRefunds());
        assertFalse(payments.settle(intent.getPaymentNo(), "MYSQL-TRADE", intent.getTotalAmount()));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM refund_record", Integer.class));
        assertEquals("SUCCESS", db.queryForObject("SELECT status FROM refund_record", String.class));
        assertEquals(5, db.queryForObject("SELECT pay_status FROM payment_record", Integer.class));
        assertEquals(10, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        assertEquals(2, db.queryForObject("SELECT order_status FROM order_info", Integer.class));
        Mockito.verify(gateway, Mockito.times(1)).refund(intent.getPaymentNo(), refundNo, intent.getTotalAmount());
    }

    @Test
    void mysqlTransactionsPreventOversellingWithConcurrentBuyers() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(12)) {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (long user = 1; user <= 24; user++) {
                long buyer = user;
                attempts.add(pool.submit(() -> {
                    start.await();
                    try {
                        reserve(buyer, "concurrent");
                        return true;
                    } catch (IllegalStateException soldOut) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int accepted = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(30, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertEquals(5, accepted);
        }
        assertEquals(0, db.queryForObject("SELECT available_stock FROM ticket_tier WHERE id=1", Integer.class));
        assertEquals(5, db.queryForObject("SELECT COUNT(*) FROM stock_reservation", Integer.class));
        assertEquals(15, db.queryForObject("SELECT COUNT(*) FROM message_outbox", Integer.class));
    }

    @Test
    void mysqlIdempotencyAndCancellationReleaseStockExactlyOnce() {
        long reservation = reserve(1, "same-request");
        assertEquals(reservation, reserve(1, "same-request"));
        long order = core.createOrder(reservation);
        assertEquals(order, core.createOrder(reservation));
        assertEquals(8, db.queryForObject("SELECT available_stock FROM ticket_tier WHERE id=1", Integer.class));
        assertTrue(core.beginCancel(order, 1L));
        assertEquals(8, db.queryForObject("SELECT available_stock FROM ticket_tier WHERE id=1", Integer.class));
        assertTrue(core.finishCancel(order));
        assertFalse(core.finishCancel(order));
        assertEquals(10, db.queryForObject("SELECT available_stock FROM ticket_tier WHERE id=1", Integer.class));
        assertEquals("RELEASED",
                db.queryForObject("SELECT status FROM stock_reservation WHERE id=?", String.class, reservation));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM payment_compensation WHERE task_type='ORDER_CLOSE'",
                Integer.class));
    }

    @Test
    void realBrokerAckPublishesOutboxWithStableEventHeaders() throws Exception {
        transactions.executeWithoutResult(
                status -> outbox.enqueue("CREATE_ORDER", "it-confirm", Map.of("reservationId", 42)));
        String eventId = db.queryForObject("SELECT event_id FROM message_outbox", String.class);
        new OutboxPublisher(outboxMapper, mapper, rabbit, clock).publishDue();
        assertEquals("PUBLISHED", db.queryForObject("SELECT publish_status FROM message_outbox", String.class));
        Message delivered = receiveMatching("trade.work.v2", eventId, Duration.ofSeconds(5));
        assertNotNull(delivered);
        assertEquals("CREATE_ORDER", delivered.getMessageProperties().getHeader("eventType"));
        var envelope = mapper.readTree(delivered.getBody());
        assertEquals("CREATE_ORDER", envelope.path("eventType").asText());
        assertEquals(42, envelope.path("payload").path("reservationId").asInt());
        assertEquals(MessageDeliveryMode.PERSISTENT, delivered.getMessageProperties().getReceivedDeliveryMode());
    }

    @Test
    void realMandatoryReturnPersistsRetryInsteadOfMarkingPublished() {
        transactions
                .executeWithoutResult(status -> outbox.enqueue("UNROUTABLE_IT", "it-return", Map.of("probe", true)));
        new OutboxPublisher(outboxMapper, mapper, rabbit, clock).publishDue();
        assertEquals("NEW", db.queryForObject("SELECT publish_status FROM message_outbox", String.class));
        assertEquals(1, db.queryForObject("SELECT attempts FROM message_outbox", Integer.class));
        assertNotNull(db.queryForObject("SELECT last_error FROM message_outbox", String.class));
        assertTrue(db.queryForObject("SELECT next_attempt_at FROM message_outbox", LocalDateTime.class)
                .isAfter(LocalDateTime.now(clock)));
        assertNull(db.queryForObject("SELECT lease_token FROM message_outbox", String.class));
    }

    @Test
    void deadlineRemainsInWaitQueueUntilTtlThenArrivesThroughDeadLetterRouting() throws Exception {
        long reservation = reserve(1, "ttl");
        // Use three real seconds rather than waiting thirty minutes. The publisher derives TTL from
        // the
        // DB absolute deadline.
        db.update("UPDATE stock_reservation SET expires_at=? WHERE id=?", LocalDateTime.now(clock).plusSeconds(3),
                reservation);
        String id = db.queryForObject("SELECT event_id FROM message_outbox WHERE event_type='ORDER_DEADLINE'",
                String.class);
        new OutboxPublisher(outboxMapper, mapper, rabbit, clock).publishDue();
        assertNull(rabbit.receive("trade.deadline.ready.v2"), "There must be no immediate deadline delivery");
        Message expired = receiveMatching("trade.deadline.ready.v2", id, Duration.ofSeconds(10));
        assertNotNull(expired, "The unconsumed TTL queue must dead-letter to the processing queue");
        assertEquals("ORDER_DEADLINE", mapper.readTree(expired.getBody()).path("eventType").asText());
        assertNotNull(expired.getMessageProperties().getHeader("x-death"));
        assertEquals("PUBLISHED",
                db.queryForObject("SELECT publish_status FROM message_outbox WHERE event_id=?", String.class, id));
    }

    @Test
    void redisProjectionRejectsOutOfOrderAndLargeVersionsWithoutPrecisionLoss() {
        long tier = 900000 + Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100000);
        String key = SeckillConstant.STOCK_KEY_PREFIX + tier;
        StockProjection projection = new StockProjection(redis);
        projection.apply(tier, 2, 8);
        projection.apply(tier, 1, 9);
        assertEquals("8", redis.opsForValue().get(key));
        assertEquals("2", redis.opsForValue().get(key + ":version"));
        projection.apply(tier, 9007199254740993L, 3);
        projection.apply(tier, 9007199254740992L, 4);
        assertEquals("3", redis.opsForValue().get(key));
        assertEquals("9007199254740993", redis.opsForValue().get(key + ":version"));
        // Reconciliation may repair missing stock at the same authoritative version.
        redis.delete(key);
        projection.apply(tier, 9007199254740993L, 3);
        assertEquals("3", redis.opsForValue().get(key));
    }

    private Message receiveMatching(String queue, String eventId, Duration timeout) {
        long end = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < end) {
            Message message = rabbit.receive(queue, 200);
            if (message != null && eventId.equals(message.getMessageProperties().getHeader("eventId"))) {
                return message;
            }
        }
        return null;
    }
}
