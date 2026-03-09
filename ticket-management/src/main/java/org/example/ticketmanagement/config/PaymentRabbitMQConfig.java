package org.example.ticketmanagement.config;

import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.PaymentConstant;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付RabbitMQ配置类，简化配置，只保留超时队列（使用TTL）
 */
@Slf4j
@Configuration
public class PaymentRabbitMQConfig {

    /**
     * 支付超时队列（TTL队列，30分钟后消息被消费）
     */
    @Bean
    public Queue paymentTimeoutQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", PaymentConstant.PAYMENT_TIMEOUT_DELAY); // 30分钟
        return QueueBuilder.durable(PaymentConstant.PAYMENT_TIMEOUT_QUEUE)
                .withArguments(args)
                .build();
    }

    /**
     * 支付超时交换机（Direct类型）
     */
    @Bean
    public DirectExchange paymentTimeoutExchange() {
        return new DirectExchange(PaymentConstant.PAYMENT_TIMEOUT_EXCHANGE, true, false);
    }

    /**
     * 绑定超时队列到交换机
     */
    @Bean
    public Binding paymentTimeoutBinding() {
        return BindingBuilder.bind(paymentTimeoutQueue())
                .to(paymentTimeoutExchange())
                .with(PaymentConstant.PAYMENT_TIMEOUT_ROUTING_KEY);
    }

    /**
     * 支付成功通知队列（保持不变）
     */
    @Bean
    public Queue paymentSuccessQueue() {
        return QueueBuilder.durable(PaymentConstant.PAYMENT_SUCCESS_QUEUE).build();
    }

    @Bean
    public DirectExchange paymentSuccessExchange() {
        return new DirectExchange(PaymentConstant.PAYMENT_SUCCESS_EXCHANGE, true, false);
    }

    @Bean
    public Binding paymentSuccessBinding() {
        return BindingBuilder.bind(paymentSuccessQueue())
                .to(paymentSuccessExchange())
                .with(PaymentConstant.PAYMENT_SUCCESS_ROUTING_KEY);
    }

    // 注：支付补偿队列不再需要，因为补偿由定时任务处理
}