package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.dto.RedisReservation;
import org.example.ticketmanagement.pojo.SeckillAdmission;
import org.example.ticketmanagement.serviceimpl.ReservationServiceImpl;
import org.example.ticketmanagement.serviceimpl.SeckillServiceImpl;
import org.example.ticketmanagement.serviceimpl.StockServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentConfirmedPriceTest {

    @Test
    void confirmationPriceTravelsToActualAdmissionAndRejectionReleasesProvisionalStock() {
        var reservations = mock(ReservationServiceImpl.class);
        var stock = mock(StockServiceImpl.class);
        var service = new SeckillServiceImpl(reservations, stock, null, null, null, null, Clock.systemUTC());
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "lockingEnabled", false);
        var pending = new RedisReservation(7, 91, "fixed-action-key", 2, "token", 1, 0, 100);
        var price = new BigDecimal("100.00");
        when(stock.reserve(91, 7, 2, "fixed-action-key")).thenReturn(pending);
        when(reservations.accept(pending, price)).thenThrow(new IllegalArgumentException("票价已变化，请重新确认"));
        var rejected = new SeckillAdmission();
        rejected.setAdmissionStatus("REJECTED");
        when(reservations.sealRejected(pending)).thenReturn(rejected);
        assertThrows(IllegalArgumentException.class,
                () -> service.reserveConfirmedApplication(91, 7, 2, "fixed-action-key", price));
        verify(reservations).accept(pending, price);
        verify(reservations, never()).accept(pending);
        verify(stock).release(pending);
    }

    @Test
    void missingOrInvalidConfirmationPriceCannotTouchStock() {
        var stock = mock(StockServiceImpl.class);
        var service = new SeckillServiceImpl(null, stock, null, null, null, null, Clock.systemUTC());
        for (BigDecimal price : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("-1")}) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.reserveConfirmedApplication(91, 7, 2, "key", price));
        }
        verifyNoInteractions(stock);
    }
}
