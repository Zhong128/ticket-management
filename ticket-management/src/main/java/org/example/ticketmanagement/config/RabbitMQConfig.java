package org.example.ticketmanagement.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import java.util.List;

@Slf4j
@Configuration
public class RabbitMQConfig {

    @Value("${seckill.consumer.concurrent:3}")
    private int concurrent = 3;
    @Value("${seckill.consumer.max-concurrent:10}")
    private int maxConcurrent = 10;
    @Value("${seckill.consumer.prefetch:10}")
    private int prefetch = 10;

    /** V3建单、Redis状态与订单通知各自路由，失败队列持久保留。 */
    @Bean
    @ConditionalOnProperty(name = "trade.enabled", havingValue = "true")
    public Declarables seckillTopology() {
        DirectExchange exchange = new DirectExchange("trade.events.v3", true, false);
        DirectExchange failures = new DirectExchange("trade.failures.v3", true, false);
        Queue orders = QueueBuilder.durable("trade.order.v3").deadLetterExchange(failures.getName())
                .deadLetterRoutingKey("ORDER_FAILED").build();
        Queue failed = QueueBuilder.durable("trade.order.failed.v3").build();
        Queue state = QueueBuilder.durable("trade.stock.v3").deadLetterExchange(failures.getName())
                .deadLetterRoutingKey("STOCK_FAILED").build();
        Queue stateFailed = QueueBuilder.durable("trade.stock.failed.v3").build();
        Queue notifications = QueueBuilder.durable("trade.notify.order.v3").deadLetterExchange(failures.getName())
                .deadLetterRoutingKey("NOTIFY_FAILED").build();
        Queue notifyFailed = QueueBuilder.durable("trade.notify.failed.v3").build();
        return new Declarables(List.of(exchange, failures, orders, failed, state, stateFailed, notifications, notifyFailed,
                BindingBuilder.bind(orders).to(exchange).with("CREATE_ORDER"),
                BindingBuilder.bind(failed).to(failures).with("ORDER_FAILED"),
                BindingBuilder.bind(stateFailed).to(failures).with("STOCK_FAILED"),
                BindingBuilder.bind(notifyFailed).to(failures).with("NOTIFY_FAILED"),
                BindingBuilder.bind(notifications).to(exchange).with("ORDER_CREATED"),
                BindingBuilder.bind(state).to(exchange).with("RESERVATION_ACCEPTED"),
                BindingBuilder.bind(state).to(exchange).with("RESERVATION_RELEASED"),
                BindingBuilder.bind(state).to(exchange).with("STOCK_CHANGED"),
                BindingBuilder.bind(state).to(exchange).with("CACHE_REBUILD")));
    }

    /** 统一后台线程池，避免发布、恢复和邮件互相串行阻塞。 */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(8);
        scheduler.setThreadNamePrefix("seckill-recovery-");
        return scheduler;
    }

    /** 管理当前版本拓扑，不清空已有队列。 */
    @Bean
    public RabbitAdmin rabbitAdmin(RabbitTemplate rabbitTemplate) {
        return new RabbitAdmin(rabbitTemplate);
    }

    // ==================== RabbitMQ模板配置 ====================
    /**
     * 配置RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        if (connectionFactory instanceof CachingConnectionFactory caching) {
            caching.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
            caching.setPublisherReturns(true);
        }
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMandatory(true);
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
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        // 设置并发消费者数量
        factory.setConcurrentConsumers(concurrent);
        factory.setMaxConcurrentConsumers(maxConcurrent);
        // 设置预取数量
        factory.setPrefetchCount(prefetch);
        // 使用JSON反序列化
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        // 开启手动确认
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
