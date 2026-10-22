package org.example.ticketmanagement.config;

import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.PaymentConstant;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 支付RabbitMQ配置类，简化配置，只保留超时队列（使用TTL）
 */
@Slf4j
@Configuration
public class PaymentRabbitMQConfig {

    /** 等待队列没有消费者，到期后才死信转发到处理队列。 */
    @Bean
    @ConditionalOnProperty(name = "trade.enabled", havingValue = "true")
    public Declarables paymentTopology() {
        DirectExchange exchange = new DirectExchange("trade.events.v3", true, false);
        DirectExchange failures = new DirectExchange("trade.failures.v3", true, false);
        Queue wait = QueueBuilder.durable("trade.deadline.wait.v3").deadLetterExchange(exchange.getName())
                .deadLetterRoutingKey("ORDER_DEADLINE_READY").build();
        Queue ready = QueueBuilder.durable("trade.deadline.ready.v3").deadLetterExchange(failures.getName())
                .deadLetterRoutingKey("DEADLINE_FAILED").build();
        Queue failed = QueueBuilder.durable("trade.deadline.failed.v3").build();
        Queue payment = QueueBuilder.durable("trade.notify.payment.v3").deadLetterExchange(failures.getName())
                .deadLetterRoutingKey("NOTIFY_FAILED").build();
        return new Declarables(List.of(wait, ready, failed, payment,
                BindingBuilder.bind(wait).to(exchange).with("ORDER_DEADLINE"),
                BindingBuilder.bind(ready).to(exchange).with("ORDER_DEADLINE_READY"),
                BindingBuilder.bind(failed).to(failures).with("DEADLINE_FAILED"),
                BindingBuilder.bind(payment).to(exchange).with("PAYMENT_SUCCESS")));
    }

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
