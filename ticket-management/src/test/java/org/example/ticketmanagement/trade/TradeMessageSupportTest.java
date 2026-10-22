package org.example.ticketmanagement.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.example.ticketmanagement.serviceimpl.MessageRecoveryService;
import org.example.ticketmanagement.util.TradeMessageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TradeMessageSupportTest {
    final MessageRecoveryService recovery = mock(MessageRecoveryService.class);
    final Channel channel = mock(Channel.class);
    final TradeMessageSupport support = new TradeMessageSupport(new ObjectMapper(), recovery);

    private Message message(String payload) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("original-event");
        properties.setDeliveryTag(7);
        return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    }

    @Test
    void retryIsPersistedBeforeAcknowledgement() throws Exception {
        support.consume(message("{\"eventType\":\"CREATE_ORDER\",\"payload\":{\"reservationId\":1}}"),
                channel, Set.of("CREATE_ORDER"), event -> {
                    throw new IllegalStateException("temporary");
                });
        var ordered = inOrder(recovery, channel);
        ordered.verify(recovery).retry(eq("original-event"), eq("CREATE_ORDER"), any());
        ordered.verify(channel).basicAck(7, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void failedPersistenceNeverAcknowledgesTheOnlyMessage() throws Exception {
        doThrow(new IllegalStateException("database unavailable")).when(recovery).retry(anyString(), anyString(), any());
        support.consume(message("{\"eventType\":\"CREATE_ORDER\",\"payload\":{\"reservationId\":1}}"),
                channel, Set.of("CREATE_ORDER"), event -> {
                    throw new IllegalStateException("temporary");
                });
        verify(channel).basicNack(7, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void malformedEventDoesNotCallBusinessOrRetryService() throws Exception {
        support.consume(message("{\"eventType\":\"CREATE_ORDER\",\"payload\":{\"reservationId\":\"1\"}}"),
                channel, Set.of("CREATE_ORDER"), event -> {
                    throw new AssertionError("非法事件不应执行业务");
                });
        verify(channel).basicReject(7, false);
        verifyNoInteractions(recovery);
    }
}
