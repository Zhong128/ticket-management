package org.example.ticketmanagement.trade.messaging;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TradeTopologyTest {

    @Test
    void deadlineWaitHasDeadLetterRouteAndNoConsumer() throws Exception {
        Declarables topology = new TradeMessagingConfig().tradeTopology();
        Queue wait = topology.getDeclarablesByType(Queue.class).stream()
                .filter(q -> q.getName().equals("trade.deadline.wait.v2")).findFirst().orElseThrow();
        assertEquals("trade.events.v2", wait.getArguments().get("x-dead-letter-exchange"));
        assertEquals("ORDER_DEADLINE_READY", wait.getArguments().get("x-dead-letter-routing-key"));
        var listener = TradeEventConsumer.class.getMethod("consume", Message.class, Channel.class)
                .getAnnotation(RabbitListener.class);
        assertFalse(List.of(listener.queues()).contains(wait.getName()));
    }

    @Test
    void bothProcessingQueuesHaveBoundDurableFailureDestinations() throws Exception {
        Declarables topology = new TradeMessagingConfig().tradeTopology();
        var queues = topology.getDeclarablesByType(Queue.class);
        var exchanges = topology.getDeclarablesByType(Exchange.class);
        var bindings = topology.getDeclarablesByType(Binding.class);
        for (String source : List.of("trade.work.v2", "trade.deadline.ready.v2")) {
            Queue processing = queues.stream().filter(q -> q.getName().equals(source)).findFirst().orElseThrow();
            String dlx = (String) processing.getArguments().get("x-dead-letter-exchange");
            String routing = (String) processing.getArguments().get("x-dead-letter-routing-key");
            assertNotNull(dlx);
            assertNotNull(routing);
            assertTrue(exchanges.stream().anyMatch(e -> e.getName().equals(dlx) && e.isDurable()));
            Binding binding = bindings.stream()
                    .filter(b -> b.getExchange().equals(dlx) && b.getRoutingKey().equals(routing)).findFirst()
                    .orElseThrow();
            Queue failed = queues.stream().filter(q -> q.getName().equals(binding.getDestination())).findFirst()
                    .orElseThrow();
            assertTrue(failed.isDurable());
            assertFalse(failed.getArguments().containsKey("x-message-ttl"));
            var listener = TradeEventConsumer.class.getMethod("consume", Message.class, Channel.class)
                    .getAnnotation(RabbitListener.class);
            assertFalse(List.of(listener.queues()).contains(failed.getName()));
        }
    }
}
