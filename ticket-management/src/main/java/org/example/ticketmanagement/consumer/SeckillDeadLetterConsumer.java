package org.example.ticketmanagement.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.serviceimpl.MessageRecoveryService;
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
public class SeckillDeadLetterConsumer {
    private final MessageRecoveryService recovery;
    private final TradeOperations orders;
    private final ObjectMapper json;

    /** 先保留人工排查证据；只有可识别申请才交给统一状态机处理。 */
    @RabbitListener(queues = "trade.order.failed.v3", containerFactory = "rabbitListenerContainerFactory")
    public void handleDeadLetter(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            recovery.quarantine(message);
        } catch (RuntimeException unavailable) {
            channel.basicNack(tag, false, true);
            return;
        }
        try {
            var payload = json.readTree(message.getBody()).path("payload").path("reservationId");
            if (payload.isIntegralNumber() && payload.canConvertToLong() && payload.asLong() > 0) {
                orders.creationFailed(payload.asLong(), "建单死信触发状态核查");
            }
        } catch (Exception invalidOrUnavailable) {
            log.warn("建单死信已保留人工记录，不能直接回补库存: {}", invalidOrUnavailable.getMessage());
        }
        channel.basicAck(tag, false);
    }
}
