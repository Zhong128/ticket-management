// org/example/ticketmanagement/consumer/SeckillOrderConsumer.java
package org.example.ticketmanagement.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.SeckillMessageDTO;
import org.example.ticketmanagement.service.OrderService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final OrderService orderService;

    /**
     * 消费抢票订单消息
     */
    @RabbitListener(queues = "${seckill.config.order-queue:seckill.order.queue}",
            containerFactory = "rabbitListenerContainerFactory")
    public void handleSeckillOrder(SeckillMessageDTO seckillMessage,
                                   Channel channel,
                                   Message message) throws IOException {
        log.info("收到抢票订单消息: orderNo={}, userId={}, tierId={}",
                seckillMessage.getOrderNo(), seckillMessage.getUserId(), seckillMessage.getTierId());

        try {
            // 1. 创建订单
            Long orderId = orderService.createOrder(seckillMessage);

            // 2. 手动确认消息
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

            log.info("订单处理成功: orderId={}, orderNo={}", orderId, seckillMessage.getOrderNo());

        } catch (Exception e) {
            log.error("处理抢票订单消息失败: orderNo={}", seckillMessage.getOrderNo(), e);

            // 3. 处理失败，拒绝消息并重新入队（最多重试3次）
            if (message.getMessageProperties().getRedelivered()) {
                // 已经重试过，仍然失败，拒绝并不再重新入队
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
                log.error("订单处理重试后仍然失败，消息已丢弃: orderNo={}", seckillMessage.getOrderNo());
            } else {
                // 第一次失败，重新入队重试
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), true);
                log.warn("订单处理失败，消息已重新入队: orderNo={}", seckillMessage.getOrderNo());
            }
        }
    }

    /**
     * 消费死信队列消息（处理超时未支付订单）
     */
    @RabbitListener(queues = "${seckill.config.dead-letter-queue:seckill.order.dead.queue}")
    public void handleDeadLetterOrder(String orderNo, Channel channel, Message message) throws IOException {
        log.info("收到死信队列消息，处理超时订单: orderNo={}", orderNo);

        try {
            // 这里可以调用订单服务取消订单
            // 注意：实际项目中，死信队列应该触发订单取消逻辑

            // 手动确认消息
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

            log.info("超时订单处理成功: orderNo={}", orderNo);

        } catch (Exception e) {
            log.error("处理超时订单失败: orderNo={}", orderNo, e);
            // 处理失败，拒绝消息（不重新入队）
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
        }
    }
}