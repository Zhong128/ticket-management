package org.example.ticketmanagement.trade.messaging;

import org.example.ticketmanagement.mapper.TradeRecoveryMapper;
import org.example.ticketmanagement.service.OrderService;
import org.example.ticketmanagement.trade.core.TradeOperations;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TradeRecoveryTest {

    @Test
    void brokerOutageStillReleasesExpiredAndCreatesLiveReservations() {
        TradeRecoveryMapper db = mock(TradeRecoveryMapper.class);
        TradeOperations core = mock(TradeOperations.class);
        OrderService orders = mock(OrderService.class);
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 0, 0);
        when(db.findRecoverableReservations()).thenReturn(List.of(
                Map.of("id", 1L, "status", "RESERVED", "expires_at", Timestamp.valueOf(now.minusSeconds(1))),
                Map.of("id", 2L, "status", "RESERVED", "expires_at", now.plusMinutes(20)), Map.of("id", 3L, "order_id",
                        30L, "status", "ORDER_CREATED", "expires_at", Timestamp.valueOf(now.minusSeconds(1)))));
        when(db.findOrdersRequiringCancellation(any())).thenReturn(List.of(40L));
        new TradeRecovery(db, core, orders, Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)).recover();
        verify(core).releaseUnordered(1, "TIMEOUT_RECOVERY");
        verify(core).createOrder(2);
        verify(orders).cancelOrder(30L);
        verify(orders).cancelOrder(40L);
    }
}
