package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.service.SeckillLockService;
import org.example.ticketmanagement.exception.ReservationRejectedException;
import org.example.ticketmanagement.serviceimpl.ReservationServiceImpl;
import org.example.ticketmanagement.serviceimpl.SeckillServiceImpl;
import org.example.ticketmanagement.serviceimpl.StockServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeckillLockModeTest {

    @Test
    void confirmedRequestRejectedBeforeSupplierStartsDoesNotBecomeUnknownOrTouchStock() {
        var reservations = mock(ReservationServiceImpl.class);
        var stock = mock(StockServiceImpl.class);
        var locks = mock(SeckillLockService.class);
        var service = new SeckillServiceImpl(reservations, stock, locks, null, null, null, Clock.systemUTC());
        ReflectionTestUtils.setField(service, "enabled", true);
        when(locks.executeWithSegmentLock(anyLong(), anyLong(), anyInt(), any()))
                .thenThrow(new IllegalStateException("lock busy"));
        assertThrows(ReservationRejectedException.class,
                () -> service.reserveConfirmedApplication(1, 1, 1, "key", new BigDecimal("100")));
        verifyNoInteractions(stock, reservations);
    }

    @Test
    void explicitlyDisabledSegmentLockStillUsesLuaAdmission() {
        var stock = mock(StockServiceImpl.class);
        var locks = mock(SeckillLockService.class);
        var service = new SeckillServiceImpl(mock(ReservationServiceImpl.class), stock, locks,
                null, null, null, Clock.systemUTC());
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "lockingEnabled", false);
        when(stock.reserve(anyLong(), anyLong(), anyInt(), anyString()))
                .thenThrow(new IllegalStateException("库存不足"));
        assertThrows(IllegalStateException.class, () -> service.reserveApplication(1, 1, 1, "same-key"));
        verify(stock).reserve(1, 1, 1, "same-key");
        verifyNoInteractions(locks);
    }
}
