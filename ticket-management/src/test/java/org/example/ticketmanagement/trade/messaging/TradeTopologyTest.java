package org.example.ticketmanagement.trade.messaging;

import org.example.ticketmanagement.config.PaymentRabbitMQConfig;
import org.example.ticketmanagement.config.RabbitMQConfig;
import org.example.ticketmanagement.consumer.PaymentTimeoutConsumer;
import org.example.ticketmanagement.consumer.SeckillOrderConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TradeTopologyTest {

    private Declarables topology() {
        List<Declarable> declarations = new ArrayList<>();
        declarations.addAll(new RabbitMQConfig().seckillTopology().getDeclarables());
        declarations.addAll(new PaymentRabbitMQConfig().paymentTopology().getDeclarables());
        return new Declarables(declarations);
    }

    @Test
    void deadlineWaitHasDeadLetterRouteAndNoConsumer() {
        Queue wait = topology().getDeclarablesByType(Queue.class).stream()
                .filter(queue -> queue.getName().equals("trade.deadline.wait.v3")).findFirst().orElseThrow();
        assertEquals("trade.events.v3", wait.getArguments().get("x-dead-letter-exchange"));
        assertEquals("ORDER_DEADLINE_READY", wait.getArguments().get("x-dead-letter-routing-key"));
        for (var method : PaymentTimeoutConsumer.class.getDeclaredMethods()) {
            RabbitListener listener = method.getAnnotation(RabbitListener.class);
            if (listener != null) {
                assertFalse(List.of(listener.queues()).contains(wait.getName()));
            }
        }
    }

    @Test
    void processingQueuesHaveDurableFailureDestinations() {
        Declarables topology = topology();
        List<Queue> queues = topology.getDeclarablesByType(Queue.class);
        List<Exchange> exchanges = topology.getDeclarablesByType(Exchange.class);
        List<Binding> bindings = topology.getDeclarablesByType(Binding.class);
        for (String source : List.of("trade.order.v3", "trade.deadline.ready.v3", "trade.stock.v3",
                "trade.notify.order.v3", "trade.notify.payment.v3")) {
            Queue processing = queues.stream().filter(queue -> queue.getName().equals(source)).findFirst().orElseThrow();
            String exchange = (String) processing.getArguments().get("x-dead-letter-exchange");
            String route = (String) processing.getArguments().get("x-dead-letter-routing-key");
            assertTrue(exchanges.stream().anyMatch(item -> item.getName().equals(exchange) && item.isDurable()));
            Binding failure = bindings.stream().filter(binding -> binding.getExchange().equals(exchange)
                    && binding.getRoutingKey().equals(route)).findFirst().orElseThrow();
            Queue failed = queues.stream().filter(queue -> queue.getName().equals(failure.getDestination()))
                    .findFirst().orElseThrow();
            assertTrue(failed.isDurable());
            assertFalse(failed.getArguments().containsKey("x-message-ttl"));
        }
    }
}
