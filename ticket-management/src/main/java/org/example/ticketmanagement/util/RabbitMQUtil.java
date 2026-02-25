
package org.example.ticketmanagement.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.OrderConstant;
import org.example.ticketmanagement.constant.SeckillConstant;
import org.example.ticketmanagement.dto.SeckillMessageDTO;
import org.example.ticketmanagement.dto.StockReturnMessageDTO;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQUtil {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送抢票订单消息
     */
    public void sendSeckillOrderMessage(SeckillMessageDTO message) {
        try {
            String correlationId = UUID.randomUUID().toString();

            rabbitTemplate.convertAndSend(
                    SeckillConstant.SECKILL_ORDER_EXCHANGE,
                    SeckillConstant.SECKILL_ORDER_ROUTING_KEY,
                    message,
                    msg -> {
                        // 添加订单号到消息头，方便死信消费者处理
                        msg.getMessageProperties().setHeader("orderNo", message.getOrderNo());
                        msg.getMessageProperties().setCorrelationId(correlationId);
                        // 设置消息持久化
                        msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return msg;
                    }
            );

            log.debug("抢票订单消息发送成功: {}, correlationId: {}", message.getOrderNo(), correlationId);
        } catch (Exception e) {
            log.error("抢票订单消息发送失败: {}", message.getOrderNo(), e);
            throw new RuntimeException("消息发送失败", e);
        }
    }

    /**
     * 发送库存回退延迟消息（用于订单超时自动取消）
     */
    public void sendStockReturnDelayMessage(StockReturnMessageDTO message) {
        try {
            rabbitTemplate.convertAndSend(
                    SeckillConstant.STOCK_RETURN_DELAY_EXCHANGE,
                    SeckillConstant.STOCK_RETURN_DELAY_ROUTING_KEY,
                    message
            );

            log.debug("库存回退延迟消息发送成功: {}, delay={}分钟",
                    message, OrderConstant.ORDER_TIMEOUT_MINUTES);
        } catch (Exception e) {
            log.error("库存回退延迟消息发送失败: {}", message, e);
            throw new RuntimeException("库存回退延迟消息发送失败", e);
        }
    }


    /**
     * 发送TTL延迟消息（基于队列TTL设置）
     */
    public void sendTtlDelayedMessage(String queueName, Object message, int ttl) {
        try {
            rabbitTemplate.convertAndSend(
                    "",
                    queueName,
                    message,
                    msg -> {
                        msg.getMessageProperties().setExpiration(String.valueOf(ttl));
                        return msg;
                    }
            );

            log.debug("TTL延迟消息发送成功: {}, queue: {}, ttl: {}ms", message, queueName, ttl);
        } catch (Exception e) {
            log.error("TTL延迟消息发送失败: {}", message, e);
            throw new RuntimeException("TTL延迟消息发送失败", e);
        }
    }

    /**
     * 通用消息发送方法（带消息头）
     */
    public void convertAndSend(String exchange, String routingKey, Object message) {
        try {
            String correlationId = UUID.randomUUID().toString();

            rabbitTemplate.convertAndSend(
                    exchange,
                    routingKey,
                    message,
                    msg -> {
                        msg.getMessageProperties().setCorrelationId(correlationId);
                        msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        msg.getMessageProperties().setHeader("timestamp", System.currentTimeMillis());
                        return msg;
                    }
            );

            log.debug("消息发送成功: exchange={}, routingKey={}, correlationId={}",
                    exchange, routingKey, correlationId);

        } catch (Exception e) {
            log.error("消息发送失败: exchange={}, routingKey={}", exchange, routingKey, e);
            throw new RuntimeException("消息发送失败", e);
        }
    }

    /**
     * 通用消息发送方法（带自定义消息头）
     */
    public void convertAndSend(String exchange, String routingKey, Object message, Map<String, Object> headers) {
        try {
            String correlationId = UUID.randomUUID().toString();

            rabbitTemplate.convertAndSend(
                    exchange,
                    routingKey,
                    message,
                    msg -> {
                        msg.getMessageProperties().setCorrelationId(correlationId);
                        msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        msg.getMessageProperties().setHeader("timestamp", System.currentTimeMillis());

                        // 添加自定义消息头
                        if (headers != null) {
                            headers.forEach((key, value) ->
                                    msg.getMessageProperties().setHeader(key, value)
                            );
                        }

                        return msg;
                    }
            );

            log.debug("消息发送成功: exchange={}, routingKey={}, correlationId={}",
                    exchange, routingKey, correlationId);

        } catch (Exception e) {
            log.error("消息发送失败: exchange={}, routingKey={}", exchange, routingKey, e);
            throw new RuntimeException("消息发送失败", e);
        }
    }
}