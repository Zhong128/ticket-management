package org.example.ticketmanagement.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.PaymentConstant;
import org.example.ticketmanagement.dto.PaymentSuccessMessageDTO;
import org.example.ticketmanagement.mapper.OrderInfoMapper;
import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.mapper.EventMapper;
import org.example.ticketmanagement.mapper.EventSessionMapper;
import org.example.ticketmanagement.mapper.TicketTierMapper;
import org.example.ticketmanagement.pojo.OrderInfo;
import org.example.ticketmanagement.pojo.User;
import org.example.ticketmanagement.pojo.Event;
import org.example.ticketmanagement.pojo.EventSession;
import org.example.ticketmanagement.pojo.TicketTier;
import org.example.ticketmanagement.service.EmailService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = PaymentConstant.PAYMENT_SUCCESS_QUEUE)
public class PaymentSuccessConsumer {

    private final EmailService emailService;
    private final OrderInfoMapper orderInfoMapper;
    private final UserMapper userMapper;
    private final EventMapper eventMapper;
    private final EventSessionMapper eventSessionMapper;
    private final TicketTierMapper ticketTierMapper;

    @RabbitHandler
    public void handlePaymentSuccess(PaymentSuccessMessageDTO message, Channel channel, Message amqpMessage) {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        log.info("收到支付成功消息：paymentNo={}, orderNo={}, userId={}",
                message.getPaymentNo(), message.getOrderNo(), message.getUserId());

        try {
            // 1. 如果消息中信息不全，从数据库查询补充
            if (message.getUserEmail() == null || message.getEventName() == null) {
                enrichMessageFromDatabase(message);
            }

            // 2. 检查邮箱是否存在
            if (message.getUserEmail() == null || message.getUserEmail().isEmpty()) {
                log.warn("用户邮箱为空，无法发送邮件：paymentNo={}", message.getPaymentNo());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 3. 调用 EmailService 发送邮件
            boolean emailSent = emailService.sendPaymentSuccessEmail(
                message.getUserEmail(),
                message.getPaymentNo(),
                message.getTradeNo(),
                message.getOrderNo(),
                message.getTotalAmount(),
                message.getPayTime(),
                message.getEventName(),
                message.getSessionName(),
                message.getTierName(),
                message.getQuantity()
            );

            if (emailSent) {
                log.info("支付成功邮件发送成功：paymentNo={}, email={}",
                        message.getPaymentNo(), message.getUserEmail());
                // 确认消息
                channel.basicAck(deliveryTag, false);
            } else {
                log.error("支付成功邮件发送失败：paymentNo={}", message.getPaymentNo());
                // 重试或进入死信队列
                handleFailedMessage(message, channel, deliveryTag, amqpMessage);
            }

        } catch (Exception e) {
            log.error("处理支付成功消息异常：paymentNo={}", message.getPaymentNo(), e);
            handleFailedMessage(message, channel, deliveryTag, amqpMessage);
        }
    }

    /**
     * 从数据库补充消息信息
     */
    private void enrichMessageFromDatabase(PaymentSuccessMessageDTO message) {
        try {
            // 查询订单信息
            OrderInfo orderInfo = orderInfoMapper.selectById(message.getOrderId());
            if (orderInfo == null) {
                log.warn("订单不存在：orderId={}", message.getOrderId());
                return;
            }

            // 查询用户信息
            if (message.getUserEmail() == null) {
                User user = userMapper.getUserById(message.getUserId());
                if (user != null) {
                    message.setUserEmail(user.getEmail());
                }
            }

            // 查询演出信息
            if (message.getEventName() == null) {
                Event event = eventMapper.selectById(orderInfo.getEventId());
                if (event != null) {
                    message.setEventName(event.getName());
                }
            }

            // 查询场次信息
            if (message.getSessionName() == null) {
                EventSession session = eventSessionMapper.selectById(orderInfo.getSessionId());
                if (session != null) {
                    message.setSessionName(session.getSessionName());
                }
            }

            // 查询票档信息
            if (message.getTierName() == null) {
                TicketTier tier = ticketTierMapper.selectById(orderInfo.getTierId());
                if (tier != null) {
                    message.setTierName(tier.getTierName());
                }
            }

            // 补充数量
            if (message.getQuantity() == null) {
                message.setQuantity(orderInfo.getQuantity());
            }

        } catch (Exception e) {
            log.error("补充消息信息失败：paymentNo={}", message.getPaymentNo(), e);
        }
    }

    /**
     * 处理失败消息
     */
    private void handleFailedMessage(PaymentSuccessMessageDTO message, Channel channel,
                                     long deliveryTag, Message amqpMessage) {
        try {
            // 获取重试次数
            Integer retryCount = (Integer) amqpMessage.getMessageProperties()
                    .getHeaders().getOrDefault("retry-count", 0);

            if (retryCount < 3) {
                // 未超过最大重试次数，重新入队
                log.warn("支付成功消息处理失败，准备重试：paymentNo={}, retryCount={}",
                        message.getPaymentNo(), retryCount + 1);

                // 增加重试次数
                amqpMessage.getMessageProperties().setHeader("retry-count", retryCount + 1);
                channel.basicReject(deliveryTag, true);
            } else {
                // 超过最大重试次数，进入死信队列
                log.error("支付成功消息超过最大重试次数，进入死信：paymentNo={}", message.getPaymentNo());
                channel.basicReject(deliveryTag, false);

                // 可以记录到数据库，人工干预
                recordFailedMessage(message);
            }
        } catch (IOException e) {
            log.error("处理失败消息异常：paymentNo={}", message.getPaymentNo(), e);
        }
    }

    /**
     * 记录失败消息
     */
    private void recordFailedMessage(PaymentSuccessMessageDTO message) {
        // TODO: 可以将失败的消息记录到数据库，方便人工处理
        log.warn("需要人工处理的失败消息：paymentNo={}, orderNo={}",
                message.getPaymentNo(), message.getOrderNo());
    }
}