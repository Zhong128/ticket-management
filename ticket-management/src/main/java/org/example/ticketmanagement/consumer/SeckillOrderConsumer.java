package org.example.ticketmanagement.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.exception.StockCacheNotReadyException;
import org.example.ticketmanagement.exception.StockVersionConflictException;
import org.example.ticketmanagement.service.ReservationService;
import org.example.ticketmanagement.service.OrderService;
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
public class SeckillOrderConsumer {
    private final ReservationService orders;
    private final OrderService orderService;
    private final TradeMessageSupport messages;

    /** 业务提交或持久化重试后才确认；缓存暂停不累计建单失败。 */
    @RabbitListener(queues = "trade.order.v3", containerFactory = "rabbitListenerContainerFactory")
    public void handleSeckillOrder(Message message, Channel channel) throws IOException {
        messages.consume(message, channel, Set.of("CREATE_ORDER"), event -> {
            long id = event.payload().path("reservationId").asLong();
            try {
                orderService.createOrder(id);
            } catch (StockCacheNotReadyException | StockVersionConflictException paused) {
                throw paused;
            } catch (RuntimeException failed) {
                orders.creationFailed(id, failed.getMessage());
            }
        });
    }
}
