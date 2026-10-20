package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.config.AlipayProperties;
import org.example.ticketmanagement.serviceimpl.PaymentServiceImpl;
import org.example.ticketmanagement.trade.payment.PaymentCoordinator;
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
        assertEquals("failure", new PaymentServiceImpl(payments, verifier, props).handleAlipayNotify(params));
        verifyNoInteractions(payments);
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
        assertEquals("success", new PaymentServiceImpl(payments, verifier, props).handleAlipayNotify(params));
        verify(payments).settle("P1", "T1", new BigDecimal("10.00"));
        assertEquals(Map.of("app_id", "app", "trade_status", "TRADE_SUCCESS", "out_trade_no", "P1", "trade_no", "T1",
                "total_amount", "10.00"), params);
    }
}
