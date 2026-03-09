package org.example.ticketmanagement.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.SeckillMessageDTO;
import org.example.ticketmanagement.service.StockService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 抢票死信队列消费者
 * 作用：处理 MQ 消费失败的异常消息
 * 触发场景：
 * 1. SeckillOrderConsumer 宕机，无法消费消息
 * 2. 消息积压超过 TTL（1 分钟）未被消费
 * 3. Consumer 处理失败且多次重试后仍然失败
 * ========================================
 * 职责：
 * 1. 恢复 Redis 库存
 * 2. 删除用户抢票记录
 * 3. 记录补偿日志，便于后续排查
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = "seckill.order.dead.queue")
public class SeckillDeadLetterConsumer {

    private final StockService stockService;

    /**
     * 处理死信消息
     *
     * @param message 抢票消息
     * @param channel RabbitMQ Channel
     * @param amqpMessage AMQP 消息对象
     */
    @RabbitHandler
    public void handleDeadLetter(SeckillMessageDTO message,
                                 Channel channel,
                                 Message amqpMessage) {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();

        log.error("收到抢票死信消息：orderNo={}, userId={}, tierId={}, quantity={}",
                message.getOrderNo(), message.getUserId(),
                message.getTierId(), message.getQuantity());

        try {
            // 1.恢复 Redis 库存
            stockService.restoreStock(message.getTierId(), message.getQuantity());
            log.info("✅ 恢复 Redis 库存成功：tierId={}, quantity={}",
                    message.getTierId(), message.getQuantity());

            // 2. 删除用户抢票记录（解除限购）
            stockService.deleteUserSeckillRecord(message.getUserId(), message.getTierId());
            log.info("删除用户抢票记录：userId={}, tierId={}",
                    message.getUserId(), message.getTierId());

            // 3. 手动确认消息（从死信队列移除）
            channel.basicAck(deliveryTag, false);
            log.info("死信消息处理完成：orderNo={}", message.getOrderNo());

        } catch (Exception e) {
            log.error("处理死信消息异常：orderNo={}, userId={}",
                    message.getOrderNo(), message.getUserId(), e);

            try {
                // 判断是否已经重试过
                boolean isRedelivered = amqpMessage.getMessageProperties().getRedelivered();

                if (isRedelivered) {
                    // 已经重试过，仍然失败，直接丢弃消息
                    channel.basicReject(deliveryTag, false);
                    log.error("消息已重新投递，仍然失败，已丢弃：orderNo={}",
                            message.getOrderNo());

                    // TODO: 可以记录到数据库补偿表，人工介入
                    // compensationService.recordFailedSeckill(message);

                } else {
                    // 第一次处理失败，重新入队重试
                    channel.basicReject(deliveryTag, true);
                    log.warn("消息处理失败，将重新入队：orderNo={}",
                            message.getOrderNo());
                }

            } catch (IOException ex) {
                log.error("拒绝消息失败：orderNo={}", message.getOrderNo(), ex);
            }
        }
    }
}
