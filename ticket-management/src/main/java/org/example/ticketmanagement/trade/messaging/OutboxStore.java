package org.example.ticketmanagement.trade.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.mapper.TradeOutboxMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OutboxStore {

    private final TradeOutboxMapper db;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String eventType, String businessKey, Object payload) {
        enqueueAt(eventType, businessKey, payload, LocalDateTime.now(clock));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueAt(String eventType, String businessKey, Object payload, LocalDateTime availableAt) {
        final String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize durable event", e);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        db.insertEvent(UUID.randomUUID().toString(), businessKey, eventType, json, availableAt, now, now);
    }
}
