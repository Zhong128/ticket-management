package org.example.ticketmanagement.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.dto.SeckillMessageDTO;
import org.example.ticketmanagement.trade.messaging.LegacyMessageAdapter;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class SeckillDeadLetterConsumer {

    private final LegacyMessageAdapter legacy;

    @RabbitListener(queues = "seckill.order.dead.queue", autoStartup = "${trade.legacy-consumers-enabled:false}")
    public void handleDeadLetter(SeckillMessageDTO dto, Channel channel, Message message) throws IOException {
        try {
            legacy.deadline(dto.getOrderNo());
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
        }
    }
}
