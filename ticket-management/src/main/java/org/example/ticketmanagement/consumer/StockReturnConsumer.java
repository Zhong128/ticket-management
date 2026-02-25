// org/example/ticketmanagement/consumer/StockReturnConsumer.java
package org.example.ticketmanagement.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.StockReturnMessageDTO;
import org.example.ticketmanagement.service.StockService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockReturnConsumer {

    private final StockService stockService;

    /**
     * 消费库存回退消息
     */
    @RabbitListener(queues = "stock.return.process.queue")
    public void handleStockReturn(StockReturnMessageDTO stockReturnMessage,
                                  Channel channel,
                                  Message message) throws IOException {
        log.info("收到库存回退处理消息: tierId={}, quantity={}, reason={}",
                stockReturnMessage.getTierId(), stockReturnMessage.getQuantity(), stockReturnMessage.getReason());

        try {
            // 根据回退原因处理
            switch (stockReturnMessage.getReason()) {
                case "timeout": // 超时未支付
                case "cancel":  // 用户主动取消
                    // 恢复库存
                    stockService.restoreStock(stockReturnMessage.getTierId(), stockReturnMessage.getQuantity());
                    log.info("恢复库存成功: tierId={}, quantity={}, reason={}",
                            stockReturnMessage.getTierId(), stockReturnMessage.getQuantity(), stockReturnMessage.getReason());
                    break;

                case "error": // 系统错误
                    // 可能需要额外处理，比如记录日志、报警等
                    log.error("系统错误导致库存回退: tierId={}, quantity={}, orderId={}",
                            stockReturnMessage.getTierId(), stockReturnMessage.getQuantity(), stockReturnMessage.getOrderId());
                    break;

                default:
                    log.warn("未知的库存回退原因: reason={}", stockReturnMessage.getReason());
            }

            // 手动确认消息
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

        } catch (Exception e) {
            log.error("处理库存回退消息失败: tierId={}", stockReturnMessage.getTierId(), e);

            // 处理失败，拒绝消息并重新入队
            if (message.getMessageProperties().getRedelivered()) {
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            } else {
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), true);
            }
        }
    }
}