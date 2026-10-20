package org.example.ticketmanagement.trade.messaging;

import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "trade.enabled", havingValue = "true")
public class TradeMessagingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(8);
        scheduler.setThreadNamePrefix("trade-recovery-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    @Bean
    public Declarables tradeTopology() {
        DirectExchange exchange = new DirectExchange(OutboxPublisher.EXCHANGE, true, false);
        DirectExchange failures = new DirectExchange("trade.failures.v2", true, false);
        Queue workFailed = QueueBuilder.durable("trade.work.failed.v2").build();
        Queue deadlineFailed = QueueBuilder.durable("trade.deadline.failed.v2").build();
        Queue work = QueueBuilder.durable("trade.work.v2").deadLetterExchange(failures.getName())
                .deadLetterRoutingKey("WORK_FAILED").build();
        Queue deadline = QueueBuilder.durable("trade.deadline.ready.v2").deadLetterExchange(failures.getName())
                .deadLetterRoutingKey("DEADLINE_FAILED").build();
        // This queue has no listener. Only expired messages reach the processing queue.
        Queue wait = QueueBuilder.durable("trade.deadline.wait.v2").deadLetterExchange(OutboxPublisher.EXCHANGE)
                .deadLetterRoutingKey("ORDER_DEADLINE_READY").build();
        List<Declarable> declarations = new ArrayList<>(List.of(exchange, failures, work, deadline, wait, workFailed,
                deadlineFailed, BindingBuilder.bind(workFailed).to(failures).with("WORK_FAILED"),
                BindingBuilder.bind(deadlineFailed).to(failures).with("DEADLINE_FAILED"),
                BindingBuilder.bind(wait).to(exchange).with("ORDER_DEADLINE"),
                BindingBuilder.bind(deadline).to(exchange).with("ORDER_DEADLINE_READY")));
        for (String event : List.of("CREATE_ORDER", "ORDER_CREATED", "PAYMENT_SUCCESS", "STOCK_CHANGED")) {
            declarations.add(BindingBuilder.bind(work).to(exchange).with(event));
        }
        return new Declarables(declarations);
    }
}
