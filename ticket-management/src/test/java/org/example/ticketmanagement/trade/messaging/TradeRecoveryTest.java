package org.example.ticketmanagement.trade.messaging;

import org.example.ticketmanagement.mapper.TradeRecoveryMapper;
import org.example.ticketmanagement.service.OrderService;
import org.example.ticketmanagement.serviceimpl.OrderRecoveryService;
import org.example.ticketmanagement.exception.StockVersionConflictException;
import org.example.ticketmanagement.exception.OrderCreationDeferredException;
import org.example.ticketmanagement.service.ReservationService;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TradeRecoveryTest {

    @Test
    void versionConflictRemainsPendingForTheNextRecoveryScan() {
        var db = mock(TradeRecoveryMapper.class);
        var reservations = mock(ReservationService.class);
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 0, 0);
        when(db.findDueReservations(eq(0L), any())).thenReturn(List.of(
                Map.of("id", 1L, "expires_at", now.plusMinutes(20))));
        when(reservations.createOrder(1)).thenThrow(new StockVersionConflictException());
        new OrderRecoveryService(db, reservations, mock(OrderService.class),
                Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)).recover();
        verify(reservations, never()).creationFailed(anyLong(), anyString());
        verify(reservations, never()).releaseUnordered(anyLong(), anyString());
    }

    @Test
    void unavailableOrderCoordinationDoesNotCountAsBusinessFailure() {
        var db = mock(TradeRecoveryMapper.class);
        var reservations = mock(ReservationService.class);
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 0, 0);
        when(db.findDueReservations(eq(0L), any())).thenReturn(List.of(
                Map.of("id", 1L, "expires_at", now.plusMinutes(20))));
        when(reservations.createOrder(1)).thenThrow(new OrderCreationDeferredException("busy"));
        new OrderRecoveryService(db, reservations, mock(OrderService.class),
                Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)).recover();
        verify(reservations, never()).creationFailed(anyLong(), anyString());
        verify(reservations, never()).releaseUnordered(anyLong(), anyString());
    }

    @Test
    void brokerOutageStillReleasesExpiredAndCreatesLiveReservations() {
        TradeRecoveryMapper db = mock(TradeRecoveryMapper.class);
        ReservationService core = mock(ReservationService.class);
        OrderService orders = mock(OrderService.class);
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 0, 0);
        when(db.findDueReservations(eq(0L), any())).thenReturn(List.of(
                Map.of("id", 1L, "status", "RESERVED", "expires_at", Timestamp.valueOf(now.minusSeconds(1))),
                Map.of("id", 2L, "status", "RESERVED", "expires_at", now.plusMinutes(20)), Map.of("id", 3L, "order_id",
                        30L, "status", "ORDER_CREATED", "expires_at", Timestamp.valueOf(now.minusSeconds(1)))));
        when(db.findOrdersRequiringCancellation(any())).thenReturn(List.of(40L));
        new OrderRecoveryService(db, core, orders, Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)).recover();
        verify(core).releaseUnordered(1, "TIMEOUT_RECOVERY");
        verify(core).createOrder(2);
        verify(orders).cancelOrder(30L);
        verify(orders).cancelOrder(40L);
    }
}
