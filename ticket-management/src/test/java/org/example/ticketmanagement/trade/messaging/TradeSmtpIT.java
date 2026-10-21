package org.example.ticketmanagement.trade.messaging;

import com.alipay.api.AlipayClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.example.ticketmanagement.TicketManagementApplication;
import org.example.ticketmanagement.pojo.PaymentRecord;
import org.example.ticketmanagement.trade.payment.PaymentGateway;
import org.example.ticketmanagement.util.AlipaySignatureUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 显式启用的真实 SMTP 验收；业务数据只写入 Testcontainers 新建的隔离库。
 */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "RUN_SMTP_ACCEPTANCE", matches = "true")
@SpringBootTest(classes = TicketManagementApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "spring.config.location=classpath:application-trade-isolated-it.properties",
                "spring.config.import=" })
class TradeSmtpIT {

    private static final String RECIPIENT = System.getenv("SPRING_MAIL_USERNAME");
    private static final String PASSWORD = System.getenv("SPRING_MAIL_PASSWORD");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("trade_smtp_it").withUsername("smtp_it").withPassword("isolated-test-only");

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
        properties.add("redisson.config", () -> "singleServerConfig:\n  address: \"redis://"
                + REDIS.getHost() + ":" + REDIS.getMappedPort(6379) + "\"\n  database: 0\n"
                + "  connectionMinimumIdleSize: 1\n  connectionPoolSize: 4\n"
                + "  subscriptionConnectionMinimumIdleSize: 1\n  subscriptionConnectionPoolSize: 2\n"
                + "threads: 2\nnettyThreads: 2\n");

        // 只把本地忽略配置中的邮箱凭据交给这次隔离应用。
        properties.add("spring.mail.host", () -> "smtp.qq.com");
        properties.add("spring.mail.port", () -> 587);
        properties.add("spring.mail.username", () -> RECIPIENT);
        properties.add("spring.mail.password", () -> PASSWORD);
        properties.add("spring.mail.properties.mail.smtp.auth", () -> true);
        properties.add("spring.mail.properties.mail.smtp.starttls.enable", () -> true);
        properties.add("spring.mail.properties.mail.smtp.starttls.required", () -> true);
        properties.add("spring.mail.properties.mail.smtp.connectiontimeout", () -> 10000);
        properties.add("spring.mail.properties.mail.smtp.timeout", () -> 10000);
        properties.add("spring.mail.properties.mail.smtp.writetimeout", () -> 10000);
    }

    @MockitoBean
    PaymentGateway gateway;

    @MockitoBean
    AlipayClient alipayClient;

    @MockitoBean
    OpenAiChatModel ai;

    @MockitoBean
    AlipaySignatureUtil signature;

    @Autowired
    JdbcTemplate db;

    @Autowired
    TestRestTemplate http;

    @Autowired
    BCryptPasswordEncoder encoder;

    @Test
    void orderAndPaymentNotificationsReachConfiguredMailboxOnce() throws Exception {
        assertTrue(RECIPIENT != null && RECIPIENT.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"),
                "SPRING_MAIL_USERNAME 必须是测试收件邮箱");
        assertTrue(PASSWORD != null && !PASSWORD.isBlank(), "SPRING_MAIL_PASSWORD 必须在本地环境中配置");

        // 使用隔离库和测试用户，通过真实 HTTP 抢票并等待异步建单。
        seedSaleAndUser();
        JsonNode login = post("/api/auth/user/login-or-register",
                Map.of("email", RECIPIENT, "password", "smtp-test-pass"), null);
        assertEquals(1, login.path("code").asInt(), login.toString());
        String token = login.path("data").path("token").asText();
        assertFalse(token.isBlank());

        JsonNode accepted = post("/api/user/seckill",
                Map.of("tierId", 5001, "quantity", 1, "requestKey", "smtp-" + UUID.randomUUID()), token);
        assertEquals(1, accepted.path("code").asInt(), accepted.toString());
        long requestId = accepted.path("data").path("requestId").asLong();
        long orderId = awaitOrder(requestId, token);
        awaitEmail(orderId, "EMAIL_ORDER");

        // 模拟已验签的支付回调，不接触支付宝渠道或真实资金。
        when(gateway.create(any(PaymentRecord.class))).thenReturn("isolated-payment-page");
        JsonNode created = post("/api/user/payments/create", Map.of("orderId", orderId, "payType", 1), token);
        assertEquals(1, created.path("code").asInt(), created.toString());
        String paymentNo = created.path("data").path("paymentNo").asText();
        when(signature.verifyAlipaySignature(any())).thenReturn(true);

        LinkedMultiValueMap<String, String> notification = new LinkedMultiValueMap<>();
        notification.add("app_id", "isolated-it");
        notification.add("out_trade_no", paymentNo);
        notification.add("trade_no", "smtp-trade-" + UUID.randomUUID());
        notification.add("total_amount", new BigDecimal("0.01").toPlainString());
        notification.add("trade_status", "TRADE_SUCCESS");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<LinkedMultiValueMap<String, String>> request = new HttpEntity<>(notification, headers);
        assertEquals("success", http.postForObject("/api/public/payments/notify", request, String.class));
        awaitEmail(orderId, "EMAIL_PAYMENT");

        // 重复通知仍只保留同一条付款邮件任务。
        assertEquals("success", http.postForObject("/api/public/payments/notify", request, String.class));
        assertEquals(1, taskCount(orderId, "EMAIL_ORDER"));
        assertEquals(1, taskCount(orderId, "EMAIL_PAYMENT"));
        assertEquals(1, db.queryForObject("SELECT order_status FROM order_info WHERE id=?", Integer.class, orderId));
    }

    private void seedSaleAndUser() {
        LocalDateTime now = LocalDateTime.now();
        db.update("INSERT INTO city(id,name,code,province) VALUES(5001,'SMTP city','SMTP','Test province')");
        db.update("INSERT INTO event_category(id,name) VALUES(5001,'SMTP category')");
        db.update("INSERT INTO event(id,name,category_id,city_id,venue,status,sale_start_time,sale_end_time,"
                        + "event_start_time,event_end_time) VALUES(5001,'SMTP event',5001,5001,'SMTP venue',1,?,?,?,?)",
                now.minusDays(1), now.plusDays(1), now.plusDays(2), now.plusDays(3));
        db.update("INSERT INTO event_session(id,event_id,session_name,session_time,status) "
                + "VALUES(5001,5001,'SMTP session',?,1)", now.plusDays(2));
        db.update("INSERT INTO ticket_tier(id,event_id,session_id,tier_name,original_price,current_price,"
                + "total_stock,available_stock,status) VALUES(5001,5001,5001,'SMTP tier',0.01,0.01,2,2,1)");
        db.update("INSERT INTO user(id,username,password,email,status,role) VALUES(5001,'smtp-it',?,?,1,'USER')",
                encoder.encode("smtp-test-pass"), RECIPIENT);
    }

    private long awaitOrder(long requestId, String token) throws InterruptedException {
        long limit = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < limit) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("ticket-token", token);
            ResponseEntity<JsonNode> response = http.exchange("/api/user/seckill/requests/" + requestId,
                    HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            JsonNode data = response.getBody().path("data");
            if ("ORDER_CREATED".equals(data.path("requestStatus").asText())) {
                return data.path("orderId").asLong();
            }
            Thread.sleep(200);
        }
        throw new AssertionError("订单未在 30 秒内创建");
    }

    private void awaitEmail(long orderId, String type) throws InterruptedException {
        long limit = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < limit) {
            Integer completed = db.queryForObject("SELECT COUNT(*) FROM payment_compensation "
                            + "WHERE order_id=? AND task_type=? AND task_status=2", Integer.class, orderId, type);
            if (completed != null && completed == 1) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError(type + " 邮件未在 60 秒内成功提交给 SMTP 服务");
    }

    private int taskCount(long orderId, String type) {
        return db.queryForObject("SELECT COUNT(*) FROM payment_compensation WHERE order_id=? AND task_type=?",
                Integer.class, orderId, type);
    }

    private JsonNode post(String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("ticket-token", token);
        }
        ResponseEntity<JsonNode> response = http.postForEntity(path, new HttpEntity<>(body, headers), JsonNode.class);
        return response.getBody();
    }
}
