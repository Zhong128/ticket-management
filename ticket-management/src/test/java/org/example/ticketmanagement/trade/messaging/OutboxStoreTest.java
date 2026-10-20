package org.example.ticketmanagement.trade.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ticketmanagement.mapper.TradeOutboxMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.time.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exercises durable write semantics without requiring a broker.
 */
class OutboxStoreTest {

    @Test
    void enqueueUsesStableBusinessKeyAndClock() throws Exception {
        TradeOutboxMapper db = mock(TradeOutboxMapper.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC);
        Class<?> type = Class.forName("org.example.ticketmanagement.trade.messaging.OutboxStore");
        Object store = type.getConstructor(TradeOutboxMapper.class, ObjectMapper.class, Clock.class).newInstance(db,
                new ObjectMapper(), clock);
        type.getMethod("enqueue", String.class, String.class, Object.class).invoke(store, "CREATE_ORDER", "create:7",
                Map.of("reservationId", 7));
        verify(db).insertEvent(anyString(), eq("create:7"), eq("CREATE_ORDER"), eq("{\"reservationId\":7}"),
                eq(LocalDateTime.now(clock)), eq(LocalDateTime.now(clock)), eq(LocalDateTime.now(clock)));
    }

    @Test
    void serializationFailureNeverWritesPartialOutbox() throws Exception {
        TradeOutboxMapper db = mock(TradeOutboxMapper.class);
        Class<?> type = Class.forName("org.example.ticketmanagement.trade.messaging.OutboxStore");
        Object store = type.getConstructor(TradeOutboxMapper.class, ObjectMapper.class, Clock.class).newInstance(db,
                new ObjectMapper(), Clock.systemUTC());
        Object bad = new Object();
        assertThrows(InvocationTargetException.class,
                () -> type.getMethod("enqueue", String.class, String.class, Object.class).invoke(store, "CREATE_ORDER",
                        "bad", bad));
        verifyNoInteractions(db);
    }
}
