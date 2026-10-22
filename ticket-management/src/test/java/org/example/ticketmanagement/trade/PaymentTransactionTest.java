package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.config.AlipayProperties;
import org.example.ticketmanagement.dto.CreatePaymentDTO;
import org.example.ticketmanagement.mapper.TradePaymentMapper;
import org.example.ticketmanagement.mapper.TradeRefundMapper;
import org.example.ticketmanagement.serviceimpl.PaymentServiceImpl;
import org.example.ticketmanagement.service.ReservationService;
import org.example.ticketmanagement.trade.messaging.OutboxStore;
import org.example.ticketmanagement.trade.payment.*;
import org.example.ticketmanagement.util.AlipaySignatureUtil;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Real MyBatis state transitions and transaction rollback; only the external channel is mocked.
 */
class PaymentTransactionTest {

    @Test
    void legacyMaximumLengthPaymentNumberCanCreateAStableRefund() throws Exception {
        var i = intent();
        String legacyNo = "L".repeat(64);
        db.update("UPDATE payment_record SET payment_no=? WHERE payment_no=?", legacyNo, i.payment());
        assertTrue(core.beginCancel(i.order(), 1L));
        assertTrue(core.finishCancel(i.order()));
        assertEquals("success", notifyPaid(legacyNo, "LEGACY-TRADE"));
        String refundNo = db.queryForObject("SELECT refund_no FROM refund_record", String.class);
        assertTrue(refundNo.length() <= 64);
        assertEquals("success", notifyPaid(legacyNo, "LEGACY-TRADE"));
        assertEquals(refundNo, db.queryForObject("SELECT refund_no FROM refund_record", String.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM refund_record", Integer.class));
    }

    TradeReservationTest fixture;
    JdbcTemplate db;
    PaymentGateway gateway;
    PaymentCoordinator payments;
    RefundService refunds;
    PaymentServiceImpl service;
    ReservationService core;
    Clock clock = Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setup() throws Exception {
        fixture = new TradeReservationTest();
        fixture.setup();
        db = fixture.db;
        core = (ReservationService) fixture.core;
        gateway = mock(PaymentGateway.class);
        when(gateway.create(any())).thenAnswer(i -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return "form";
        });
        var tx = new TransactionTemplate(fixture.manager);
        refunds = new RefundService(fixture.mapper(TradeRefundMapper.class), gateway, tx, clock);
        ReflectionTestUtils.setField(refunds, "enabled", true);
        payments = new PaymentCoordinator(new PaymentRepository(fixture.mapper(TradePaymentMapper.class)), gateway,
                core, (OutboxStore) ReflectionTestUtils.getField(core, "outbox"), refunds, tx, clock);
        ReflectionTestUtils.setField(payments, "enabled", true);
        var signature = mock(AlipaySignatureUtil.class);
        when(signature.verifyAlipaySignature(any())).thenReturn(true);
        var properties = new AlipayProperties();
        properties.setAppId("app");
        service = new PaymentServiceImpl(payments, signature, properties, mock(PaymentNotificationAudit.class));
    }

    record Intent(long order, String payment) {
    }

    Intent intent() throws Exception {
        long r = ((Number) fixture.reserve(1, "buy").get("requestId")).longValue();
        long o = ((Number) fixture.call("createOrder", r)).longValue();
        return new Intent(o, payments.create(1, new CreatePaymentDTO(o, 1)).payment().getPaymentNo());
    }

    String notifyPaid(String no, String trade) {
        return service.handleAlipayNotify(Map.of("app_id", "app", "out_trade_no", no, "trade_no", trade, "trade_status",
                "TRADE_SUCCESS", "total_amount", "200.00"));
    }

    @Test
    void canceledOrderLatePaymentCreatesOneFullRefundAndNeverReclaimsReleasedStock() throws Exception {
        var i = intent();
        assertTrue(core.beginCancel(i.order(), 1L));
        assertTrue(core.finishCancel(i.order()));
        assertEquals("success", notifyPaid(i.payment(), "T1"));
        assertEquals("success", notifyPaid(i.payment(), "T1"));
        assertEquals(2, db.queryForObject("SELECT order_status FROM order_info", Integer.class));
        assertEquals("RELEASED", db.queryForObject("SELECT status FROM stock_reservation", String.class));
        assertEquals(10, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM refund_record", Integer.class));
        assertEquals(new BigDecimal("200.00"), db.queryForObject("SELECT amount FROM refund_record", BigDecimal.class));
        String refundNo = db.queryForObject("SELECT refund_no FROM refund_record", String.class);
        when(gateway.queryRefund(i.payment(), refundNo, new BigDecimal("200.00"))).thenAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new PaymentGateway.RefundResult(PaymentGateway.RefundState.NOT_FOUND, null, null);
        });
        when(gateway.refund(i.payment(), refundNo, new BigDecimal("200.00"))).thenAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new PaymentGateway.RefundResult(PaymentGateway.RefundState.SUCCESS, "T1", null);
        });
        assertEquals(1, refunds.processDueRefunds());
        assertEquals(0, refunds.processDueRefunds());
        assertEquals("success", notifyPaid(i.payment(), "T1"));
        assertEquals("SUCCESS", db.queryForObject("SELECT status FROM refund_record", String.class));
        assertEquals(5, db.queryForObject("SELECT pay_status FROM payment_record", Integer.class));
        assertEquals(10, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        verify(gateway, times(1)).refund(i.payment(), refundNo, new BigDecimal("200.00"));
    }

    @Test
    void expiredRefundLeaseIsRecoveredWithPersistedNumberBeforeAnotherChannelSubmission() throws Exception {
        var i = intent();
        assertTrue(core.beginCancel(i.order(), 1L));
        assertTrue(core.finishCancel(i.order()));
        assertEquals("success", notifyPaid(i.payment(), "T1"));
        String refundNo = db.queryForObject("SELECT refund_no FROM refund_record", String.class);
        db.update("UPDATE refund_record SET" + " status='PROCESSING',attempts=1,lease_token='old-worker',lease_until=?",
                LocalDateTime.now(clock).minusSeconds(1));
        when(gateway.queryRefund(i.payment(), refundNo, new BigDecimal("200.00"))).thenAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(2, db.queryForObject("SELECT attempts FROM refund_record", Integer.class));
            assertNotEquals("old-worker", db.queryForObject("SELECT lease_token FROM refund_record", String.class));
            return new PaymentGateway.RefundResult(PaymentGateway.RefundState.SUCCESS, "T1", "already refunded");
        });
        assertEquals(1, refunds.processDueRefunds());
        assertEquals("SUCCESS", db.queryForObject("SELECT status FROM refund_record", String.class));
        assertNull(db.queryForObject("SELECT lease_token FROM refund_record", String.class));
        assertEquals(5, db.queryForObject("SELECT pay_status FROM payment_record", Integer.class));
        assertEquals(refundNo, db.queryForObject("SELECT refund_no FROM refund_record", String.class));
        verify(gateway, never()).refund(any(), any(), any());
    }

    @Test
    void paymentBeforeChannelDeadlineMaySettleAfterLocalDeadlineWhileStockIsHeld() throws Exception {
        var i = intent();
        db.update("UPDATE order_info SET expire_time=?", LocalDateTime.parse("2026-10-18T23:59:00"));
        assertEquals("success", notifyPaid(i.payment(), "T1"));
        assertEquals("success", notifyPaid(i.payment(), "T1"));
        assertEquals(1, db.queryForObject("SELECT order_status FROM order_info", Integer.class));
        assertEquals("PAID", db.queryForObject("SELECT status FROM stock_reservation", String.class));
        assertEquals(8, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM refund_record", Integer.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM message_outbox WHERE event_type='PAYMENT_SUCCESS'",
                Integer.class));
    }

    @Test
    void secondCapturedPaymentForSameOrderIsRefundedWithoutChangingFirstPayment() throws Exception {
        var i = intent();
        assertEquals("success", notifyPaid(i.payment(), "T1"));
        db.update("INSERT INTO"
                + " payment_record(order_id,order_no,user_id,payment_no,total_amount,pay_status,pay_type,expire_time,create_time,update_time)"
                + " SELECT" + " order_id,order_no,user_id,'P2',total_amount,0,1,expire_time,create_time,update_time"
                + " FROM payment_record WHERE payment_no=?", i.payment());
        assertEquals("success", notifyPaid("P2", "T2"));
        assertEquals("success", notifyPaid("P2", "T2"));
        assertEquals("P2", db.queryForObject("SELECT payment_no FROM refund_record", String.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM refund_record", Integer.class));
        assertEquals(1, db.queryForObject("SELECT order_status FROM order_info", Integer.class));
        assertEquals(8, db.queryForObject("SELECT available_stock FROM ticket_tier", Integer.class));
    }

    @Test
    void outboxFailureRollsBackPaymentAndOrderTogether() throws Exception {
        var i = intent();
        db.execute("DROP TABLE message_outbox");
        assertEquals("failure", notifyPaid(i.payment(), "T1"));
        assertEquals(0, db.queryForObject("SELECT pay_status FROM payment_record", Integer.class));
        assertEquals(0, db.queryForObject("SELECT order_status FROM order_info", Integer.class));
        assertEquals("ORDER_CREATED", db.queryForObject("SELECT status FROM stock_reservation", String.class));
    }
}
