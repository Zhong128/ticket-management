package org.example.ticketmanagement.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.constant.PaymentConstant;
import org.example.ticketmanagement.dto.PaymentTimeoutMessageDTO;
import org.example.ticketmanagement.trade.messaging.LegacyMessageAdapter;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class PaymentTimeoutConsumer {

    private final LegacyMessageAdapter legacy;

    @RabbitListener(queues = PaymentConstant.PAYMENT_TIMEOUT_QUEUE, autoStartup = "${trade.legacy-consumers-enabled:false}")
    public void handlePaymentTimeout(PaymentTimeoutMessageDTO dto, Channel channel, Message message)
            throws IOException {
        try {
            legacy.deadline(dto.getOrderNo());
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
        }
    }
}
