package org.example.ticketmanagement.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 支付到期与支付成功通知的V3拓扑
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

}
