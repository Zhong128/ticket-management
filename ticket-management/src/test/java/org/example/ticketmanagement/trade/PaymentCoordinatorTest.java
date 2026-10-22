package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.dto.CreatePaymentDTO;
import org.example.ticketmanagement.pojo.PaymentRecord;
import org.example.ticketmanagement.service.ReservationService;
import org.example.ticketmanagement.trade.messaging.OutboxStore;
import org.example.ticketmanagement.trade.payment.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentCoordinatorTest {

    @Test
    void failedCloseAndUnknownRequeryNeverClosesLocalPayment() {
        var repository = mock(PaymentRepository.class);
        var gateway = mock(PaymentGateway.class);
        var record = new PaymentRecord();
        record.setId(1L);
        record.setOrderId(2L);
        record.setPaymentNo("P1");
        record.setPayStatus(0);
        record.setExpireTime(LocalDateTime.now().plusMinutes(10));
        when(repository.paymentsForOrder(2L)).thenReturn(List.of(record));
        when(gateway.query("P1")).thenReturn(new PaymentGateway.Query(PaymentGateway.State.WAITING, "P1", null, null),
                PaymentGateway.Query.unknown());
        when(gateway.close("P1")).thenReturn(false);
        var coordinator = new PaymentCoordinator(repository, gateway, mock(ReservationService.class),
                mock(OutboxStore.class), mock(RefundService.class), mock(TransactionTemplate.class), Clock.systemUTC());
        ReflectionTestUtils.setField(coordinator, "enabled", true);
        assertEquals(TradePaymentPort.CloseDecision.UNKNOWN, coordinator.closeForOrder(2L));
        verify(repository, never()).close(anyLong(), any());
    }

    @Test
    void ambiguousOrdersRequireExplicitOrderIdBeforeCreatingAnIntent() {
        var repository = mock(PaymentRepository.class);
        var gateway = mock(PaymentGateway.class);
        when(repository.payableOrders(eq(3L), any())).thenReturn(List.of(1L, 2L));
        var coordinator = new PaymentCoordinator(repository, gateway, mock(ReservationService.class),
                mock(OutboxStore.class), mock(RefundService.class), mock(TransactionTemplate.class), Clock.systemUTC());
        ReflectionTestUtils.setField(coordinator, "enabled", true);
        assertThrows(IllegalArgumentException.class, () -> coordinator.create(3L, new CreatePaymentDTO(null, 1)));
        verify(repository, never()).insert(any());
        verifyNoInteractions(gateway);
    }

    @Test
    void unsupportedPayTypeIsRejectedBeforeCreatingAnIntent() {
        var repository = mock(PaymentRepository.class);
        var gateway = mock(PaymentGateway.class);
        var coordinator = new PaymentCoordinator(repository, gateway, mock(ReservationService.class),
                mock(OutboxStore.class), mock(RefundService.class), mock(TransactionTemplate.class), Clock.systemUTC());
        ReflectionTestUtils.setField(coordinator, "enabled", true);
        assertThrows(IllegalArgumentException.class, () -> coordinator.create(3L, new CreatePaymentDTO(1L, 2)));
        verifyNoInteractions(repository, gateway);
    }

    @Test
    void uncertainChannelNeverClosesLocalPayment() {
        var repository = mock(PaymentRepository.class);
        var gateway = mock(PaymentGateway.class);
        var record = new PaymentRecord();
        record.setId(1L);
        record.setOrderId(2L);
        record.setPaymentNo("P1");
        record.setPayStatus(0);
        when(repository.paymentsForOrder(2L)).thenReturn(List.of(record));
        when(gateway.query("P1")).thenReturn(PaymentGateway.Query.unknown());
        var coordinator = new PaymentCoordinator(repository, gateway, mock(ReservationService.class),
                mock(OutboxStore.class), mock(RefundService.class), mock(TransactionTemplate.class), Clock.systemUTC());
        ReflectionTestUtils.setField(coordinator, "enabled", true);
        assertEquals(TradePaymentPort.CloseDecision.UNKNOWN, coordinator.closeForOrder(2L));
        verify(repository, never()).close(anyLong(), any());
    }
}
