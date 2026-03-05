// PaymentTimeoutConsumer.java
package org.example.ticketmanagement.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.PaymentConstant;
import org.example.ticketmanagement.dto.PaymentTimeoutMessageDTO;
import org.example.ticketmanagement.mapper.PaymentRecordMapper;
import org.example.ticketmanagement.pojo.PaymentRecord;
import org.example.ticketmanagement.service.OrderService;
import org.example.ticketmanagement.service.PaymentService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = PaymentConstant.PAYMENT_TIMEOUT_QUEUE) // 直接监听超时队列
public class PaymentTimeoutConsumer {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PaymentRecordMapper paymentRecordMapper;

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
                log.info("支付记录已处理，跳过超时处理: paymentNo={}, status={}",
                        message.getPaymentNo(), paymentRecord.getPayStatus());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 3. 检查是否过期
            if (paymentRecord.isExpired()) {
                log.info("支付订单已过期，准备关闭支付记录并取消订单: paymentNo={}", message.getPaymentNo());

                // 3.1 关闭支付记录（调用支付宝并更新本地）
                boolean closeSuccess = paymentService.closePayment(message.getPaymentNo());
                if (closeSuccess) {
                    log.info("支付记录关闭成功: paymentNo={}", message.getPaymentNo());

                    // 3.2 取消订单（订单服务会恢复库存）
                    boolean cancelSuccess = orderService.cancelOrderByOrderNo(paymentRecord.getOrderNo());
                    if (cancelSuccess) {
                        log.info("订单取消成功: orderNo={}", paymentRecord.getOrderNo());
                    } else {
                        log.error("订单取消失败: orderNo={}", paymentRecord.getOrderNo());
                        // 可考虑重试或记录失败
                    }
                } else {
                    log.error("支付记录关闭失败: paymentNo={}", message.getPaymentNo());
                    // 关闭失败，可重试（重新入队）
                    channel.basicReject(deliveryTag, true);
                    return;
                }
            } else {
                // 未过期，忽略（理论上不会发生，因为队列TTL保证）
                log.info("支付订单未过期，忽略: paymentNo={}, expireTime={}",
                        message.getPaymentNo(), paymentRecord.getExpireTime());
            }

            // 4. 确认消息
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("处理支付超时消息异常: paymentNo={}", message.getPaymentNo(), e);
            try {
                channel.basicReject(deliveryTag, true); // 重新入队重试
            } catch (IOException ex) {
                log.error("拒绝消息失败", ex);
            }
        }
    }
}