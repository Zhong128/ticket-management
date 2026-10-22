package org.example.ticketmanagement.trade.messaging;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.example.ticketmanagement.serviceimpl.StockServiceImpl;
import org.example.ticketmanagement.service.EmailService;
import org.example.ticketmanagement.trade.payment.RefundService;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 显式启用的真实支付宝沙箱验收；业务数据只写入 Testcontainers 新建的隔离库。
 */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "RUN_ALIPAY_SANDBOX", matches = "true")
@SpringBootTest(classes = TicketManagementApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = { "spring.config.location=classpath:application-trade-isolated-it.properties",
                "spring.config.import=", "server.port=18080", "server.address=127.0.0.1" })
@Import(AlipaySandboxIT.CapturedCallbacks.class)
class AlipaySandboxIT {

    private static final String RECIPIENT = "sandbox-refactor@example.invalid";

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

        // 密钥仅从进程环境读取；本配置不会导入工作目录中的.env。
        properties.add("alipay.app-id", () -> System.getenv("ALIPAY_APP_ID"));
        properties.add("alipay.merchant-private-key", () -> System.getenv("ALIPAY_MERCHANT_PRIVATE_KEY"));
        properties.add("alipay.alipay-public-key", () -> System.getenv("ALIPAY_PUBLIC_KEY"));
        properties.add("alipay.gateway-url", () -> System.getenv().getOrDefault("ALIPAY_GATEWAY_URL", "https://openapi-sandbox.dl.alipaydev.com/gateway.do"));
        properties.add("alipay.notify-url", () -> System.getenv("ALIPAY_NOTIFY_URL"));
        properties.add("alipay.return-url", () -> System.getenv("ALIPAY_RETURN_URL"));
    }

    @Autowired
    PaymentGateway gateway;
    @Autowired
    RefundService refunds;
    @MockitoBean
    EmailService email;
    @MockitoBean
    OpenAiChatModel ai;

    @Autowired
    JdbcTemplate db;

    @Autowired
    TestRestTemplate http;

    @Autowired
    BCryptPasswordEncoder encoder;

    private static final Path DIRECTORY = Path.of("target", "alipay-sandbox");

    @Test
    void realChannelNotificationsCloseAndLateRefundPreserveRedisInventory() throws Exception {
        assertTrue(System.getenv().getOrDefault("ALIPAY_GATEWAY_URL", "https://openapi-sandbox.dl.alipaydev.com/gateway.do")
                .equals("https://openapi-sandbox.dl.alipaydev.com/gateway.do"), "只能使用支付宝沙箱网关");
        assertTrue(System.getenv("ALIPAY_APP_ID") != null, "请通过忽略的本地配置提供沙箱材料");
        when(email.sendSimpleEmail(anyString(), anyString(), anyString())).thenReturn(true);
        Files.createDirectories(DIRECTORY);
        Files.deleteIfExists(DIRECTORY.resolve("allow-close.signal"));
        seedSaleAndUser();
        String token = post("/api/auth/user/login-or-register",
                Map.of("email", RECIPIENT, "password", "smtp-test-pass"), null).path("data").path("token").asText();
        assertFalse(token.isBlank());

        // 首笔付款必须由真实渠道通知完成，回跳或查单不能替代异步通知证据。
        Intent normal = intent(token, "normal", "refactor-payment-page.html");
        phase("WAIT_NORMAL_PAYMENT");
        awaitPaidCallback(normal.paymentNo());
        assertEquals(1, orderStatus(normal.orderId()));
        replayCapturedNotification(normal.paymentNo());
        assertEquals(1, taskCount(normal.orderId(), "EMAIL_PAYMENT"));
        assertInventory();

        // 隔离库提前本地截止时间，原渠道表单仍有效，模拟已释放后才收到付款。
        String lateToken = loginBuyer(5002);
        Intent late = intent(lateToken, "late", "refactor-late-payment-page.html");
        LocalDateTime expired = LocalDateTime.now().minusSeconds(5);
        db.update("UPDATE stock_reservation SET expires_at=? WHERE order_id=?", expired, late.orderId());
        db.update("UPDATE order_info SET expire_time=? WHERE id=?", expired, late.orderId());
        db.update("UPDATE payment_record SET expire_time=? WHERE payment_no=?", expired, late.paymentNo());
        assertEquals(1, put("/api/user/orders/" + late.orderId() + "/cancel", lateToken).path("code").asInt());
        assertEquals(2, orderStatus(late.orderId()));
        int releasedStock = db.queryForObject("SELECT available_stock FROM ticket_tier WHERE id=5001", Integer.class);
        phase("WAIT_LATE_PAYMENT");
        awaitPaidCallback(late.paymentNo());
        long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
        while (System.nanoTime() < deadline && !"SUCCESS".equals(refundStatus(late.paymentNo()))) {
            refunds.processDueRefunds();
            Thread.sleep(1000);
        }
        assertEquals("SUCCESS", refundStatus(late.paymentNo()));
        String refundNo = db.queryForObject("SELECT refund_no FROM refund_record WHERE payment_no=?", String.class, late.paymentNo());
        assertEquals(PaymentGateway.RefundState.SUCCESS,
                gateway.queryRefund(late.paymentNo(), refundNo, new BigDecimal("0.01")).state());
        replayCapturedNotification(late.paymentNo());
        assertEquals(releasedStock, db.queryForObject("SELECT available_stock FROM ticket_tier WHERE id=5001", Integer.class));
        assertInventory();

        // 渠道真实建单后由人工确认停在未付步骤，再验收真实关单。
        String closeToken = loginBuyer(5003);
        Intent unpaid = intent(closeToken, "close", "refactor-close-page.html");
        phase("WAIT_CLOSE_CONFIRMATION");
        deadline = System.nanoTime() + Duration.ofMinutes(10).toNanos();
        while (!Files.exists(DIRECTORY.resolve("allow-close.signal")) && System.nanoTime() < deadline) {
            Thread.sleep(500);
        }
        assertTrue(Files.exists(DIRECTORY.resolve("allow-close.signal")), "等待确认步骤超时");
        assertEquals(PaymentGateway.State.WAITING, gateway.query(unpaid.paymentNo()).state(),
                "必须是渠道已创建的未付款交易，NOT_FOUND不能冒充真实关单");
        assertEquals(1, put("/api/user/orders/" + unpaid.orderId() + "/cancel", closeToken).path("code").asInt());
        assertEquals(PaymentGateway.State.CLOSED, gateway.query(unpaid.paymentNo()).state());
        assertEquals(2, orderStatus(unpaid.orderId()));
        assertInventory();
        phase("PASSED");
        Files.writeString(DIRECTORY.resolve("refactor-acceptance-summary.txt"),
                "normalCallback=passed\nduplicateCallback=passed\nlatePaymentRefund=passed\nrefundQuery=passed\nrealChannelClose=passed\ninventory=passed\n");
    }

    record Intent(long orderId, String paymentNo) {
    }

    private String loginBuyer(long id) {
        String address = "sandbox-" + id + "@example.invalid";
        db.update("INSERT INTO user(id,username,password,email,status,role) VALUES(?,?,?,?,1,'USER')",
                id, "sandbox-" + id, encoder.encode("sandbox-test-pass"), address);
        JsonNode result = post("/api/auth/user/login-or-register",
                Map.of("email", address, "password", "sandbox-test-pass"), null);
        assertEquals(1, result.path("code").asInt());
        return result.path("data").path("token").asText();
    }

    private Intent intent(String token, String key, String pageName) throws Exception {
        JsonNode accepted = post("/api/user/seckill", Map.of("tierId", 5001, "quantity", 1,
                "requestKey", "sandbox-" + key), token);
        assertEquals(1, accepted.path("code").asInt(), accepted.toString());
        long orderId = awaitOrder(accepted.path("data").path("requestId").asLong(), token);
        JsonNode created = post("/api/user/payments/create", Map.of("orderId", orderId, "payType", 1), token);
        assertEquals(1, created.path("code").asInt(), created.toString());
        String form = created.path("data").path("qrCode").asText();
        assertTrue(form.contains("<form"), "渠道未返回付款表单");
        Files.writeString(DIRECTORY.resolve(pageName), form);
        return new Intent(orderId, created.path("data").path("paymentNo").asText());
    }

    private void awaitPaidCallback(String paymentNo) throws Exception {
        long deadline = System.nanoTime() + Duration.ofMinutes(10).toNanos();
        while (System.nanoTime() < deadline) {
            int count = db.queryForObject("SELECT COUNT(*) FROM payment_notify_log WHERE payment_no=? AND process_status=1", Integer.class, paymentNo);
            if (count > 0) {
                return;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("未收到处理成功的真实异步通知，不能用回跳或模拟回调代替");
    }

    private void replayCapturedNotification(String paymentNo) {
        Map<String, String> captured = CapturedCallbacks.values.get(paymentNo);
        assertTrue(captured != null && captured.containsKey("sign"), "未捕获真实渠道签名通知");
        LinkedMultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        captured.forEach(body::add);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        assertEquals("success", http.postForObject("/api/public/payments/notify", new HttpEntity<>(body, headers), String.class));
    }

    private String refundStatus(String paymentNo) {
        var states = db.queryForList("SELECT status FROM refund_record WHERE payment_no=?", String.class, paymentNo);
        return states.isEmpty() ? "MISSING" : states.getFirst();
    }

    private int orderStatus(long orderId) {
        return db.queryForObject("SELECT order_status FROM order_info WHERE id=?", Integer.class, orderId);
    }

    private JsonNode put(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("ticket-token", token);
        return http.exchange(path, HttpMethod.PUT, new HttpEntity<>(headers), JsonNode.class).getBody();
    }

    private void phase(String value) throws Exception {
        Files.writeString(DIRECTORY.resolve("refactor-phase.txt"), value);
    }

    private void assertInventory() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        var stock = application.getBean(StockServiceImpl.class);
        while (System.nanoTime() < deadline) {
            int held = db.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM stock_reservation WHERE tier_id=5001 AND inventory_state='HELD'", Integer.class);
            int available = db.queryForObject("SELECT available_stock FROM ticket_tier WHERE id=5001", Integer.class);
            if (held + available == 4 && stock.getStock(5001L) == available) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("库存未在30秒内收敛");
    }

    @Autowired
    ApplicationContext application;

    @TestConfiguration
    static class CapturedCallbacks {
        static final Map<String, Map<String, String>> values = new ConcurrentHashMap<>();

        @Bean
        OncePerRequestFilter captureSignedNotification() {
            return new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request,
                        HttpServletResponse response, FilterChain chain)
                        throws ServletException, IOException {
                    if (request.getRequestURI().equals("/api/public/payments/notify")) {
                        Map<String, String> captured = new HashMap<>();
                        request.getParameterMap().forEach((key, value) -> captured.put(key, value[0]));
                        if (captured.get("out_trade_no") != null) {
                            values.put(captured.get("out_trade_no"), captured);
                        }
                    }
                    chain.doFilter(request, response);
                }
            };
        }
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
                + "total_stock,available_stock,status) VALUES(5001,5001,5001,'Sandbox tier',0.01,0.01,4,4,1)");
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
