package org.example.ticketmanagement.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.trade.messaging.DeadlineHandler;
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
public class PaymentTimeoutConsumer {
    private final DeadlineHandler deadlines;
    private final TradeMessageSupport messages;

    /** 只监听到期处理队列，提前触发仍按原截止时间重新调度。 */
    @RabbitListener(queues = "trade.deadline.ready.v3", containerFactory = "rabbitListenerContainerFactory")
    public void handlePaymentTimeout(Message message, Channel channel) throws IOException {
        messages.consume(message, channel, Set.of("ORDER_DEADLINE"), event -> {
            long id = event.payload().path("reservationId").asLong();
            deadlines.handle(id);
            deadlines.cancelIfExpired(id);
        });
    }
}
