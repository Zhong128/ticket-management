package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.config.AlipayProperties;
import org.example.ticketmanagement.serviceimpl.PaymentServiceImpl;
import org.example.ticketmanagement.trade.payment.PaymentCoordinator;
import org.example.ticketmanagement.trade.payment.PaymentNotificationAudit;
import org.example.ticketmanagement.util.AlipaySignatureUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentNotifyTest {

    @Test
    void validSignatureFromAnotherApplicationDoesNotSettle() {
        var payments = mock(PaymentCoordinator.class);
        var verifier = mock(AlipaySignatureUtil.class);
        var props = new AlipayProperties();
        props.setAppId("expected");
        var params = Map.of("app_id", "other", "trade_status", "TRADE_SUCCESS", "out_trade_no", "P1", "trade_no", "T1",
                "total_amount", "10.00");
        when(verifier.verifyAlipaySignature(params)).thenReturn(true);
        var audit = mock(PaymentNotificationAudit.class);
        assertEquals("failure", new PaymentServiceImpl(payments, verifier, props, audit).handleAlipayNotify(params));
        verifyNoInteractions(payments);
        verifyNoInteractions(audit);
    }

    @Test
    void paidCallbackUsesUnifiedSettlementAndPreservesParameters() {
        var payments = mock(PaymentCoordinator.class);
        var verifier = mock(AlipaySignatureUtil.class);
        var props = new AlipayProperties();
        props.setAppId("app");
        var params = Map.of("app_id", "app", "trade_status", "TRADE_SUCCESS", "out_trade_no", "P1", "trade_no", "T1",
                "total_amount", "10.00");
        when(verifier.verifyAlipaySignature(params)).thenReturn(true);
        var audit = mock(PaymentNotificationAudit.class);
        assertEquals("success", new PaymentServiceImpl(payments, verifier, props, audit).handleAlipayNotify(params));
        verify(payments).settle("P1", "T1", new BigDecimal("10.00"));
        verify(audit).recordHandled("P1", "T1", "TRADE_SUCCESS", new BigDecimal("10.00"));
        assertEquals(Map.of("app_id", "app", "trade_status", "TRADE_SUCCESS", "out_trade_no", "P1", "trade_no", "T1",
                "total_amount", "10.00"), params);
    }

    @Test
    void invalidAmountDoesNotWriteAudit() {
        var payments = mock(PaymentCoordinator.class);
        var verifier = mock(AlipaySignatureUtil.class);
        var audit = mock(PaymentNotificationAudit.class);
        var props = new AlipayProperties();
        props.setAppId("app");
        var params = Map.of("app_id", "app", "trade_status", "TRADE_SUCCESS", "out_trade_no", "P1", "trade_no", "T1",
                "total_amount", "10.00");
        when(verifier.verifyAlipaySignature(params)).thenReturn(true);
        doThrow(new IllegalArgumentException("金额不匹配")).when(payments)
                .validateNotification("P1", "T1", new BigDecimal("10.00"));

        assertEquals("failure", new PaymentServiceImpl(payments, verifier, props, audit).handleAlipayNotify(params));
        verify(payments, never()).settle(any(), any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void auditFailureRequestsCallbackRetryAfterIdempotentSettlement() {
        var payments = mock(PaymentCoordinator.class);
        var verifier = mock(AlipaySignatureUtil.class);
        var audit = mock(PaymentNotificationAudit.class);
        var props = new AlipayProperties();
        props.setAppId("app");
        var params = Map.of("app_id", "app", "trade_status", "TRADE_SUCCESS", "out_trade_no", "P1", "trade_no", "T1",
                "total_amount", "10.00");
        when(verifier.verifyAlipaySignature(params)).thenReturn(true);
        doThrow(new IllegalStateException("审计写入失败")).when(audit)
                .recordHandled("P1", "T1", "TRADE_SUCCESS", new BigDecimal("10.00"));

        assertEquals("failure", new PaymentServiceImpl(payments, verifier, props, audit).handleAlipayNotify(params));
        verify(payments).settle("P1", "T1", new BigDecimal("10.00"));
    }
}
