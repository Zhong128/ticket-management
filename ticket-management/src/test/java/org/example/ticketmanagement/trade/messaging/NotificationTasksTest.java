package org.example.ticketmanagement.trade.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ticketmanagement.mapper.TradeTaskMapper;
import org.example.ticketmanagement.service.EmailService;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationTasksTest {

    @Test
    void thirdFailureIsPersistedForManualAttention() {
        TradeTaskMapper db = mock(TradeTaskMapper.class);
        EmailService email = mock(EmailService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC);
        when(db.findDueEmailTasks(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(Map.of("id", 9L, "order_id", 70L, "task_type", "EMAIL_ORDER", "retry_count", 2)));
        when(db.claimEmailTask(anyString(), any(), any(), any(), anyLong(), any(), any())).thenReturn(1);
        when(db.findOrderWithEmail(any())).thenReturn(Map.of("email", "", "order_status", 0));
        new NotificationTasks(db, email, new ObjectMapper(), clock).processDue();
        verify(db).claimEmailTask(anyString(), any(), any(), any(), eq(9L), any(), any());
        verify(db).failEmailTask(eq(3), any(), eq("User has no email"), any(), eq(9L), any());
        verifyNoInteractions(email);
    }

    @Test
    void phantomOrderEventDoesNotSendOrCreateNotification() {
        TradeTaskMapper db = mock(TradeTaskMapper.class);
        EmailService email = mock(EmailService.class);
        ObjectMapper mapper = new ObjectMapper();
        when(db.findOrderByReservation(anyLong())).thenReturn(List.of());
        new NotificationTasks(db, email, mapper, Clock.systemUTC()).accept("ORDER_CREATED",
                mapper.valueToTree(Map.of("reservationId", 7)));
        verify(db, never()).insertEmailTask(any(), any(), any(), anyString(), anyString(), anyString(), any(), any(),
                any());
        verifyNoInteractions(email);
    }

    @Test
    void exhaustedLeaseIsMarkedManualWithoutSendingAgain() {
        TradeTaskMapper db = mock(TradeTaskMapper.class);
        EmailService email = mock(EmailService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC);
        when(db.findDueEmailTasks(any(), any()))
                .thenReturn(List.of(Map.of("id", 9L, "order_id", 70L, "task_type", "EMAIL_ORDER", "retry_count", 3)));
        new NotificationTasks(db, email, new ObjectMapper(), clock).processDue();
        verify(db).exhaustEmailTask(9L, LocalDateTime.now(clock));
        verify(db, never()).findOrderWithEmail(any());
        verifyNoInteractions(email);
    }
}
