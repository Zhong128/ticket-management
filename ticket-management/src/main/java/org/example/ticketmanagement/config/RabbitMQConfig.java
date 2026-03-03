package org.example.ticketmanagement.config;

import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.OrderConstant;
import org.example.ticketmanagement.constant.SeckillConstant;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class RabbitMQConfig {

    // ==================== 抢票队列 ====================

    /**
     * 抢票订单交换机（直连交换机）
     */
    @Bean
    public DirectExchange seckillOrderExchange() {
        return new DirectExchange(SeckillConstant.SECKILL_ORDER_EXCHANGE, true, false);
    }

    /**
     * 抢票订单队列
     * - 持久化
     * - 死信交换机：用于处理超时未支付的订单
     */
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(SeckillConstant.SECKILL_ORDER_QUEUE)
                .withArgument("x-dead-letter-exchange", "seckill.order.dead.exchange")  // 死信交换机
                .withArgument("x-dead-letter-routing-key", "seckill.order.dead.routing.key")  // 死信路由键
                .withArgument("x-message-ttl", SeckillConstant.PAY_TIMEOUT_MINUTES * 60 * 1000)  // 消息TTL（支付超时时间）
                .build();
    }

    /**
     * 绑定抢票订单队列到交换机
     */
    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder.bind(seckillOrderQueue())
                .to(seckillOrderExchange())
                .with(SeckillConstant.SECKILL_ORDER_ROUTING_KEY);
    }

    // ==================== 死信队列（处理超时订单） ====================

    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange seckillOrderDeadExchange() {
        return new DirectExchange("seckill.order.dead.exchange", true, false);
    }

    /**
     * 死信队列（用于处理超时未支付的订单）
     */
    @Bean
    public Queue seckillOrderDeadQueue() {
        return QueueBuilder.durable("seckill.order.dead.queue").build();
    }

    /**
     * 死信监控
     */
    @Bean
    public RabbitAdmin rabbitAdmin(RabbitTemplate rabbitTemplate) {
        return new RabbitAdmin(rabbitTemplate);
    }

    /**
     * 绑定死信队列到死信交换机
     */
    @Bean
    public Binding seckillOrderDeadBinding() {
        return BindingBuilder.bind(seckillOrderDeadQueue())
                .to(seckillOrderDeadExchange())
                .with("seckill.order.dead.routing.key");
    }

    // 保留基础的库存回退队列（用于其他场景，如支付失败等）
    @Bean
    public DirectExchange stockReturnExchange() {
        return new DirectExchange("stock.return.exchange", true, false);
    }

    @Bean
    public Queue stockReturnQueue() {
        return QueueBuilder.durable("stock.return.queue").build();
    }

    @Bean
    public Binding stockReturnBinding() {
        return BindingBuilder.bind(stockReturnQueue())
                .to(stockReturnExchange())
                .with("stock.return.routing.key");
    }

    // ==================== RabbitMQ模板配置 ====================

    /**
     * 配置RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);

        // 使用JSON序列化
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());

        // 开启消息确认模式
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("消息发送成功, correlationData: {}", correlationData);
            } else {
                log.error("消息发送失败, cause: {}, correlationData: {}", cause, correlationData);
                // 这里可以添加重发逻辑
            }
        });

        // 开启消息返回模式
        rabbitTemplate.setReturnsCallback(returned -> {
            log.error("消息路由失败: {}", returned.getMessage());
            // 这里可以添加消息补偿逻辑
        });

        return rabbitTemplate;
    }

    /**
     * 配置消息监听容器工厂
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);

        // 设置并发消费者数量
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);

        // 设置预取数量
        factory.setPrefetchCount(10);

        // 使用JSON反序列化
        factory.setMessageConverter(new Jackson2JsonMessageConverter());

        // 开启手动确认
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);

        return factory;
    }
}