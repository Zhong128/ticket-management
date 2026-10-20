package org.example.ticketmanagement.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.example.ticketmanagement.dto.OrderUpdateDTO;
import org.example.ticketmanagement.dto.TicketTierDTO;
import org.example.ticketmanagement.mapper.EventMapper;
import org.example.ticketmanagement.mapper.EventSessionMapper;
import org.example.ticketmanagement.mapper.OrderInfoMapper;
import org.example.ticketmanagement.mapper.TicketTierMapper;
import org.example.ticketmanagement.mapper.TradeCoreMapper;
import org.example.ticketmanagement.mapper.TradeOrderQueryMapper;
import org.example.ticketmanagement.mapper.TradeOutboxMapper;
import org.example.ticketmanagement.mapper.TradeStockMapper;
import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.pojo.TicketTier;
import org.example.ticketmanagement.serviceimpl.OrderServiceImpl;
import org.example.ticketmanagement.serviceimpl.TicketTierServiceImpl;
import org.example.ticketmanagement.trade.core.TradeOperations;
import org.example.ticketmanagement.trade.payment.TradePaymentPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real database transactions: no Redis or broker availability is needed for acceptance.
 */
class TradeReservationTest {

    JdbcTemplate db;
    Object core;
    DataSourceTransactionManager manager;
    SqlSessionTemplate sessions;

    <T> T mapper(Class<T> mapperType) {
        return sessions.getMapper(mapperType);
    }

    @BeforeEach
    void setup() throws Exception {
        var ds = new DriverManagerDataSource(
                "jdbc:h2:mem:trade" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa",
                "");
        db = new JdbcTemplate(ds);
        db.execute("CREATE TABLE ticket_tier(id BIGINT PRIMARY KEY,event_id BIGINT,session_id"
                + " BIGINT,current_price DECIMAL(10,2),available_stock INT,stock_version BIGINT DEFAULT"
                + " 0,status INT,update_time TIMESTAMP)");
        db.execute("CREATE TABLE event(id BIGINT PRIMARY KEY,status INT,sale_start_time"
                + " TIMESTAMP,sale_end_time TIMESTAMP)");
        db.execute("CREATE TABLE event_session(id BIGINT PRIMARY KEY,status INT)");
        db.execute("CREATE TABLE purchase_guard(user_id BIGINT,tier_id BIGINT,reservation_id"
                + " BIGINT,create_time TIMESTAMP,update_time TIMESTAMP,PRIMARY KEY(user_id,tier_id))");
        db.execute("CREATE TABLE stock_reservation(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT,tier_id"
                + " BIGINT,event_id BIGINT,session_id BIGINT,request_key VARCHAR(64),order_no"
                + " VARCHAR(32),status VARCHAR(32),quantity INT,unit_price DECIMAL(10,2),total_amount"
                + " DECIMAL(10,2),expires_at TIMESTAMP,create_time TIMESTAMP,update_time"
                + " TIMESTAMP,order_id BIGINT,release_reason VARCHAR(255),attempts INT DEFAULT"
                + " 0,UNIQUE(user_id,request_key),UNIQUE(order_no))");
        db.execute("CREATE TABLE order_info(id BIGINT AUTO_INCREMENT PRIMARY KEY,order_no VARCHAR(32),user_id"
                + " BIGINT,event_id BIGINT,session_id BIGINT,tier_id BIGINT,quantity INT,total_amount"
                + " DECIMAL(10,2),order_status INT,pay_time TIMESTAMP,expire_time"
                + " TIMESTAMP,active_payment_id BIGINT,cancel_reason VARCHAR(255),create_time"
                + " TIMESTAMP,update_time TIMESTAMP)");
        db.execute("CREATE TABLE payment_record(id BIGINT PRIMARY KEY,order_id BIGINT,user_id"
                + " BIGINT,total_amount DECIMAL(10,2),pay_status INT)");
        db.execute("CREATE TABLE payment_compensation(id BIGINT AUTO_INCREMENT PRIMARY KEY,payment_no"
                + " VARCHAR(64),order_id BIGINT,order_no VARCHAR(32),user_id BIGINT,task_type"
                + " VARCHAR(32),business_key VARCHAR(160),payload JSON,task_status INT,retry_count"
                + " INT,max_retry INT,next_execute_time TIMESTAMP,create_time TIMESTAMP,update_time"
                + " TIMESTAMP,UNIQUE(task_type,business_key))");
        db.execute("CREATE TABLE message_outbox(event_id VARCHAR(36) PRIMARY KEY,business_key VARCHAR(160)"
                + " UNIQUE,event_type VARCHAR(32),payload JSON,publish_status"
                + " VARCHAR(16),next_attempt_at TIMESTAMP,attempts INT,created_at TIMESTAMP,updated_at"
                + " TIMESTAMP)");
        db.update("INSERT INTO"
                + " ticket_tier(id,event_id,session_id,current_price,available_stock,stock_version,status)"
                + " VALUES(1,1,1,100,10,0,1)");
        db.update("INSERT INTO event VALUES(1,1,NULL,NULL)");
        db.update("INSERT INTO event_session VALUES(1,1)");
        sessions = MapperTestSupport.sessions(ds);
        Class<?> outboxType = Class.forName("org.example.ticketmanagement.trade.messaging.OutboxStore");
        Clock fixed = Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC);
        Object outbox = outboxType.getConstructor(TradeOutboxMapper.class, ObjectMapper.class, Clock.class)
                .newInstance(mapper(TradeOutboxMapper.class), new ObjectMapper(), fixed);
        Class<?> cls = Class.forName("org.example.ticketmanagement.trade.core.TradeOperationsImpl");
        manager = new DataSourceTransactionManager(ds);
        core = cls.getConstructor(TradeCoreMapper.class, PlatformTransactionManager.class, outboxType, Clock.class,
                boolean.class).newInstance(mapper(TradeCoreMapper.class), manager, outbox, fixed, true);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> reserve(long user, String key) throws Exception {
        return (Map<String, Object>) core.getClass()
                .getMethod("reserve", long.class, long.class, int.class, String.class).invoke(core, user, 1L, 2, key);
    }

    Object call(String name, long id) throws Exception {
        return core.getClass().getMethod(name, long.class).invoke(core, id);
    }

    @Test
    void acceptanceDeductsExactlyOnceAndRepeatedKeyReturnsSameReservation() throws Exception {
        var first = reserve(1, "one");
        var second = reserve(1, "one");
        assertEquals(first.get("requestId"), second.get("requestId"));
        assertEquals(8, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        call("createOrder", ((Number) first.get("requestId")).longValue());
        assertEquals(8, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM order_info", Integer.class));
        assertEquals(4, db.queryForObject("SELECT COUNT(*) FROM message_outbox", Integer.class));
    }

    @Test
    void finalCreationFailureReleasesOnlyOnce() throws Exception {
        long id = ((Number) reserve(1, "one").get("requestId")).longValue();
        var fail = core.getClass().getMethod("creationFailed", long.class, String.class);
        for (int i = 0; i < 5; i++) {
            fail.invoke(core, id, "permanent database error");
        }
        assertEquals(10, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        assertEquals("RELEASED", db.queryForObject("SELECT status FROM stock_reservation", String.class));
    }

    @Test
    void historicalPaidOrderPreventsAnotherReservation() throws Exception {
        db.update("INSERT INTO order_info(user_id,tier_id,order_status) VALUES(1,1,1)");
        assertThrows(InvocationTargetException.class, () -> reserve(1, "new"));
        assertEquals(10, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
    }

    @Test
    void cancelingDoesNotReleaseUntilChannelCloseAndFinishIsIdempotent() throws Exception {
        long reservation = ((Number) reserve(1, "one").get("requestId")).longValue();
        long order = ((Number) call("createOrder", reservation)).longValue();
        assertEquals(true, core.getClass().getMethod("beginCancel", long.class, Long.class).invoke(core, order, 1L));
        assertEquals(8, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        assertEquals(4, db.queryForObject("SELECT order_status FROM order_info", Integer.class));
        assertEquals(true, call("finishCancel", order));
        assertEquals(false, call("finishCancel", order));
        assertEquals(10, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
    }

    @Test
    void paidTransitionChecksPaymentOwnershipAndAmount() throws Exception {
        long reservation = ((Number) reserve(1, "one").get("requestId")).longValue();
        long order = ((Number) call("createOrder", reservation)).longValue();
        db.update("INSERT INTO payment_record VALUES(1,?,1,199,2)", order);
        var paid = core.getClass().getMethod("applyPaid", long.class, long.class);
        assertEquals(false, paid.invoke(core, order, 1L));
        db.update("UPDATE payment_record SET total_amount=200 WHERE id=1");
        assertEquals(true, paid.invoke(core, order, 1L));
        assertEquals(1, db.queryForObject("SELECT order_status FROM order_info", Integer.class));
        assertEquals(false, core.getClass().getMethod("beginCancel", long.class, Long.class).invoke(core, order, 1L));
    }

    @Test
    void outboxFailureRollsBackAcceptanceAndStock() {
        db.execute("DROP TABLE message_outbox");
        assertThrows(InvocationTargetException.class, () -> reserve(1, "one"));
        assertEquals(10, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM stock_reservation", Integer.class));
    }

    @Test
    void expiredUnorderedReservationIsReleasedInsteadOfCreated() throws Exception {
        long id = ((Number) reserve(1, "one").get("requestId")).longValue();
        db.update("UPDATE stock_reservation SET expires_at=?", LocalDateTime.parse("2026-10-18T23:59:00"));
        assertNull(call("createOrder", id));
        assertEquals(10, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM order_info", Integer.class));
    }

    @Test
    void reservationAndCancellationRejectAnotherUser() throws Exception {
        long id = ((Number) reserve(1, "one").get("requestId")).longValue();
        assertThrows(InvocationTargetException.class,
                () -> core.getClass().getMethod("request", long.class, long.class).invoke(core, id, 2L));
        long order = ((Number) call("createOrder", id)).longValue();
        assertThrows(InvocationTargetException.class,
                () -> core.getClass().getMethod("beginCancel", long.class, Long.class).invoke(core, order, 2L));
        assertEquals(0, db.queryForObject("SELECT order_status FROM order_info", Integer.class));
    }

    @Test
    void legalChannelPaymentCanArriveAfterLocalDeadlineWhileStockStillHeld() throws Exception {
        long id = ((Number) reserve(1, "one").get("requestId")).longValue();
        long order = ((Number) call("createOrder", id)).longValue();
        db.update("UPDATE order_info SET expire_time=?", LocalDateTime.parse("2026-10-18T23:59:00"));
        db.update("INSERT INTO payment_record VALUES(1,?,1,200,2)", order);
        assertEquals(true, core.getClass().getMethod("applyPaid", long.class, long.class).invoke(core, order, 1L));
        assertEquals(8, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
    }

    @Test
    void successfulPaymentJoinsCallerTransactionAndRollsBackTogether() throws Exception {
        long id = ((Number) reserve(1, "one").get("requestId")).longValue();
        long order = ((Number) call("createOrder", id)).longValue();
        db.update("INSERT INTO payment_record VALUES(1,?,1,200,2)", order);
        var outer = new TransactionTemplate(manager);
        outer.executeWithoutResult(s -> {
            try {
                assertEquals(true,
                        core.getClass().getMethod("applyPaid", long.class, long.class).invoke(core, order, 1L));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            s.setRollbackOnly();
        });
        assertEquals(0, db.queryForObject("SELECT order_status FROM order_info", Integer.class));
        assertEquals("ORDER_CREATED", db.queryForObject("SELECT status FROM stock_reservation", String.class));
    }

    @Test
    void concurrentBuyersCannotOversellMysqlStock() throws Exception {
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (long user = 1; user <= 20; user++) {
                final long buyer = user;
                results.add(pool.submit(() -> {
                    try {
                        reserve(buyer, "buy");
                        return true;
                    } catch (InvocationTargetException expected) {
                        return false;
                    }
                }));
            }
            int success = 0;
            for (var result : results) {
                if (result.get()) {
                    success++;
                }
            }
            assertEquals(5, success);
        }
        assertEquals(0, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        assertEquals(5, db.queryForObject("SELECT COUNT(*) FROM stock_reservation", Integer.class));
    }

    @SuppressWarnings("unchecked")
    OrderServiceImpl orderService(TradePaymentPort port) {
        var provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(port);
        var service = new OrderServiceImpl(Mockito.mock(OrderInfoMapper.class), Mockito.mock(TicketTierMapper.class),
                Mockito.mock(EventMapper.class), Mockito.mock(EventSessionMapper.class), Mockito.mock(UserMapper.class),
                (TradeOperations) core, provider, mapper(TradeOrderQueryMapper.class),
                Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "tradeEnabled", true);
        return service;
    }

    @Test
    void unknownChannelCloseKeepsStockAndCallsNetworkWithoutTransaction() throws Exception {
        long id = ((Number) reserve(1, "one").get("requestId")).longValue();
        long order = ((Number) call("createOrder", id)).longValue();
        var service = orderService(orderId -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return TradePaymentPort.CloseDecision.UNKNOWN;
        });
        assertFalse(service.userCancelOrder(order, 1L));
        assertEquals(4, db.queryForObject("SELECT order_status FROM order_info", Integer.class));
        assertEquals(8, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM payment_compensation WHERE task_type='ORDER_CLOSE'",
                Integer.class));
    }

    @Test
    void adminCannotRewriteQuantityAmountOrMarkAnOrderPaid() {
        var service = orderService(orderId -> TradePaymentPort.CloseDecision.CLOSED);
        var update = new OrderUpdateDTO();
        update.setTotalAmount(new BigDecimal("1"));
        assertThrows(IllegalArgumentException.class, () -> service.adminUpdateOrder(1L, update, 2L));
        update.setTotalAmount(null);
        update.setQuantity(100);
        assertThrows(IllegalArgumentException.class, () -> service.adminUpdateOrder(1L, update, 2L));
        update.setQuantity(null);
        update.setOrderStatus(1);
        assertThrows(IllegalArgumentException.class, () -> service.adminUpdateOrder(1L, update, 2L));
    }

    @Test
    void metadataMapperNeverOverwritesStockReadBeforeAReservation() throws Exception {
        db.execute("ALTER TABLE ticket_tier ADD total_stock INT DEFAULT 10");
        db.execute("ALTER TABLE ticket_tier ADD tier_name VARCHAR(64)");
        db.execute("ALTER TABLE ticket_tier ADD original_price DECIMAL(10,2)");
        db.execute("ALTER TABLE ticket_tier ADD create_time TIMESTAMP");
        var stale = new TicketTier();
        stale.setId(1L);
        stale.setEventId(1L);
        stale.setSessionId(1L);
        stale.setTierName("rename");
        stale.setCurrentPrice(new BigDecimal("100"));
        stale.setOriginalPrice(new BigDecimal("100"));
        stale.setStatus(1);
        stale.setTotalStock(10);
        stale.setAvailableStock(10);
        stale.setUpdateTime(LocalDateTime.now());
        reserve(1, "buy");
        var environment = new Environment("test", new JdbcTransactionFactory(), db.getDataSource());
        var config = new Configuration(environment);
        config.addMapper(TicketTierMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(config).openSession(true)) {
            assertEquals(1, session.getMapper(TicketTierMapper.class).update(stale));
        }
        assertEquals(8, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
    }

    Object adjustment() throws Exception {
        var outbox = ReflectionTestUtils.getField(core, "outbox");
        return Class.forName("org.example.ticketmanagement.trade.core.StockAdjustmentService")
                .getConstructor(TradeStockMapper.class, PlatformTransactionManager.class,
                        Class.forName("org.example.ticketmanagement.trade.messaging.OutboxStore"), Clock.class,
                        boolean.class)
                .newInstance(mapper(TradeStockMapper.class), manager, outbox,
                        Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC), true);
    }

    @Test
    void adjustmentRequiresOffSaleAndNoUnsettledReservation() throws Exception {
        db.execute("ALTER TABLE ticket_tier ADD total_stock INT DEFAULT 10");
        var adjust = adjustment();
        var method = adjust.getClass().getMethod("adjust", long.class, int.class);
        assertThrows(InvocationTargetException.class, () -> method.invoke(adjust, 1L, 2));
        reserve(1, "buy");
        db.update("UPDATE ticket_tier SET status=0");
        assertThrows(InvocationTargetException.class, () -> method.invoke(adjust, 1L, 2));
        assertEquals(8, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
    }

    @Test
    void adjustmentChangesCapacityAndAvailableBySameDeltaWithOutboxVersion() throws Exception {
        db.execute("ALTER TABLE ticket_tier ADD total_stock INT DEFAULT 10");
        db.update("UPDATE ticket_tier SET status=0,available_stock=8");
        var adjust = adjustment();
        var method = adjust.getClass().getMethod("adjust", long.class, int.class);
        method.invoke(adjust, 1L, 3);
        assertEquals(13, db.queryForObject("SELECT total_stock FROM ticket_tier", Integer.class));
        assertEquals(11, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        assertEquals(1L, db.queryForObject("SELECT stock_version FROM ticket_tier", Long.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM message_outbox WHERE event_type='STOCK_CHANGED'",
                Integer.class));
        assertThrows(InvocationTargetException.class, () -> method.invoke(adjust, 1L, -12));
        assertEquals(11, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
    }

    SqlSession tierSession() {
        var environment = new Environment("test", new JdbcTransactionFactory(), db.getDataSource());
        var config = new Configuration(environment);
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(TicketTierMapper.class);
        config.addMapper(EventMapper.class);
        config.addMapper(EventSessionMapper.class);
        return new SqlSessionFactoryBuilder().build(config).openSession(true);
    }

    @Test
    void softDeletePreservesTierAndAllowsOutstandingStockRelease() throws Exception {
        long reservation = ((Number) reserve(1, "buy").get("requestId")).longValue();
        try (var session = tierSession()) {
            assertEquals(1, session.getMapper(TicketTierMapper.class).deleteById(1L));
        }
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM ticket_tier", Integer.class));
        assertEquals(0, db.queryForObject("SELECT status FROM ticket_tier", Integer.class));
        assertEquals(8, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        ((TradeOperations) core).releaseUnordered(reservation, "EXPIRED");
        assertEquals(10, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
    }

    @Test
    void metadataCannotMoveTierIdentityWhileReservationIsActive() throws Exception {
        reserve(1, "buy");
        try (var session = tierSession()) {
            var service = new TicketTierServiceImpl();
            ReflectionTestUtils.setField(service, "ticketTierMapper", session.getMapper(TicketTierMapper.class));
            ReflectionTestUtils.setField(service, "stockMapper", mapper(TradeStockMapper.class));
            ReflectionTestUtils.setField(service, "tradeEnabled", true);
            var update = new TicketTierDTO();
            update.setEventId(2L);
            assertThrows(IllegalStateException.class, () -> service.updateTicketTier(1L, update));
            update.setEventId(null);
            update.setSessionId(2L);
            assertThrows(IllegalStateException.class, () -> service.updateTicketTier(1L, update));
        }
        assertEquals(1L, db.queryForObject("SELECT event_id FROM ticket_tier", Long.class));
        assertEquals(1L, db.queryForObject("SELECT session_id FROM ticket_tier", Long.class));
    }

    @Test
    void metadataRejectsExplicitStockFields() {
        var service = new TicketTierServiceImpl();
        var update = new TicketTierDTO();
        update.setAvailableStock(100);
        assertThrows(IllegalArgumentException.class, () -> service.updateTicketTier(1L, update));
        update.setAvailableStock(null);
        update.setTotalStock(100);
        assertThrows(IllegalArgumentException.class, () -> service.updateTicketTier(1L, update));
    }

    void enableParentCascades() {
        db.execute("ALTER TABLE event ADD update_time TIMESTAMP");
        db.execute("ALTER TABLE event_session ADD event_id BIGINT DEFAULT 1");
        db.execute("ALTER TABLE event_session ADD CONSTRAINT session_parent FOREIGN KEY(event_id) REFERENCES"
                + " event(id) ON DELETE CASCADE");
        db.execute("ALTER TABLE ticket_tier ADD CONSTRAINT tier_event_parent FOREIGN KEY(event_id) REFERENCES"
                + " event(id) ON DELETE CASCADE");
        db.execute("ALTER TABLE ticket_tier ADD CONSTRAINT tier_session_parent FOREIGN KEY(session_id)"
                + " REFERENCES event_session(id) ON DELETE CASCADE");
    }

    @Test
    void softDeletingEventPreservesCascadeChildrenAndOrderCancellationCanRelease() throws Exception {
        enableParentCascades();
        long reservation = ((Number) reserve(1, "buy").get("requestId")).longValue();
        long order = ((Number) call("createOrder", reservation)).longValue();
        try (var session = tierSession()) {
            assertEquals(1, session.getMapper(EventMapper.class).deleteById(1L));
        }
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM event", Integer.class));
        assertEquals(0, db.queryForObject("SELECT status FROM event", Integer.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM event_session", Integer.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM ticket_tier", Integer.class));
        var orders = orderService(id -> TradePaymentPort.CloseDecision.CLOSED);
        assertTrue(orders.userCancelOrder(order, 1L));
        assertEquals(10, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        assertEquals(2, db.queryForObject("SELECT order_status FROM order_info", Integer.class));
        assertThrows(InvocationTargetException.class, () -> reserve(2, "off-sale"));
    }

    @Test
    void softDeletingSessionPreservesTierAndOrderCancellationCanRelease() throws Exception {
        enableParentCascades();
        long reservation = ((Number) reserve(1, "buy").get("requestId")).longValue();
        long order = ((Number) call("createOrder", reservation)).longValue();
        try (var session = tierSession()) {
            assertEquals(1, session.getMapper(EventSessionMapper.class).deleteById(1L));
        }
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM event_session", Integer.class));
        assertEquals(0, db.queryForObject("SELECT status FROM event_session", Integer.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM ticket_tier", Integer.class));
        var orders = orderService(id -> TradePaymentPort.CloseDecision.CLOSED);
        assertTrue(orders.userCancelOrder(order, 1L));
        assertEquals(10, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        assertEquals(2, db.queryForObject("SELECT order_status FROM order_info", Integer.class));
        assertThrows(InvocationTargetException.class, () -> reserve(2, "off-sale"));
    }
}
