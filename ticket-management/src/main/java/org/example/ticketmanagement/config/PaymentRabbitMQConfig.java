package org.example.ticketmanagement.config;

import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.PaymentConstant;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付模块RabbitMQ配置类
 */
@Slf4j
@Configuration
public class PaymentRabbitMQConfig {

    /**
     * 1. 支付成功交换机（Direct类型）
     */
    @Bean
    public DirectExchange paymentSuccessExchange() {
        return new DirectExchange(PaymentConstant.PAYMENT_SUCCESS_EXCHANGE);
    }

    /**
     * 2. 支付成功队列
     */
    @Bean
    public Queue paymentSuccessQueue() {
        return QueueBuilder.durable(PaymentConstant.PAYMENT_SUCCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", "dlx.exchange") // 可选：死信交换机
                .build();
    }

    /**
     * 3. 绑定支付成功队列到交换机
     */
    @Bean
    public Binding paymentSuccessBinding() {
        return BindingBuilder.bind(paymentSuccessQueue())
                .to(paymentSuccessExchange())
                .with(PaymentConstant.PAYMENT_SUCCESS_ROUTING_KEY);
    }

    /**
     * 4. 支付超时延迟队列（基于TTL + 死信实现）
     * 用于30分钟后检查支付状态
     */
    @Bean
    public Queue paymentTimeoutQueue() {
        Map<String, Object> args = new HashMap<>();
        // 设置消息TTL为30分钟
        args.put("x-message-ttl", PaymentConstant.PAYMENT_TIMEOUT_DELAY);
        // 设置死信交换机（消息过期后转到此交换机）
        args.put("x-dead-letter-exchange", PaymentConstant.PAYMENT_COMPENSATION_EXCHANGE);
        // 设置死信路由键
        args.put("x-dead-letter-routing-key", PaymentConstant.PAYMENT_COMPENSATION_ROUTING_KEY);

        return QueueBuilder.durable(PaymentConstant.PAYMENT_TIMEOUT_QUEUE)
                .withArguments(args)
                .build();
    }

    /**
     * 5. 支付超时交换机
     */
    @Bean
    public DirectExchange paymentTimeoutExchange() {
        return new DirectExchange(PaymentConstant.PAYMENT_TIMEOUT_EXCHANGE);
    }

    /**
     * 6. 绑定支付超时队列到交换机
     */
    @Bean
    public Binding paymentTimeoutBinding() {
        return BindingBuilder.bind(paymentTimeoutQueue())
                .to(paymentTimeoutExchange())
                .with(PaymentConstant.PAYMENT_TIMEOUT_ROUTING_KEY);
    }

    /**
     * 7. 支付补偿交换机
     */
    @Bean
    public DirectExchange paymentCompensationExchange() {
        return new DirectExchange(PaymentConstant.PAYMENT_COMPENSATION_EXCHANGE);
    }

    /**
     * 8. 支付补偿队列（用于处理掉单补偿）
     */
    @Bean
    public Queue paymentCompensationQueue() {
        return QueueBuilder.durable(PaymentConstant.PAYMENT_COMPENSATION_QUEUE)
                .build();
    }

    /**
     * 9. 绑定支付补偿队列到交换机
     */
    @Bean
    public Binding paymentCompensationBinding() {
        return BindingBuilder.bind(paymentCompensationQueue())
                .to(paymentCompensationExchange())
                .with(PaymentConstant.PAYMENT_COMPENSATION_ROUTING_KEY);
    }

    /**
     * 10. 配置RabbitAdmin（用于管理队列，你已有）
     * 注意：这个Bean通常已经在主RabbitMQ配置中，这里不需要重复创建
     */
}