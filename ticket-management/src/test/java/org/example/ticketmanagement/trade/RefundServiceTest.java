package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.mapper.TradeRefundMapper;
import org.example.ticketmanagement.trade.payment.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefundServiceTest {

    @SuppressWarnings("unchecked")
    private RefundService service(PaymentGateway gateway, int claimResult) {
        Map<String, Object> row = Map.of("payment_no", "P1", "refund_no", "REFUND_P1", "payment_id", 1L, "amount",
                new BigDecimal("10.00"), "attempts", 1);
        var mapper = mock(TradeRefundMapper.class);
        when(mapper.findDueIds(any())).thenReturn(List.of(1L));
        when(mapper.claim(anyLong(), anyString(), any(), any())).thenReturn(claimResult);
        when(mapper.findById(1L)).thenReturn(row);
        when(mapper.complete(anyLong(), anyString(), anyString(), any(), any(), any(), any())).thenReturn(1);
        var tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(
                i -> ((TransactionCallback<Object>) i.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
        doAnswer(i -> {
            ((Consumer<TransactionStatus>) i.getArgument(0)).accept(mock(TransactionStatus.class));
            return null;
        }).when(tx).executeWithoutResult(any());
        var service = new RefundService(mapper, gateway, tx, Clock.systemUTC());
        ReflectionTestUtils.setField(service, "enabled", true);
        return service;
    }

    @Test
    void uncertainPriorRefundIsQueriedThenRetriedWithOriginalNumberAndFullAmount() {
        var gateway = mock(PaymentGateway.class);
        var amount = new BigDecimal("10.00");
        when(gateway.queryRefund("P1", "REFUND_P1", amount))
                .thenReturn(new PaymentGateway.RefundResult(PaymentGateway.RefundState.UNKNOWN, null, null));
        when(gateway.refund("P1", "REFUND_P1", amount))
                .thenReturn(new PaymentGateway.RefundResult(PaymentGateway.RefundState.SUCCESS, "T1", null));
        assertEquals(1, service(gateway, 1).processDueRefunds());
        var ordered = inOrder(gateway);
        ordered.verify(gateway).queryRefund("P1", "REFUND_P1", amount);
        ordered.verify(gateway).refund("P1", "REFUND_P1", amount);
    }

    @Test
    void confirmedExistingRefundIsNotSubmittedAgain() {
        var gateway = mock(PaymentGateway.class);
        when(gateway.queryRefund(eq("P1"), eq("REFUND_P1"), any()))
                .thenReturn(new PaymentGateway.RefundResult(PaymentGateway.RefundState.SUCCESS, "T1", null));
        assertEquals(1, service(gateway, 1).processDueRefunds());
        verify(gateway, never()).refund(any(), any(), any());
    }

    @Test
    void anotherInstanceHoldingLeasePreventsChannelCalls() {
        var gateway = mock(PaymentGateway.class);
        assertEquals(0, service(gateway, 0).processDueRefunds());
        verifyNoInteractions(gateway);
    }
}
