package org.example.ticketmanagement.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.example.ticketmanagement.dto.OrderUpdateDTO;
import org.example.ticketmanagement.dto.TicketTierDTO;
import org.example.ticketmanagement.exception.ReservationRejectedException;
import org.example.ticketmanagement.mapper.EventMapper;
import org.example.ticketmanagement.mapper.EventSessionMapper;
import org.example.ticketmanagement.mapper.OrderInfoMapper;
import org.example.ticketmanagement.mapper.TicketTierMapper;
import org.example.ticketmanagement.mapper.TradeCoreMapper;
import org.example.ticketmanagement.mapper.TradeOrderQueryMapper;
import org.example.ticketmanagement.mapper.TradeOutboxMapper;
import org.example.ticketmanagement.mapper.TradeStockMapper;
import org.example.ticketmanagement.mapper.SeckillAdmissionMapper;
import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.pojo.TicketTier;
import org.example.ticketmanagement.serviceimpl.OrderServiceImpl;
import org.example.ticketmanagement.serviceimpl.TicketTierServiceImpl;
import org.example.ticketmanagement.serviceimpl.SeckillServiceImpl;
import org.example.ticketmanagement.serviceimpl.ReservationServiceImpl;
import org.example.ticketmanagement.serviceimpl.StockServiceImpl;
import org.example.ticketmanagement.service.ReservationService;
import org.example.ticketmanagement.trade.payment.TradePaymentPort;
import org.example.ticketmanagement.trade.messaging.OutboxStore;
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
 * 真实隔离MySQL与Redis验证新协议，保留原支付、权限和库存维护回归。
 */
class TradeReservationTest {

    JdbcTemplate db;
    Object core;
    DataSourceTransactionManager manager;
    SqlSessionTemplate sessions;
    SeckillServiceImpl confirmed;
    StockServiceImpl stockService;

    <T> T mapper(Class<T> mapperType) {
        return sessions.getMapper(mapperType);
    }

    @BeforeEach
    void setup() throws Exception {
        var ds = IsolatedProtocolDatabase.freshDatabase();
        db = new JdbcTemplate(ds);
        sessions = MapperTestSupport.sessions(ds);
        manager = new DataSourceTransactionManager(ds);
        Clock fixed = Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC);
        var outbox = new OutboxStore(
                mapper(TradeOutboxMapper.class), new ObjectMapper(), fixed);
        var fixture = ReservationFixture.create(sessions, new TransactionTemplate(manager), outbox,
                IsolatedProtocolDatabase.redis, fixed);
        core = fixture.core();
        stockService = fixture.stock();
        confirmed = new SeckillServiceImpl(fixture.core(), fixture.stock(), null,
                mapper(TicketTierMapper.class), mapper(EventSessionMapper.class), mapper(EventMapper.class), fixed);
        ReflectionTestUtils.setField(confirmed, "enabled", true);
        ReflectionTestUtils.setField(confirmed, "lockingEnabled", false);
        db.update("INSERT INTO city(id,name,code,province) VALUES(1,'Test city','IT','Test')");
        db.update("INSERT INTO event_category(id,name) VALUES(1,'Test category')");
        db.update("INSERT INTO event(id,name,category_id,city_id,venue,status,sale_start_time,sale_end_time,event_start_time,event_end_time) VALUES(1,'Test event',1,1,'Test',1,?,?,?,?)",
                LocalDateTime.now(fixed).minusDays(1), LocalDateTime.now(fixed).plusDays(1),
                LocalDateTime.now(fixed).plusDays(2), LocalDateTime.now(fixed).plusDays(3));
        db.update("INSERT INTO event_session(id,event_id,session_name,session_time,status) VALUES(1,1,'Test',?,1)",
                LocalDateTime.now(fixed).plusDays(2));
        db.update("INSERT INTO ticket_tier(id,event_id,session_id,tier_name,original_price,current_price,total_stock,available_stock,status) VALUES(1,1,1,'Test tier',100,100,10,10,1)");
        fixture.stock().prepare(1);
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
    void soldOutBeforeAdmissionIsDefiniteRejectionAndLaterCapacityCanServeANewCard() {
        var other = confirmed.reserveConfirmedApplication(2, 1, 5, "other-one", new BigDecimal("100"));
        confirmed.reserveConfirmedApplication(3, 1, 5, "other-two", new BigDecimal("100"));
        assertThrows(ReservationRejectedException.class,
                () -> confirmed.reserveConfirmedApplication(1, 1, 1, "sold-out-card", new BigDecimal("100")));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM seckill_admission WHERE user_id=1", Integer.class));
        long releasedId = ((Number) other.get("requestId")).longValue();
        var reservations = (ReservationServiceImpl) core;
        reservations.releaseUnordered(releasedId, "test release");
        // 此fixture不启动消息监听，按真实消费者路径投影释放，仍执行原Redis Lua。
        stockService.release(stockService.from(reservations.find(releasedId)));
        var newCard = confirmed.reserveConfirmedApplication(1, 1, 1, "new-card", new BigDecimal("100"));
        assertNotNull(newCard.get("requestId"));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM stock_reservation WHERE user_id=1", Integer.class));
    }

    @Test
    void confirmedPriceMismatchRejectsAtAdmissionAndReleasesOnlyItsProvisionalStock() {
        db.update("UPDATE ticket_tier SET current_price=120 WHERE id=1");
        assertThrows(IllegalArgumentException.class,
                () -> confirmed.reserveConfirmedApplication(1, 1, 2, "confirm-price", new BigDecimal("100")));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM stock_reservation", Integer.class));
        assertEquals("REJECTED", db.queryForObject(
                "SELECT admission_status FROM seckill_admission WHERE request_key='confirm-price'", String.class));
        assertEquals(10, confirmed.getRealTimeStock(1L));
        assertEquals(10, db.queryForObject("SELECT available_stock FROM ticket_tier WHERE id=1", Integer.class));
    }

    @Test
    void acceptedConfirmedRequestKeepsOriginalAmountWhenPriceLaterChanges() {
        var accepted = confirmed.reserveConfirmedApplication(1, 1, 2, "confirmed", new BigDecimal("100"));
        db.update("UPDATE ticket_tier SET current_price=120 WHERE id=1");
        var replay = confirmed.reserveConfirmedApplication(1, 1, 2, "confirmed", new BigDecimal("100"));
        assertEquals(accepted.get("requestId"), replay.get("requestId"));
        assertEquals(0, new BigDecimal("200").compareTo((BigDecimal) replay.get("totalAmount")));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM stock_reservation", Integer.class));
        assertEquals(8, confirmed.getRealTimeStock(1L));
    }

    @Test
    void acceptanceDefersDatabaseDeductionAndRepeatedKeyReturnsSameReservation() throws Exception {
        var first = reserve(1, "one");
        var second = reserve(1, "one");
        assertEquals(first.get("requestId"), second.get("requestId"));
        assertEquals(10, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        call("createOrder", ((Number) first.get("requestId")).longValue());
        assertEquals(8, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM order_info", Integer.class));
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM message_outbox", Integer.class) >= 4);
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
        db.update("INSERT INTO order_info(order_no,user_id,event_id,session_id,tier_id,quantity,total_amount,order_status,expire_time) VALUES('historic',1,1,1,1,2,200,1,'2026-10-20')");
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
        db.update("INSERT INTO payment_record(id,order_id,order_no,user_id,payment_no,total_amount,pay_status,expire_time) VALUES(1,?,'test-order',1,'test-payment',199,2,'2026-10-20')", order);
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
        db.update("INSERT INTO payment_record(id,order_id,order_no,user_id,payment_no,total_amount,pay_status,expire_time) VALUES(1,?,'test-order',1,'test-payment',200,2,'2026-10-20')", order);
        assertEquals(true, core.getClass().getMethod("applyPaid", long.class, long.class).invoke(core, order, 1L));
        assertEquals(8, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
    }

    @Test
    void successfulPaymentJoinsCallerTransactionAndRollsBackTogether() throws Exception {
        long id = ((Number) reserve(1, "one").get("requestId")).longValue();
        long order = ((Number) call("createOrder", id)).longValue();
        db.update("INSERT INTO payment_record(id,order_id,order_no,user_id,payment_no,total_amount,pay_status,expire_time) VALUES(1,?,'test-order',1,'test-payment',200,2,'2026-10-20')", order);
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
        assertEquals(10, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        for (Long id : db.queryForList("SELECT id FROM stock_reservation", Long.class)) {
            call("createOrder", id);
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
                (ReservationService) core, provider, mapper(TradeOrderQueryMapper.class),
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
        call("createOrder", ((Number) reserve(1, "buy").get("requestId")).longValue());
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
                .getConstructor(TradeStockMapper.class, SeckillAdmissionMapper.class, PlatformTransactionManager.class,
                        Class.forName("org.example.ticketmanagement.trade.messaging.OutboxStore"), Clock.class,
                        boolean.class)
                .newInstance(mapper(TradeStockMapper.class), mapper(SeckillAdmissionMapper.class), manager, outbox,
                        Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC), true);
    }

    @Test
    void adjustmentRequiresOffSaleAndNoUnsettledReservation() throws Exception {
        var adjust = adjustment();
        var method = adjust.getClass().getMethod("adjust", long.class, int.class);
        assertThrows(InvocationTargetException.class, () -> method.invoke(adjust, 1L, 2));
        call("createOrder", ((Number) reserve(1, "buy").get("requestId")).longValue());
        db.update("UPDATE ticket_tier SET status=0");
        assertThrows(InvocationTargetException.class, () -> method.invoke(adjust, 1L, 2));
        assertEquals(8, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
    }

    @Test
    void adjustmentChangesCapacityAndAvailableBySameDeltaWithOutboxVersion() throws Exception {
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
        assertNotNull(call("createOrder", reservation));
        try (var session = tierSession()) {
            assertEquals(1, session.getMapper(TicketTierMapper.class).deleteById(1L));
        }
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM ticket_tier", Integer.class));
        assertEquals(0, db.queryForObject("SELECT status FROM ticket_tier", Integer.class));
        assertEquals(8, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        long orderId = db.queryForObject("SELECT id FROM order_info", Long.class);
        ((ReservationService) core).beginCancel(orderId, 1L);
        ((ReservationService) core).finishCancel(orderId);
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


    @Test
    void softDeletingEventPreservesCascadeChildrenAndOrderCancellationCanRelease() throws Exception {
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
