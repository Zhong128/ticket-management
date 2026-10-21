package org.example.ticketmanagement.trade.messaging;

import com.alipay.api.AlipayClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.example.ticketmanagement.TicketManagementApplication;
import org.example.ticketmanagement.constant.SeckillConstant;
import org.example.ticketmanagement.controller.TestDataController;
import org.example.ticketmanagement.service.EmailService;
import org.example.ticketmanagement.trade.core.TradeOperations;
import org.example.ticketmanagement.trade.payment.PaymentGateway;
import org.example.ticketmanagement.trade.payment.RefundService;
import org.example.ticketmanagement.util.AlipaySignatureUtil;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.*;
import java.util.Map;
import java.util.function.BooleanSupplier;

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

    @MockitoBean
    AlipaySignatureUtil signature;

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

    @Autowired
    RefundService refunds;

    @Autowired
    TradeRecovery recovery;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void completeContextMigratesBeforeStartupAndServesPublicHttp() {
        assertNotNull(context.getBean(TradeOperations.class));
        assertNotNull(context.getBean(OutboxPublisher.class));
        assertNotNull(context.getBean(TradeEventConsumer.class));
        assertTrue(context.getBeansOfType(TestDataController.class).isEmpty());
        assertEquals(1, context.getBeansOfType(Clock.class).size());
        assertEquals(4, db.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class));
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

    @Test
    void fullSaleJourneyCoversPaymentCancellationTimeoutAndRefund() throws Exception {
        when(email.sendSimpleEmail(anyString(), anyString(), anyString())).thenReturn(true);
        when(gateway.create(ArgumentMatchers.any())).thenReturn("isolated-payment-page");
        when(signature.verifyAlipaySignature(ArgumentMatchers.anyMap())).thenReturn(true);

        // 1. 只准备管理接口所依赖的分类、城市及测试账号。
        LocalDateTime now = LocalDateTime.now();
        db.update("INSERT INTO city(id,name,code,province) VALUES(3001,'Journey city','JOURNEY','Test province')");
        db.update("INSERT INTO event_category(id,name) VALUES(3001,'Journey category')");
        seedAccount(3000, "ADMIN");
        for (long id = 3001; id <= 3005; id++) {
            seedAccount(id, "USER");
        }

        String adminToken = loginAdmin(3000);
        assertSuccess(post("/api/admin/events", Map.of(
                "name", "Journey event", "categoryId", 3001, "cityId", 3001,
                "venue", "Journey venue", "status", 1,
                "saleStartTime", now.minusDays(1).toString(),
                "saleEndTime", now.plusDays(1).toString(),
                "eventStartTime", now.plusDays(2).toString(),
                "eventEndTime", now.plusDays(2).plusHours(3).toString()), adminToken));
        long eventId = db.queryForObject("SELECT id FROM event WHERE name='Journey event'", Long.class);

        assertSuccess(post("/api/admin/sessions", Map.of(
                "eventId", eventId, "sessionName", "Journey session",
                "sessionTime", now.plusDays(2).plusHours(1).toString(), "status", 1), adminToken));
        long sessionId = db.queryForObject("SELECT id FROM event_session WHERE session_name='Journey session'",
                Long.class);

        assertSuccess(post("/api/admin/ticket-tiers", Map.of(
                "eventId", eventId, "sessionId", sessionId, "tierName", "Journey tier",
                "originalPrice", 100, "currentPrice", 100,
                "totalStock", 10, "availableStock", 10, "status", 1), adminToken));
        long tierId = db.queryForObject("SELECT id FROM ticket_tier WHERE tier_name='Journey tier'", Long.class);

        // 2. 用户登录、抢票，等待真实 RabbitMQ 消费者异步建单。
        String paidToken = loginUser(3001);
        long paidOrder = reserveAndAwaitOrder(paidToken, tierId, 2, "journey-paid");
        assertEquals(8, stock(tierId));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM order_info WHERE id=? AND order_status=0",
                Integer.class, paidOrder));

        // 通过用户和管理端接口核对新订单的可见范围与详情。
        JsonNode userOrders = get("/api/user/orders/page?page=1&size=10", paidToken);
        assertSuccess(userOrders);
        assertEquals(1, userOrders.path("data").path("total").asInt());
        assertEquals(paidOrder, userOrders.path("data").path("records").get(0).path("id").asLong());

        JsonNode userOrder = get("/api/user/orders/" + paidOrder, paidToken);
        assertSuccess(userOrder);
        assertEquals(3001, userOrder.path("data").path("userId").asLong());
        assertEquals(2, userOrder.path("data").path("quantity").asInt());
        assertEquals(0, userOrder.path("data").path("orderStatus").asInt());

        JsonNode adminOrder = get("/api/admin/orders/" + paidOrder, adminToken);
        assertSuccess(adminOrder);
        assertEquals(paidOrder, adminOrder.path("data").path("id").asLong());

        // 3. 通过真实支付接口建流水，再用测试回调完成付款。
        String paidNo = createPayment(paidToken, paidOrder);
        assertEquals("success", notifyPaid(paidNo, "ALIPAY-JOURNEY-PAID", "200.00"));
        assertEquals("success", notifyPaid(paidNo, "ALIPAY-JOURNEY-PAID", "200.00"));
        assertEquals(1, orderStatus(paidOrder));
        assertEquals(2, paymentStatus(paidNo));
        assertEquals("PAID", reservationStatus(paidOrder));
        assertEquals(8, stock(tierId));
        JsonNode paidOrderDetail = get("/api/user/orders/" + paidOrder, paidToken);
        assertSuccess(paidOrderDetail);
        assertEquals(1, paidOrderDetail.path("data").path("orderStatus").asInt());

        when(gateway.query(paidNo)).thenReturn(new PaymentGateway.Query(
                PaymentGateway.State.PAID, paidNo, "ALIPAY-JOURNEY-PAID", new BigDecimal("200.00")));
        JsonNode paymentDetail = get("/api/user/payments/status/" + paidNo, paidToken);
        assertSuccess(paymentDetail);
        assertEquals(paidNo, paymentDetail.path("data").path("paymentNo").asText());
        assertEquals(2, paymentDetail.path("data").path("payStatus").asInt());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM refund_record WHERE payment_no=?",
                Integer.class, paidNo));

        // 4. 另一用户取消订单，渠道确认关单后库存只回补一次。
        String cancelToken = loginUser(3002);
        long cancelOrder = reserveAndAwaitOrder(cancelToken, tierId, 1, "journey-cancel");
        String cancelNo = createPayment(cancelToken, cancelOrder);
        when(gateway.query(cancelNo)).thenReturn(new PaymentGateway.Query(
                PaymentGateway.State.WAITING, cancelNo, null, null));
        when(gateway.close(cancelNo)).thenReturn(true);
        assertSuccess(put("/api/user/orders/" + cancelOrder + "/cancel", cancelToken));
        assertEquals(2, orderStatus(cancelOrder));
        assertEquals("RELEASED", reservationStatus(cancelOrder));
        assertEquals(8, stock(tierId));
        assertEquals(4, paymentStatus(cancelNo));

        // 5. 渠道状态未知时先保留库存，明确关闭后再取消。
        String unknownToken = loginUser(3003);
        long unknownOrder = reserveAndAwaitOrder(unknownToken, tierId, 1, "journey-unknown");
        String unknownNo = createPayment(unknownToken, unknownOrder);
        when(gateway.query(unknownNo)).thenReturn(PaymentGateway.Query.unknown());
        assertEquals(0, put("/api/user/orders/" + unknownOrder + "/cancel", unknownToken).path("code").asInt());
        assertEquals(4, orderStatus(unknownOrder));
        assertEquals(7, stock(tierId));

        when(gateway.query(unknownNo)).thenReturn(new PaymentGateway.Query(
                PaymentGateway.State.CLOSED, unknownNo, null, null));
        assertSuccess(put("/api/user/orders/" + unknownOrder + "/cancel", unknownToken));
        assertEquals(2, orderStatus(unknownOrder));
        assertEquals(8, stock(tierId));

        // 6. 在隔离库中推进订单截止时间，触发恢复任务处理无支付流水的超时订单。
        String timeoutToken = loginUser(3004);
        long timeoutOrder = reserveAndAwaitOrder(timeoutToken, tierId, 1, "journey-timeout");
        db.update("UPDATE stock_reservation SET expires_at=? WHERE order_id=?", now.minusMinutes(1), timeoutOrder);
        db.update("UPDATE order_info SET expire_time=? WHERE id=?", now.minusMinutes(1), timeoutOrder);
        recovery.recover();
        assertEquals(2, orderStatus(timeoutOrder));
        assertEquals("RELEASED", reservationStatus(timeoutOrder));
        assertEquals(8, stock(tierId));

        // 7. 已取消订单收到迟到付款，生成退款任务并完成测试网关退款。
        String lateToken = loginUser(3005);
        long lateOrder = reserveAndAwaitOrder(lateToken, tierId, 1, "journey-late");
        String lateNo = createPayment(lateToken, lateOrder);
        when(gateway.query(lateNo)).thenReturn(new PaymentGateway.Query(
                PaymentGateway.State.CLOSED, lateNo, null, null));
        assertSuccess(put("/api/user/orders/" + lateOrder + "/cancel", lateToken));
        assertEquals("success", notifyPaid(lateNo, "ALIPAY-JOURNEY-LATE", "100.00"));
        assertEquals(2, orderStatus(lateOrder));
        assertEquals(8, stock(tierId));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM refund_record WHERE payment_no=?",
                Integer.class, lateNo));

        when(gateway.queryRefund(ArgumentMatchers.eq(lateNo), anyString(),
                ArgumentMatchers.eq(new BigDecimal("100.00"))))
                .thenReturn(new PaymentGateway.RefundResult(PaymentGateway.RefundState.NOT_FOUND, null, null));
        when(gateway.refund(ArgumentMatchers.eq(lateNo), anyString(),
                ArgumentMatchers.eq(new BigDecimal("100.00"))))
                .thenReturn(new PaymentGateway.RefundResult(PaymentGateway.RefundState.SUCCESS,
                        "ALIPAY-JOURNEY-LATE", null));
        refunds.processDueRefunds();

        JsonNode userRefunds = get("/api/user/refunds", lateToken);
        assertSuccess(userRefunds);
        assertEquals(1, userRefunds.path("data").size());
        assertEquals("SUCCESS", userRefunds.path("data").get(0).path("status").asText());
        assertEquals(5, paymentStatus(lateNo));
        assertEquals(8, stock(tierId));

        // 8. 等待消息和缓存投影收敛，再核对最终订单、库存与通知任务。
        awaitUntil(() -> "8".equals(redis.opsForValue().get(SeckillConstant.STOCK_KEY_PREFIX + tierId)));
        awaitUntil(() -> db.queryForObject(
                "SELECT COUNT(*) FROM message_outbox WHERE business_key=? AND publish_status='PUBLISHED'",
                Integer.class, "PAYMENT_SUCCESS:" + paidNo) == 1);
        awaitUntil(() -> db.queryForObject(
                "SELECT COUNT(*) FROM payment_compensation WHERE order_id=? AND task_type='EMAIL_PAYMENT' "
                        + "AND task_status=2", Integer.class, paidOrder) == 1);
        assertEquals(5, db.queryForObject("SELECT COUNT(*) FROM order_info WHERE tier_id=?", Integer.class, tierId));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM stock_reservation "
                + "WHERE tier_id=? AND status IN ('RESERVED','ORDER_CREATED')", Integer.class, tierId));
    }

    private void seedAccount(long id, String role) {
        db.update("INSERT INTO user(id,username,password,email,status,role) VALUES(?,?,?,?,1,?)",
                id, "journey-" + id, encoder.encode("journey-test-pass"),
                "journey-" + id + "@example.invalid", role);
    }

    private String loginAdmin(long id) {
        JsonNode result = post("/api/auth/admin/login",
                Map.of("username", "journey-" + id, "password", "journey-test-pass"), null);
        assertSuccess(result);
        return result.path("data").path("token").asText();
    }

    private String loginUser(long id) {
        JsonNode result = post("/api/auth/user/login-or-register",
                Map.of("email", "journey-" + id + "@example.invalid", "password", "journey-test-pass"), null);
        assertSuccess(result);
        return result.path("data").path("token").asText();
    }

    private long reserveAndAwaitOrder(String token, long tierId, int quantity, String requestKey)
            throws InterruptedException {
        JsonNode accepted = post("/api/user/seckill",
                Map.of("tierId", tierId, "quantity", quantity, "requestKey", requestKey), token);
        assertSuccess(accepted);
        long requestId = accepted.path("data").path("requestId").asLong();
        assertTrue(requestId > 0);

        long limit = System.nanoTime() + Duration.ofSeconds(25).toNanos();
        while (System.nanoTime() < limit) {
            JsonNode request = get("/api/user/seckill/requests/" + requestId, token);
            assertSuccess(request);
            if ("ORDER_CREATED".equals(request.path("data").path("requestStatus").asText())) {
                return request.path("data").path("orderId").asLong();
            }
            Thread.sleep(100);
        }
        fail("订单未在25秒内创建，requestId=" + requestId);
        return 0;
    }

    private String createPayment(String token, long orderId) {
        JsonNode result = post("/api/user/payments/create", Map.of("orderId", orderId, "payType", 1), token);
        assertSuccess(result);
        assertEquals("isolated-payment-page", result.path("data").path("qrCode").asText());
        String paymentNo = result.path("data").path("paymentNo").asText();
        assertFalse(paymentNo.isBlank());
        return paymentNo;
    }

    private String notifyPaid(String paymentNo, String tradeNo, String amount) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("app_id", "isolated-it");
        form.add("out_trade_no", paymentNo);
        form.add("trade_no", tradeNo);
        form.add("total_amount", amount);
        form.add("trade_status", "TRADE_SUCCESS");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> response = http.exchange("/api/public/payments/notify", HttpMethod.POST,
                new HttpEntity<>(form, headers), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    private JsonNode get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("ticket-token", token);
        ResponseEntity<JsonNode> response = http.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private JsonNode put(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("ticket-token", token);
        ResponseEntity<JsonNode> response = http.exchange(path, HttpMethod.PUT,
                new HttpEntity<>(headers), JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private int stock(long tierId) {
        return db.queryForObject("SELECT available_stock FROM ticket_tier WHERE id=?", Integer.class, tierId);
    }

    private int orderStatus(long orderId) {
        return db.queryForObject("SELECT order_status FROM order_info WHERE id=?", Integer.class, orderId);
    }

    private int paymentStatus(String paymentNo) {
        return db.queryForObject("SELECT pay_status FROM payment_record WHERE payment_no=?", Integer.class, paymentNo);
    }

    private String reservationStatus(long orderId) {
        return db.queryForObject("SELECT status FROM stock_reservation WHERE order_id=?", String.class, orderId);
    }

    private void assertSuccess(JsonNode result) {
        assertEquals(1, result.path("code").asInt(), result.toString());
    }

    private void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long limit = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < limit) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        fail("异步消息或库存投影未在10秒内收敛");
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
