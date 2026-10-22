package org.example.ticketmanagement.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.trade.messaging.NotificationTasks;
import org.example.ticketmanagement.util.TradeMessageSupport;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trade.enabled", havingValue = "true")
public class OrderNotificationConsumer {
    private final NotificationTasks notifications;
    private final TradeMessageSupport messages;

    /** 订单创建提交后再建立邮件任务，不能预扣成功就发送建单成功邮件。 */
    @RabbitListener(queues = "trade.notify.order.v3", containerFactory = "rabbitListenerContainerFactory")
    public void handleOrderCreated(Message message, Channel channel) throws IOException {
        messages.consume(message, channel, Set.of("ORDER_CREATED"),
                event -> notifications.accept(event.type(), event.payload()));
    }
}
