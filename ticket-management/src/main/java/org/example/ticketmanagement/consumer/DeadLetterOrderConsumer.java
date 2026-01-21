// org/example/ticketmanagement/consumer/DeadLetterOrderConsumer.java
package org.example.ticketmanagement.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.SeckillConstant;
import org.example.ticketmanagement.service.OrderService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterOrderConsumer {

    private final OrderService orderService;

    /**
     * 处理死信队列中的超时订单
     * 注意：死信队列中的消息格式与原始消息相同
     */
    @RabbitListener(queues = "seckill.order.dead.queue")
    public void handleDeadLetterOrder(Message message, Channel channel) throws IOException {
        String orderNo = extractOrderNoFromMessage(message);
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        log.info("收到死信队列消息，处理超时订单: orderNo={}, deliveryTag={}", orderNo, deliveryTag);

        try {
            // 1. 根据订单号查询订单
            org.example.ticketmanagement.vo.OrderInfoVO order = orderService.getOrderByOrderNo(orderNo);

            if (order == null) {
                log.warn("死信队列中的订单不存在，可能是重复消息: orderNo={}", orderNo);
                channel.basicAck(deliveryTag, false); // 确认消息，防止阻塞
                return;
            }

            // 2. 只处理待支付状态的订单（幂等性校验）
            if (!order.getOrderStatus().equals(SeckillConstant.ORDER_STATUS_PENDING)) {
                log.warn("订单状态不是待支付，无需处理: orderNo={}, status={}",
                        orderNo, order.getOrderStatusDesc());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 3. 取消订单（会自动恢复库存）
            boolean success = orderService.cancelOrderByOrderNo(orderNo);

            if (success) {
                log.info("死信队列成功取消超时订单: orderNo={}, orderId={}", orderNo, order.getId());
                channel.basicAck(deliveryTag, false); // 确认消息
            } else {
                log.error("死信队列取消订单失败: orderNo={}", orderNo);
                // 失败重试（最多3次）
                handleFailedMessage(message, channel, deliveryTag);
            }

        } catch (Exception e) {
            log.error("处理死信队列消息异常: orderNo={}", orderNo, e);
            handleFailedMessage(message, channel, deliveryTag);
        }
    }

    /**
     * 从消息中提取订单号
     * RabbitMQ消息可能是JSON格式的SeckillMessageDTO，我们只关心订单号
     */
    private String extractOrderNoFromMessage(Message message) {
        try {
            // 方法1：如果消息是JSON格式的SeckillMessageDTO
            String messageBody = new String(message.getBody());
            if (messageBody.contains("orderNo")) {
                // 简单解析JSON，获取orderNo字段
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode jsonNode = mapper.readTree(messageBody);
                if (jsonNode.has("orderNo")) {
                    return jsonNode.get("orderNo").asText();
                }
            }

            // 方法2：如果消息头中有订单号
            String orderNoHeader = (String) message.getMessageProperties().getHeaders().get("orderNo");
            if (orderNoHeader != null) {
                return orderNoHeader;
            }

            // 方法3：如果消息体本身就是订单号（字符串）
            return new String(message.getBody());

        } catch (Exception e) {
            log.warn("无法从消息中提取订单号，使用默认值", e);
            return "UNKNOWN_ORDER";
        }
    }

    /**
     * 处理失败的消息（重试或丢弃）
     */
    private void handleFailedMessage(Message message, Channel channel, long deliveryTag) throws IOException {
        // 检查消息是否已经重试过
        Integer retryCount = (Integer) message.getMessageProperties().getHeaders().get("retry-count");
        retryCount = (retryCount == null) ? 0 : retryCount;

        if (retryCount >= 3) {
            // 超过最大重试次数，丢弃消息（记录到数据库或日志）
            log.error("消息重试超过3次，丢弃: deliveryTag={}, retryCount={}", deliveryTag, retryCount);
            channel.basicReject(deliveryTag, false); // 不重新入队

            // 可以记录到数据库，人工干预
            recordFailedMessageForManualReview(message);
        } else {
            // 重新入队重试
            log.warn("消息处理失败，重新入队重试: deliveryTag={}, retryCount={}", deliveryTag, retryCount);

            // 增加重试次数
            message.getMessageProperties().getHeaders().put("retry-count", retryCount + 1);

            // 延迟重试（使用死信队列的TTL实现延迟）
            channel.basicReject(deliveryTag, false);
            // 注意：这里需要重新发布到原队列，设置新的TTL
            // 或者更简单的方式：直接重新入队，等待下一次消费
            channel.basicReject(deliveryTag, true); // true表示重新入队
        }
    }

    /**
     * 记录失败消息，用于人工干预
     */
    private void recordFailedMessageForManualReview(Message message) {
        // 这里可以将失败的消息记录到数据库，方便后续排查
        // 例如：创建一张 failed_message 表
        log.warn("记录失败消息到数据库，需要人工干预: {}", new String(message.getBody()));
    }
}