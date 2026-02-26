// org/example/ticketmanagement/consumer/PaymentTimeoutConsumer.java
package org.example.ticketmanagement.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.PaymentConstant;
import org.example.ticketmanagement.dto.PaymentTimeoutMessageDTO;
import org.example.ticketmanagement.mapper.PaymentRecordMapper;
import org.example.ticketmanagement.pojo.PaymentRecord;
import org.example.ticketmanagement.service.PaymentService;
import org.example.ticketmanagement.util.RabbitMQUtil;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = PaymentConstant.PAYMENT_COMPENSATION_QUEUE)
public class PaymentTimeoutConsumer {

    private final PaymentService paymentService;
    private final PaymentRecordMapper paymentRecordMapper;
    private final RabbitMQUtil rabbitMQUtil;

    @RabbitHandler
    public void handlePaymentTimeout(PaymentTimeoutMessageDTO message, Channel channel, Message amqpMessage) {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        log.info("收到支付超时消息: paymentNo={}, orderNo={}", message.getPaymentNo(), message.getOrderNo());

        try {
            // 1. 查询支付记录
            PaymentRecord paymentRecord = paymentRecordMapper.selectByPaymentNo(message.getPaymentNo());

            if (paymentRecord == null) {
                log.warn("支付记录不存在: paymentNo={}", message.getPaymentNo());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 2. 如果已经支付成功或已关闭，直接确认
            if (paymentRecord.getPayStatus().equals(PaymentConstant.PAY_STATUS_SUCCESS) ||
                    paymentRecord.getPayStatus().equals(PaymentConstant.PAY_STATUS_CLOSED)) {
                log.info("支付记录已处理，跳过超时检查: paymentNo={}, status={}",
                        message.getPaymentNo(), paymentRecord.getPayStatus());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 3. 如果已过期，主动关闭
            if (paymentRecord.isExpired()) {
                log.info("支付订单已过期，准备关闭: paymentNo={}", message.getPaymentNo());
                boolean success = paymentService.closePayment(message.getPaymentNo());

                if (success) {
                    log.info("超时关闭订单成功: paymentNo={}", message.getPaymentNo());
                } else {
                    log.error("超时关闭订单失败: paymentNo={}", message.getPaymentNo());
                }
            } else {
                // 4. 未过期但收到了消息（可能是网络延迟），重新计算剩余时间并发送延迟消息
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime expireTime = paymentRecord.getExpireTime();

                // 计算剩余过期时间（毫秒）
                long remainingMillis = Duration.between(now, expireTime).toMillis();

                if (remainingMillis > 0) {
                    log.info("支付订单未过期，重新发送超时检查: paymentNo={}, 剩余时间={}ms, expireTime={}",
                            message.getPaymentNo(), remainingMillis, expireTime);

                    // 创建新的超时消息
                    PaymentTimeoutMessageDTO newMessage = new PaymentTimeoutMessageDTO();
                    newMessage.setPaymentNo(paymentRecord.getPaymentNo());
                    newMessage.setOrderNo(paymentRecord.getOrderNo());
                    newMessage.setOrderId(paymentRecord.getOrderId());
                    newMessage.setUserId(paymentRecord.getUserId());
                    newMessage.setTimestamp(System.currentTimeMillis());

                    // 使用 RabbitMQUtil 发送TTL延迟消息
                    // 注意：这里发送到超时队列，不是直接发送到补偿队列
                    rabbitMQUtil.sendTtlDelayedMessage(
                            PaymentConstant.PAYMENT_TIMEOUT_QUEUE,  // 发送到超时队列，而不是补偿队列
                            newMessage,
                            (int) remainingMillis  // 剩余时间作为TTL
                    );

                    log.debug("重新发送超时检查消息: paymentNo={}, 新TTL={}ms",
                            message.getPaymentNo(), remainingMillis);
                } else {
                    // 如果剩余时间<=0，说明已经过期，直接关闭
                    log.info("支付订单已过期（计算剩余时间<=0），准备关闭: paymentNo={}", message.getPaymentNo());
                    boolean success = paymentService.closePayment(message.getPaymentNo());

                    if (success) {
                        log.info("超时关闭订单成功: paymentNo={}", message.getPaymentNo());
                    } else {
                        log.error("超时关闭订单失败: paymentNo={}", message.getPaymentNo());
                    }
                }
            }

            // 5. 确认当前消息处理完成
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("处理支付超时消息异常: paymentNo={}", message.getPaymentNo(), e);
            try {
                // 异常情况，重新入队重试
                channel.basicReject(deliveryTag, true);
            } catch (IOException ex) {
                log.error("拒绝消息失败", ex);
            }
        }
    }
}