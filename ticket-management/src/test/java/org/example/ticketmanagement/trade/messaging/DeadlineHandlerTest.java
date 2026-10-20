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

class DeadlineHandlerTest {

    @Test
    void earlyDeadlineDurablyReschedulesWithoutForgettingOrder() {
        TradeRecoveryMapper db = mock(TradeRecoveryMapper.class);
        TradeOperations core = mock(TradeOperations.class);
        OrderService orders = mock(OrderService.class);
        OutboxStore outbox = mock(OutboxStore.class);
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 0, 0);
        when(db.findReservationDeadline(7L)).thenReturn(
                List.of(Map.of("order_id", 70L, "status", "ORDER_CREATED", "expires_at", now.plusMinutes(20))));
        new DeadlineHandler(db, core, orders, outbox, Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
                .handle(7);
        verify(outbox).enqueue(eq("ORDER_DEADLINE"), startsWith("deadline-retry:7:"), eq(Map.of("reservationId", 7L)));
        verifyNoInteractions(core, orders);
    }

    @Test
    void expiredUnorderedReservationIsReleasedThroughCore() {
        TradeRecoveryMapper db = mock(TradeRecoveryMapper.class);
        TradeOperations core = mock(TradeOperations.class);
        OrderService orders = mock(OrderService.class);
        OutboxStore outbox = mock(OutboxStore.class);
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 0, 0);
        when(db.findReservationDeadline(7L))
                .thenReturn(List.of(Map.of("status", "RESERVED", "expires_at", Timestamp.valueOf(now))));
        new DeadlineHandler(db, core, orders, outbox, Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
                .handle(7);
        verify(core).releaseUnordered(7, "TIMEOUT");
        verifyNoInteractions(orders, outbox);
    }
}
