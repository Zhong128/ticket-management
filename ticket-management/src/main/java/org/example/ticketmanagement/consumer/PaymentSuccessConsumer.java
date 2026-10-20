package org.example.ticketmanagement.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.constant.PaymentConstant;
import org.example.ticketmanagement.dto.PaymentSuccessMessageDTO;
import org.example.ticketmanagement.trade.messaging.NotificationTasks;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PaymentSuccessConsumer {

    private final ObjectProvider<NotificationTasks> notifications;
    private final ObjectMapper mapper;

    @RabbitListener(queues = PaymentConstant.PAYMENT_SUCCESS_QUEUE, autoStartup = "${trade.legacy-consumers-enabled:false}")
    public void handlePaymentSuccess(PaymentSuccessMessageDTO dto, Channel channel, Message message)
            throws IOException {
        try {
            notifications.getObject().accept("PAYMENT_SUCCESS",
                    mapper.valueToTree(Map.of("paymentNo", dto.getPaymentNo())));
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
        }
    }
}
