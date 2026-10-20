package org.example.ticketmanagement.trade.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.mapper.TradeOutboxMapper;
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
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trade.enabled", havingValue = "true")
public class OutboxPublisher {

    public static final String EXCHANGE = "trade.events.v2";
    private final TradeOutboxMapper db;
    private final ObjectMapper mapper;
    private final RabbitTemplate rabbit;
    private final Clock clock;

    @Scheduled(fixedDelay = 1000)
    public void publishDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Map<String, Object>> rows = db.findDueEvents(now, now);

        // 逐条领取租约，避免多个发布实例重复处理同一事件。
        for (Map<String, Object> row : rows) {
            now = LocalDateTime.now(clock);
            String id = (String) row.get("event_id");
            String token = UUID.randomUUID().toString();
            if (db.claimEvent(token, now.plusSeconds(30), now, id, now, now) != 1) {
                continue;
            }

            try {
                // 收到 Broker 确认后，才将 Outbox 标为已发布。
                publish(row, id);
                db.markPublished(LocalDateTime.now(clock), id, token);
            } catch (Exception e) {
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
        }
    }

    private void publish(Map<String, Object> row, String id) throws Exception {
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
        CorrelationData correlation = new CorrelationData(id);
        rabbit.send(EXCHANGE, route, message, correlation);

        // 发布确认和路由回退都通过，才算真正投递成功。
        CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirm.isAck() || correlation.getReturned() != null) {
            throw new IllegalStateException("Broker did not confirm routing: " + confirm.getReason());
        }
    }
}
