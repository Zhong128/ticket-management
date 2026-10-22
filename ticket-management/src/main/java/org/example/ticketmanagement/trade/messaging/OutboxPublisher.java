package org.example.ticketmanagement.trade.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.mapper.TradeOutboxMapper;
import org.example.ticketmanagement.util.RabbitMQUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(name = "trade.enabled", havingValue = "true")
public class OutboxPublisher {

    public static final String EXCHANGE = "trade.events.v3";
    private static final int MAX_BATCHES_PER_RUN = 10;
    private static final long CONFIRM_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);

    private final TradeOutboxMapper db;
    private final ObjectMapper mapper;
    private final RabbitMQUtil transport;
    private final Clock clock;

    @Autowired
    public OutboxPublisher(TradeOutboxMapper db, ObjectMapper mapper, RabbitMQUtil transport, Clock clock) {
        this.db = db;
        this.mapper = mapper;
        this.transport = transport;
        this.clock = clock;
    }

    public OutboxPublisher(TradeOutboxMapper db, ObjectMapper mapper, RabbitTemplate rabbit, Clock clock) {
        this(db, mapper, new RabbitMQUtil(rabbit), clock);
    }

    @Scheduled(fixedDelay = 250)
    public void publishDue() {
        // 有积压时连续处理有限批次，避免每 100 条都额外等待调度间隔。
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            LocalDateTime now = LocalDateTime.now(clock);
            List<Map<String, Object>> rows = db.findDueEvents(now, now);
            if (rows.isEmpty()) {
                return;
            }

            publishBatch(rows);
            if (rows.size() < 100) {
                return;
            }
        }
    }

    private void publishBatch(List<Map<String, Object>> rows) {
        List<PendingPublication> pending = new ArrayList<>(rows.size());
        List<String> ids = rows.stream().map(row -> (String) row.get("event_id")).toList();
        LocalDateTime now = LocalDateTime.now(clock);
        String token = UUID.randomUUID().toString();

        // 批量领取租约，并只发送本实例实际领取到的事件。
        if (db.claimEvents(token, now.plusSeconds(30), now, ids, now, now) == 0) {
            return;
        }
        Set<String> claimedIds = new HashSet<>(db.findClaimedEventIds(token, ids));

        for (Map<String, Object> row : rows) {
            String id = (String) row.get("event_id");
            if (!claimedIds.contains(id)) {
                continue;
            }

            try {
                // 先发送整批消息，再集中等待各自的 Broker 确认。
                CorrelationData correlation = publish(row, id);
                pending.add(new PendingPublication(row, id, correlation));
            } catch (Exception e) {
                reschedule(row, id, token, e);
            }
        }

        // 使用整批共享的超时期限，避免多个未确认消息把租约逐个耗尽。
        long confirmDeadline = System.nanoTime() + CONFIRM_TIMEOUT_NANOS;
        List<String> confirmedIds = new ArrayList<>(pending.size());
        for (PendingPublication event : pending) {
            try {
                confirm(event.correlation(), confirmDeadline);
                confirmedIds.add(event.id());
            } catch (Exception e) {
                reschedule(event.row(), event.id(), token, e);
            }
        }

        // 仅把已确认且仍持有本批租约的事件批量标记为成功。
        if (!confirmedIds.isEmpty()) {
            int published = db.markPublishedEvents(LocalDateTime.now(clock), token, confirmedIds);
            if (published != confirmedIds.size()) {
                log.warn("Outbox lease changed before completion confirmed={} published={}",
                        confirmedIds.size(), published);
            }
        }
    }

    private CorrelationData publish(Map<String, Object> row, String id) throws Exception {
        String type = (String) row.get("event_type");
        Object payload = mapper.readTree(row.get("payload").toString());

        // 设置持久化消息及业务标识，便于消费者幂等处理。
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setHeader("eventId", id);
        props.setHeader("eventType", type);
        props.setMessageId(id);
        String route = type;

        // 到期消息按预占的绝对截止时间设置剩余 TTL。
        if ("ORDER_DEADLINE".equals(type)) {
            long reservationId = ((JsonNode) payload).path("reservationId").asLong();
            LocalDateTime expiry = db.findReservationExpiry(reservationId);
            long remaining = Math.max(1,
                    Duration.between(LocalDateTime.now(clock), Objects.requireNonNull(expiry)).toMillis());
            props.setExpiration(Long.toString(remaining));
        }
        Message message = new Message(mapper.writeValueAsBytes(Map.of("eventType", type, "payload", payload)), props);
        return transport.sendReliableEvent(EXCHANGE, route, message, id);
    }

    private void confirm(CorrelationData correlation, long deadline) throws Exception {
        // 发布确认和路由回退都通过，才算真正投递成功。
        long remaining = Math.max(0, deadline - System.nanoTime());
        CorrelationData.Confirm confirm = correlation.getFuture().get(remaining, TimeUnit.NANOSECONDS);
        if (!confirm.isAck() || correlation.getReturned() != null) {
            throw new IllegalStateException("Broker did not confirm routing: " + confirm.getReason());
        }
    }

    private void reschedule(Map<String, Object> row, String id, String token, Exception e) {
        // 发布失败保留事件，记录退避时间供下轮重试。
        int attempts = ((Number) row.get("attempts")).intValue() + 1;
        LocalDateTime failed = LocalDateTime.now(clock);
        String error = Objects.toString(e.getMessage(), e.getClass().getSimpleName());
        db.rescheduleEvent(failed.plusSeconds(Math.min(60, 1L << Math.min(6, attempts))),
                error.substring(0, Math.min(500, error.length())), failed, id, token);
        if (attempts >= 10) {
            log.error("OUTBOX_ALERT eventId={} attempts={} remains durably retryable", id, attempts, e);
        } else {
            log.warn("Outbox publication will retry eventId={}: {}", id, error);
        }
    }

    private record PendingPublication(Map<String, Object> row, String id, CorrelationData correlation) {
    }
}
