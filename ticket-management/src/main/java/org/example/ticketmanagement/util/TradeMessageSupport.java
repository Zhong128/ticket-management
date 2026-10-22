package org.example.ticketmanagement.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.serviceimpl.MessageRecoveryService;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.function.Consumer;

/** 统一消息格式与确认，业务状态仍由各Service处理。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeMessageSupport {
    private final ObjectMapper json;
    private final MessageRecoveryService recovery;

    public void consume(Message message, Channel channel, Set<String> types, Consumer<Event> handler) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        Event event;
        String root;
        try {
            JsonNode envelope = json.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(message.getBody());
            if (envelope == null || !envelope.isObject() || !envelope.path("eventType").isTextual()
                    || !envelope.path("payload").isObject()) {
                throw new IllegalArgumentException("交易消息格式错误");
            }
            String type = envelope.path("eventType").asText();
            JsonNode payload = envelope.path("payload");
            if (!types.contains(type)) {
                throw new IllegalArgumentException("事件路由与消费者不匹配");
            }
            validate(type, payload);
            String id = message.getMessageProperties().getMessageId();
            root = payload.has("retryRoot") ? payload.path("retryRoot").asText() : id;
            if (root == null || root.isBlank() || root.length() > 64) {
                throw new IllegalArgumentException("缺少有效事件标识");
            }
            event = new Event(type, payload);
        } catch (IOException | IllegalArgumentException invalid) {
            log.warn("非法交易消息保留到失败队列: {}", invalid.getMessage());
            channel.basicReject(tag, false);
            return;
        }

        try {
            handler.accept(event);
            recovery.completed(root);
            channel.basicAck(tag, false);
        } catch (RuntimeException failure) {
            try {
                recovery.retry(root, event.type(), event.payload());
                log.warn("消费失败已保存退避或人工任务 event={}, type={}", root, event.type(), failure);
                channel.basicAck(tag, false);
            } catch (RuntimeException persistenceUnavailable) {
                // 无法持久化恢复任务时保留Broker消息，不能为了结束重试而ACK。
                log.error("消费恢复任务暂不可写入 event={}", root, persistenceUnavailable);
                channel.basicNack(tag, false, true);
            }
        }
    }

    private void validate(String type, JsonNode payload) {
        if ("PAYMENT_SUCCESS".equals(type)) {
            if (!payload.path("paymentNo").isTextual() || payload.path("paymentNo").asText().isBlank()
                    || payload.path("paymentNo").asText().length() > 64) {
                throw new IllegalArgumentException("支付流水号格式错误");
            }
            return;
        }
        String id = Set.of("STOCK_CHANGED", "CACHE_REBUILD").contains(type) ? "tierId" : "reservationId";
        requireLong(payload, id, true);
        if ("STOCK_CHANGED".equals(type)) {
            requireLong(payload, "version", false);
            JsonNode stock = payload.path("stock");
            if (!stock.isIntegralNumber() || !stock.canConvertToInt() || stock.asInt() < 0) {
                throw new IllegalArgumentException("库存格式错误");
            }
        }
    }

    private void requireLong(JsonNode payload, String name, boolean positive) {
        JsonNode value = payload.path(name);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < (positive ? 1 : 0)) {
            throw new IllegalArgumentException("事件ID或版本格式错误");
        }
    }

    public record Event(String type, JsonNode payload) {
    }
}
