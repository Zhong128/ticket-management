package org.example.ticketmanagement.trade.messaging;

import com.alipay.api.AlipayClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.example.ticketmanagement.TicketManagementApplication;
import org.example.ticketmanagement.service.EmailService;
import org.example.ticketmanagement.trade.core.TradeOperations;
import org.example.ticketmanagement.trade.payment.PaymentGateway;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Full production Spring wiring on disposable services; no original configuration or external
 * payment/email/AI calls.
 */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = TicketManagementApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=classpath:application-trade-isolated-it.properties", "spring.config.import=" })
class TradeApplicationIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("trade_application_it").withUsername("trade_it").withPassword("isolated-test-only");

    @Container
    static final RabbitMQContainer BROKER = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.12-management-alpine"));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void isolatedEndpoints(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.rabbitmq.host", BROKER::getHost);
        properties.add("spring.rabbitmq.port", BROKER::getAmqpPort);
        properties.add("spring.rabbitmq.username", BROKER::getAdminUsername);
        properties.add("spring.rabbitmq.password", BROKER::getAdminPassword);
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        properties.add("redisson.config",
                () -> "singleServerConfig:\n  address: \"redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379)
                        + "\"\n" + "  database: 0\n" + "  connectionMinimumIdleSize: 1\n" + "  connectionPoolSize: 4\n"
                        + "  subscriptionConnectionMinimumIdleSize: 1\n" + "  subscriptionConnectionPoolSize: 2\n"
                        + "threads: 2\n" + "nettyThreads: 2\n");
    }

    @MockitoBean
    PaymentGateway gateway;

    @MockitoBean
    AlipayClient alipayClient;

    @MockitoBean
    EmailService email;

    @MockitoBean
    OpenAiChatModel ai;

    @Autowired
    ApplicationContext context;

    @Autowired
    Environment environment;

    @Autowired
    JdbcTemplate db;

    @Autowired
    TestRestTemplate http;

    @Autowired
    BCryptPasswordEncoder encoder;

    @Test
    void completeContextMigratesBeforeStartupAndServesPublicHttp() {
        assertNotNull(context.getBean(TradeOperations.class));
        assertNotNull(context.getBean(OutboxPublisher.class));
        assertNotNull(context.getBean(TradeEventConsumer.class));
        assertEquals(1, context.getBeansOfType(Clock.class).size());
        assertEquals(3, db.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class));
        assertEquals("", environment.getProperty("spring.config.import"));
        assertEquals("isolated-it-dummy-key", environment.getProperty("spring.ai.openai.api-key"));
        assertTrue(environment.getProperty("spring.datasource.url", "").contains("trade_application_it"));
        assertTrue(environment.getProperty("redisson.config", "").contains(":" + REDIS.getMappedPort(6379)));
        ResponseEntity<JsonNode> response = http.getForEntity("/api/public/categories", JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().path("code").asInt(), response.getBody().toString());
        assertTrue(response.getBody().path("data").isArray());
        verifyNoInteractions(gateway, alipayClient);
        verify(ai, never()).call(ArgumentMatchers.any(Prompt.class));
        verify(ai, never()).stream(ArgumentMatchers.any(Prompt.class));
    }

    @Test
    void loginReserveThenObserveOrderCreatedByRealOutboxConsumer() throws Exception {
        when(email.sendSimpleEmail(anyString(), anyString(), anyString())).thenReturn(true);
        LocalDateTime now = LocalDateTime.now();
        db.update(
                "INSERT INTO city(id,name,code,province) VALUES(1001,'HTTP test city','HTTPIT','Test" + " province')");
        db.update("INSERT INTO event_category(id,name) VALUES(1001,'HTTP test category')");
        db.update("INSERT INTO"
                + " event(id,name,category_id,city_id,venue,status,sale_start_time,sale_end_time,event_start_time,event_end_time)"
                + " VALUES(1001,'HTTP sale',1001,1001,'Test venue',1,?,?,?,?)", now.minusDays(1), now.plusDays(1),
                now.plusDays(2), now.plusDays(3));
        db.update("INSERT INTO event_session(id,event_id,session_name,session_time,status)"
                + " VALUES(1001,1001,'HTTP session',?,1)", now.plusDays(2));
        db.update("INSERT INTO"
                + " ticket_tier(id,event_id,session_id,tier_name,original_price,current_price,total_stock,available_stock,status)"
                + " VALUES(1001,1001,1001,'HTTP tier',100,100,10,10,1)");
        db.update(
                "INSERT INTO user(id,username,password,email,status,role)"
                        + " VALUES(1001,'http-it',?,'http-it@example.invalid',1,'USER')",
                encoder.encode("testpass123"));
        JsonNode login = post("/api/auth/user/login-or-register",
                Map.of("email", "http-it@example.invalid", "password", "testpass123"), null);
        assertEquals(1, login.path("code").asInt(), login.toString());
        String token = login.path("data").path("token").asText();
        assertFalse(token.isBlank());
        JsonNode accepted = post("/api/user/seckill",
                Map.of("tierId", 1001, "quantity", 2, "requestKey", "http-integration"), token);
        assertEquals(1, accepted.path("code").asInt(), accepted.toString());
        long requestId = accepted.path("data").path("requestId").asLong();
        assertTrue(requestId > 0, accepted.toString());
        JsonNode request = null;
        long limit = System.nanoTime() + Duration.ofSeconds(25).toNanos();
        while (System.nanoTime() < limit) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("ticket-token", token);
            ResponseEntity<JsonNode> response = http.exchange("/api/user/seckill/requests/" + requestId, HttpMethod.GET,
                    new HttpEntity<>(headers), JsonNode.class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            request = response.getBody();
            assertNotNull(request);
            assertEquals(1, request.path("code").asInt(), request.toString());
            if ("ORDER_CREATED".equals(request.path("data").path("requestStatus").asText()) && "PUBLISHED"
                    .equals(db.queryForObject("SELECT publish_status FROM message_outbox WHERE business_key=?",
                            String.class, "create-order:" + requestId + ":0"))) {
                break;
            }
            Thread.sleep(100);
        }
        assertNotNull(request);
        assertEquals("ORDER_CREATED", request.path("data").path("requestStatus").asText(), request.toString());
        long orderId = request.path("data").path("orderId").asLong();
        assertTrue(orderId > 0);
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM order_info WHERE id=? AND user_id=1001 AND quantity=2",
                Integer.class, orderId));
        assertEquals(8, db.queryForObject("SELECT available_stock FROM ticket_tier WHERE id=1001", Integer.class));
        assertEquals("PUBLISHED", db.queryForObject("SELECT publish_status FROM message_outbox WHERE business_key=?",
                String.class, "create-order:" + requestId + ":0"));
        // Repeating the HTTP request must return the same reservation and leave stock unchanged.
        JsonNode replay = post("/api/user/seckill",
                Map.of("tierId", 1001, "quantity", 2, "requestKey", "http-integration"), token);
        assertEquals(requestId, replay.path("data").path("requestId").asLong(), replay.toString());
        assertEquals(8, db.queryForObject("SELECT available_stock FROM ticket_tier WHERE id=1001", Integer.class));
        verifyNoInteractions(gateway, alipayClient);
        verify(ai, never()).call(ArgumentMatchers.any(Prompt.class));
        verify(ai, never()).stream(ArgumentMatchers.any(Prompt.class));
    }

    private JsonNode post(String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("ticket-token", token);
        }
        ResponseEntity<JsonNode> response = http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
                JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }
}
