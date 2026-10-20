package org.example.ticketmanagement.trade.messaging;

import org.junit.jupiter.api.Test;

import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class MessagingBoundaryTest {

    @Test
    void transactionalOutboxExists() throws Exception {
        Path source = Path.of("src/main/java/org/example/ticketmanagement/trade/messaging/OutboxStore.java");
        assertTrue(Files.exists(source), "A durable outbox must replace direct MQ writes");
        String text = Files.readString(source);
        assertTrue(text.contains("TradeOutboxMapper"));
        assertTrue(text.contains("Propagation.MANDATORY"));
        assertFalse(text.contains("JdbcTemplate"));
        String sql = Files
                .readString(Path.of("src/main/java/org/example/ticketmanagement/mapper/TradeOutboxMapper.java"));
        assertTrue(sql.contains("business_key"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
    }

    @Test
    void oldDeadLetterCannotRestoreRedis() throws Exception {
        String text = Files.readString(
                Path.of("src/main/java/org/example/ticketmanagement/consumer/SeckillDeadLetterConsumer.java"));
        assertFalse(text.contains("stockService.restoreStock("), "Legacy dead letters must not mint stock");
    }
}
