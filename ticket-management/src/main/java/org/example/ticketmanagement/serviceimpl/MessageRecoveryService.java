package org.example.ticketmanagement.serviceimpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.mapper.ConsumerRetryMapper;
import org.example.ticketmanagement.trade.messaging.OutboxStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Base64;
import java.util.UUID;
import org.springframework.amqp.core.Message;

/** 消费失败先写有界退避任务，再ACK；数据库不可用时不确认消息。 */
@Service
@RequiredArgsConstructor
public class MessageRecoveryService {
    private final ConsumerRetryMapper mapper;
    private final OutboxStore outbox;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public void retry(String root, String type, JsonNode payload) {
        transactions.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now(clock);
            mapper.init(root, json.createObjectNode().put("eventType", type).set("payload", payload).toString(), now);
            Map<String, Object> task = mapper.lock(root);
            if (((Number) task.get("task_status")).intValue() == 3) {
                return;
            }
            int attempt = ((Number) task.get("retry_count")).intValue() + 1;
            LocalDateTime next = now.plusSeconds(Math.min(60, 1L << Math.min(6, attempt)));
            mapper.failed(root, attempt, attempt >= 10 ? 3 : 0, next, now);
            if (attempt < 10) {
                ObjectNode retry = ((ObjectNode) payload).deepCopy();
                retry.put("retryRoot", root);
                outbox.enqueueAt(type, "consumer-retry:" + root + ":" + attempt, retry, next);
            }
        });
    }

    public void completed(String root) {
        mapper.completed(root, LocalDateTime.now(clock));
    }

    /** 非法消息不能解析为业务事件时，原始字节仍持久保留供人工核查。 */
    public void quarantine(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        String root = messageId == null || messageId.length() > 64 ? UUID.randomUUID().toString() : messageId;
        transactions.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now(clock);
            String payload = json.createObjectNode().put("rawBase64",
                    Base64.getEncoder().encodeToString(message.getBody())).toString();
            mapper.init(root, payload, now);
            mapper.lock(root);
            mapper.failed(root, 10, 3, now, now);
        });
    }
}
