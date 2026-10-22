package org.example.ticketmanagement.util;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** 关键消息统一由Outbox调用，返回关联确认对象，不把本地send返回当作投递成功。 */
@Component
@RequiredArgsConstructor
public class RabbitMQUtil {
    private final RabbitTemplate rabbitTemplate;

    public CorrelationData sendReliableEvent(String exchange, String route, Message message, String eventId) {
        CorrelationData correlation = new CorrelationData(eventId);
        rabbitTemplate.send(exchange, route, message, correlation);
        return correlation;
    }
}
