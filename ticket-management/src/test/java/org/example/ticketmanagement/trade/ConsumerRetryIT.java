package org.example.ticketmanagement.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ticketmanagement.mapper.ConsumerRetryMapper;
import org.example.ticketmanagement.mapper.TradeOutboxMapper;
import org.example.ticketmanagement.serviceimpl.MessageRecoveryService;
import org.example.ticketmanagement.trade.messaging.OutboxStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实MySQL验证消费次数与Outbox同事务，不依赖正在运行的消息消费者。 */
@Testcontainers
class ConsumerRetryIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    static DriverManagerDataSource source;
    static JdbcTemplate db;
    final ObjectMapper json = new ObjectMapper();
    final Clock clock = Clock.systemUTC();

    @BeforeAll
    static void migrate() {
        source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        db = new JdbcTemplate(source);
    }

    private MessageRecoveryService service(OutboxStore events) {
        var mapper = MapperTestSupport.sessions(source).getMapper(ConsumerRetryMapper.class);
        return new MessageRecoveryService(mapper, events, json,
                new TransactionTemplate(new DataSourceTransactionManager(source)), clock);
    }

    private OutboxStore outbox() {
        return new OutboxStore(MapperTestSupport.sessions(source).getMapper(TradeOutboxMapper.class), json, clock);
    }

    @Test
    void tenthFailureBecomesManualAndDoesNotCreateAnEleventhRetry() {
        String root = UUID.randomUUID().toString();
        MessageRecoveryService recovery = service(outbox());
        for (int attempt = 0; attempt < 12; attempt++) {
            recovery.retry(root, "CREATE_ORDER", json.createObjectNode().put("reservationId", 1));
        }
        assertEquals(10, db.queryForObject(
                "SELECT retry_count FROM payment_compensation WHERE business_key=?", Integer.class, root));
        assertEquals(3, db.queryForObject(
                "SELECT task_status FROM payment_compensation WHERE business_key=?", Integer.class, root));
        assertEquals(9, db.queryForObject("SELECT COUNT(*) FROM message_outbox WHERE business_key LIKE ?",
                Integer.class, "consumer-retry:" + root + ":%"));
    }

    @Test
    void failedOutboxWriteRollsBackTheRetryCounterAndTask() {
        String root = UUID.randomUUID().toString();
        OutboxStore events = spy(outbox());
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("injected failure after real outbox insert");
        }).when(events).enqueueAt(anyString(), anyString(), any(), any());
        assertThrows(IllegalStateException.class, () -> service(events).retry(root, "CREATE_ORDER",
                json.createObjectNode().put("reservationId", 1)));
        assertEquals(0, db.queryForObject(
                "SELECT COUNT(*) FROM payment_compensation WHERE business_key=?", Integer.class, root));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM message_outbox WHERE business_key LIKE ?",
                Integer.class, "consumer-retry:" + root + ":%"));
    }
}
