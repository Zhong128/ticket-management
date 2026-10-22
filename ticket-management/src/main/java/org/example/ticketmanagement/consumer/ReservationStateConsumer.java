package org.example.ticketmanagement.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.pojo.StockReservation;
import org.example.ticketmanagement.serviceimpl.ReservationServiceImpl;
import org.example.ticketmanagement.serviceimpl.StockServiceImpl;
import org.example.ticketmanagement.trade.messaging.StockProjection;
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
public class ReservationStateConsumer {
    private final ReservationServiceImpl reservations;
    private final StockServiceImpl stock;
    private final StockProjection projection;
    private final TradeMessageSupport messages;

    /** 展示投影与抢票配额分开；乱序确认也必须服从数据库终态。 */
    @RabbitListener(queues = "trade.stock.v3", containerFactory = "rabbitListenerContainerFactory")
    public void handleStock(Message message, Channel channel) throws IOException {
        messages.consume(message, channel, Set.of("RESERVATION_ACCEPTED", "RESERVATION_RELEASED",
                "STOCK_CHANGED", "CACHE_REBUILD"), event -> {
            var payload = event.payload();
            if ("STOCK_CHANGED".equals(event.type())) {
                projection.apply(payload.path("tierId").asLong(), payload.path("version").asLong(),
                        payload.path("stock").asInt());
                return;
            }
            if ("CACHE_REBUILD".equals(event.type())) {
                stock.rebuild(payload.path("tierId").asLong());
                return;
            }
            StockReservation row = reservations.find(payload.path("reservationId").asLong());
            if (row == null) {
                throw new IllegalArgumentException("申请不存在，无法同步缓存");
            }
            if ("RELEASED".equals(row.getStatus())) {
                stock.release(stock.from(row));
            } else {
                stock.confirm(stock.from(row));
            }
        });
    }
}
