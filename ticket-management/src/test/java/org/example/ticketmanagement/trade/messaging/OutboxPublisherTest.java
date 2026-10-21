package org.example.ticketmanagement.trade.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ticketmanagement.mapper.TradeOutboxMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxPublisherTest {

    private final TradeOutboxMapper db = mock(TradeOutboxMapper.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC);

    private void due() {
        when(db.findDueEvents(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of(Map.of("event_id",
                "id-7", "event_type", "CREATE_ORDER", "payload", "{\"reservationId\":7}", "attempts", 9)));
        claimed();
    }

    private void claimed() {
        when(db.claimEvents(anyString(), any(), any(), anyList(), any(), any())).thenReturn(1);
        when(db.findClaimedEventIds(anyString(), anyList())).thenAnswer(inv -> inv.getArgument(1));
    }

    @Test
    void publishesOnlyAfterAckAndPreservesEventIdentity() {
        due();
        doAnswer(inv -> {
            Message message = inv.getArgument(2);
            assertEquals("id-7", message.getMessageProperties().getHeader("eventId"));
            assertTrue(new String(message.getBody(), StandardCharsets.UTF_8).contains("CREATE_ORDER"));
            inv.<CorrelationData>getArgument(3).getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        new OutboxPublisher(db, new ObjectMapper(), rabbit, clock).publishDue();
        verify(db).markPublishedEvents(eq(LocalDateTime.now(clock)), anyString(), eq(List.of("id-7")));
    }

    @Test
    void returnedMessageRetriesEvenAfterBrokerAck() {
        due();
        doAnswer(inv -> {
            CorrelationData c = inv.getArgument(3);
            c.setReturned(new ReturnedMessage(inv.getArgument(2), 312, "NO_ROUTE", "trade.events.v2", "CREATE_ORDER"));
            c.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        new OutboxPublisher(db, new ObjectMapper(), rabbit, clock).publishDue();
        verify(db, never()).markPublishedEvents(any(), anyString(), anyList());
        verify(db).rescheduleEvent(any(), anyString(), any(), eq("id-7"), anyString());
    }

    @Test
    void anotherPublishersLeasePreventsDuplicateSend() {
        due();
        when(db.claimEvents(anyString(), any(), any(), anyList(), any(), any())).thenReturn(0);
        new OutboxPublisher(db, new ObjectMapper(), rabbit, clock).publishDue();
        verifyNoInteractions(rabbit);
    }

    @Test
    void deadlineTtlUsesAbsoluteReservationExpiryAtPublication() {
        due();
        when(db.findDueEvents(any(), any())).thenReturn(List.of(Map.of("event_id", "deadline-7", "event_type",
                "ORDER_DEADLINE", "payload", "{\"reservationId\":7}", "attempts", 0)));
        when(db.findReservationExpiry(7L)).thenReturn(LocalDateTime.now(clock).plusMinutes(20));
        doAnswer(inv -> {
            Message message = inv.getArgument(2);
            assertEquals("1200000", message.getMessageProperties().getExpiration());
            inv.<CorrelationData>getArgument(3).getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        new OutboxPublisher(db, new ObjectMapper(), rabbit, clock).publishDue();
        verify(db).markPublishedEvents(any(), anyString(), eq(List.of("deadline-7")));
    }

    @Test
    void publishesNextBatchWithoutWaitingForAnotherScheduledRun() {
        List<Map<String, Object>> firstBatch = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            firstBatch.add(Map.of("event_id", "event-" + i, "event_type", "CREATE_ORDER",
                    "payload", "{\"reservationId\":7}", "attempts", 0));
        }
        Map<String, Object> lastEvent = Map.of("event_id", "event-100", "event_type", "CREATE_ORDER",
                "payload", "{\"reservationId\":7}", "attempts", 0);
        when(db.findDueEvents(any(), any())).thenReturn(firstBatch, List.of(lastEvent));
        claimed();
        doAnswer(inv -> {
            inv.<CorrelationData>getArgument(3).getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        new OutboxPublisher(db, new ObjectMapper(), rabbit, clock).publishDue();

        verify(db, times(2)).findDueEvents(any(), any());
        verify(rabbit, times(101)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        verify(db, times(2)).claimEvents(anyString(), any(), any(), anyList(), any(), any());
        verify(db, times(2)).markPublishedEvents(any(), anyString(), anyList());
    }

    @Test
    void sendsWholeBatchBeforeWaitingForPublisherConfirms() {
        List<Map<String, Object>> events = List.of(
                Map.of("event_id", "first", "event_type", "CREATE_ORDER",
                        "payload", "{\"reservationId\":7}", "attempts", 0),
                Map.of("event_id", "second", "event_type", "CREATE_ORDER",
                        "payload", "{\"reservationId\":8}", "attempts", 0));
        when(db.findDueEvents(any(), any())).thenReturn(events);
        claimed();
        List<CorrelationData> sent = new ArrayList<>();
        doAnswer(inv -> {
            sent.add(inv.getArgument(3));
            if (sent.size() == 2) {
                for (CorrelationData correlation : sent) {
                    correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
                }
            }
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        new OutboxPublisher(db, new ObjectMapper(), rabbit, clock).publishDue();

        verify(db).markPublishedEvents(any(), anyString(), eq(List.of("first", "second")));
        verify(db, never()).rescheduleEvent(any(), anyString(), any(), anyString(), anyString());
    }
}
