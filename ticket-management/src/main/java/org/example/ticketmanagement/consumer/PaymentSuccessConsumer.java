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
public class PaymentSuccessConsumer {
    private final NotificationTasks notifications;
    private final TradeMessageSupport messages;

    /** 消费者只保存通知任务，网络邮件发送不阻塞支付消息确认。 */
    @RabbitListener(queues = "trade.notify.payment.v3", containerFactory = "rabbitListenerContainerFactory")
    public void handlePaymentSuccess(Message message, Channel channel) throws IOException {
        messages.consume(message, channel, Set.of("PAYMENT_SUCCESS"),
                event -> notifications.accept(event.type(), event.payload()));
    }
}
