package org.example.ticketmanagement.trade.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.example.ticketmanagement.trade.core.TradeOperations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.TransientDataAccessResourceException;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TradeEventConsumerTest {

    private final TradeOperations core = mock(TradeOperations.class);
    private final DeadlineHandler deadlines = mock(DeadlineHandler.class);
    private final StockProjection stocks = mock(StockProjection.class);
    private final NotificationTasks notifications = mock(NotificationTasks.class);
    private final Channel channel = mock(Channel.class);
    private final TradeEventConsumer consumer = new TradeEventConsumer(new ObjectMapper(), core, deadlines, stocks,
            notifications);

    private Message message(String json) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(9L);
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }

    @ParameterizedTest
    @ValueSource(strings = { "not-json", "null", "[]", "{}", "{\"eventType\":\"UNKNOWN\",\"payload\":{}}",
            "{\"eventType\":\"CREATE_ORDER\",\"payload\":{\"reservationId\":0}}",
            "{\"eventType\":\"ORDER_DEADLINE\",\"payload\":{\"reservationId\":-1}}",
            "{\"eventType\":\"ORDER_CREATED\",\"payload\":{\"reservationId\":1.5}}",
            "{\"eventType\":\"CREATE_ORDER\",\"payload\":{\"reservationId\":\"1\"}}",
            "{\"eventType\":\"CREATE_ORDER\",\"payload\":{\"reservationId\":9223372036854775808}}",
            "{\"eventType\":\"CREATE_ORDER\",\"payload\":[]}",
            "{\"eventType\":\"STOCK_CHANGED\",\"payload\":{\"tierId\":1,\"version\":-1,\"stock\":1}}",
            "{\"eventType\":\"STOCK_CHANGED\",\"payload\":{\"tierId\":1,\"version\":1,\"stock\":-1}}",
            "{\"eventType\":\"STOCK_CHANGED\",\"payload\":{\"tierId\":0,\"version\":1,\"stock\":1}}",
            "{\"eventType\":\"STOCK_CHANGED\",\"payload\":{\"tierId\":1,\"stock\":1}}",
            "{\"eventType\":\"PAYMENT_SUCCESS\",\"payload\":{\"paymentNo\":\" \"}}",
            "{\"eventType\":\"PAYMENT_SUCCESS\",\"payload\":{\"paymentNo\":123}}",
            "{\"eventType\":\"CREATE_ORDER\",\"payload\":{\"reservationId\":1}} {}" })
    void malformedOrInvalidEnvelopeIsRejectedToFailureQueue(String json) throws Exception {
        consumer.consume(message(json), channel);
        verify(channel).basicReject(9L, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verifyNoInteractions(core, deadlines, stocks, notifications);
    }

    @Test
    void transientBusinessFailureStillRequeues() throws Exception {
        doThrow(new TransientDataAccessResourceException("Database temporarily unavailable")).when(deadlines)
                .handle(7L);
        consumer.consume(message("{\"eventType\":\"ORDER_DEADLINE\",\"payload\":{\"reservationId\":7}}"), channel);
        verify(channel).basicNack(9L, false, true);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
    }

    @Test
    void validZeroStockAndVersionAreAccepted() throws Exception {
        consumer.consume(
                message("{\"eventType\":\"STOCK_CHANGED\",\"payload\":{\"tierId\":1,\"version\":0,\"stock\":0}}"),
                channel);
        verify(stocks).apply(1L, 0L, 0);
        verify(channel).basicAck(9L, false);
    }

    @Test
    void creationFailurePersistsRetryBeforeAcknowledging() throws Exception {
        doThrow(new IllegalStateException("temporary")).when(core).createOrder(7L);
        consumer.consume(message("{\"eventType\":\"CREATE_ORDER\",\"payload\":{\"reservationId\":7}}"), channel);
        var ordered = inOrder(core, channel);
        ordered.verify(core).createOrder(7L);
        ordered.verify(core).creationFailed(7L, "temporary");
        ordered.verify(channel).basicAck(9L, false);
    }
}
