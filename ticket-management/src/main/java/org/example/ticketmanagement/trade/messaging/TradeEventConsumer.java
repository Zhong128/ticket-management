package org.example.ticketmanagement.trade.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.trade.core.TradeOperations;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trade.enabled", havingValue = "true")
public class TradeEventConsumer {

    private final ObjectMapper mapper;
    private final TradeOperations core;
    private final DeadlineHandler deadlines;
    private final StockProjection stocks;
    private final NotificationTasks notifications;

    @RabbitListener(queues = { "trade.work.v2", "trade.deadline.ready.v2" })
    public void consume(Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        final ValidatedEvent event;

        // 1. 先校验事件格式，非法消息进入失败队列供排查。
        try {
            JsonNode envelope = mapper.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(message.getBody());
            event = validate(envelope);
        } catch (IOException | IllegalArgumentException malformed) {
            log.error("Invalid trade event retained in failure queue eventId={}: {}",
                    message.getMessageProperties().getHeader("eventId"), malformed.getMessage());
            channel.basicReject(tag, false);
            return;
        }

        // 2. 按事件类型处理业务，成功后手动确认消息。
        try {
            JsonNode payload = event.payload();
            switch (event.type()) {
            case "CREATE_ORDER" -> {
                // 创建失败写入持久化重试次数，由 Outbox 再次调度。
                long id = payload.get("reservationId").longValue();
                try {
                    core.createOrder(id);
                } catch (Exception failure) {
                    core.creationFailed(id, failure.getMessage());
                }
            }
            case "ORDER_DEADLINE" -> {
                long id = payload.get("reservationId").longValue();
                deadlines.handle(id);
                deadlines.cancelIfExpired(id);
            }
            case "STOCK_CHANGED" -> {
                // 缓存更新失败不影响 MySQL 库存，由定时投影修复。
                try {
                    stocks.apply(payload.get("tierId").longValue(), payload.get("version").longValue(),
                            payload.get("stock").intValue());
                } catch (Exception failure) {
                    log.warn("Stock cache unavailable; scheduled DB projection will repair tier={}",
                            payload.get("tierId"), failure);
                }
            }
            case "ORDER_CREATED", "PAYMENT_SUCCESS" -> notifications.accept(event.type(), payload);
            default -> throw new IllegalStateException("Validated trade event has no handler");
            }
            channel.basicAck(tag, false);
        } catch (Exception failure) {
            // 瞬时处理失败重新入队，等待下一次消费。
            log.error("Trade event handling failed eventId={}", message.getMessageProperties().getHeader("eventId"),
                    failure);
            channel.basicNack(tag, false, true);
        }
    }

    private ValidatedEvent validate(JsonNode envelope) {
        if (envelope == null || !envelope.isObject() || !envelope.path("eventType").isTextual()
                || !envelope.path("payload").isObject()) {
            throw new IllegalArgumentException("Expected an object containing textual eventType and object payload");
        }
        String type = envelope.get("eventType").textValue();
        JsonNode payload = envelope.get("payload");
        switch (type) {
        case "CREATE_ORDER", "ORDER_DEADLINE", "ORDER_CREATED" -> requirePositiveLong(payload, "reservationId");
        case "STOCK_CHANGED" -> {
            requirePositiveLong(payload, "tierId");
            requireNonnegativeLong(payload, "version");
            JsonNode stock = payload.path("stock");
            if (!stock.isIntegralNumber() || !stock.canConvertToInt() || stock.intValue() < 0) {
                throw new IllegalArgumentException("stock must be a nonnegative integer");
            }
        }
        case "PAYMENT_SUCCESS" -> {
            JsonNode no = payload.path("paymentNo");
            if (!no.isTextual() || no.textValue().isBlank() || no.textValue().length() > 64) {
                throw new IllegalArgumentException("paymentNo must be a nonblank string of at most 64 characters");
            }
        }
        default -> throw new IllegalArgumentException("Unknown trade event type");
        }
        return new ValidatedEvent(type, payload);
    }

    private void requirePositiveLong(JsonNode payload, String field) {
        requireNonnegativeLong(payload, field);
        if (payload.get(field).longValue() == 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private void requireNonnegativeLong(JsonNode payload, String field) {
        JsonNode value = payload.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw new IllegalArgumentException(field + " must be a nonnegative 64-bit integer");
        }
    }

    private record ValidatedEvent(String type, JsonNode payload) {
    }
}
