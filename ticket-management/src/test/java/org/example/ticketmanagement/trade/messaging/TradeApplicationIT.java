package org.example.ticketmanagement.trade.messaging;

import org.example.ticketmanagement.exception.OrderCreationDeferredException;
import org.example.ticketmanagement.serviceimpl.OrderCreationCoordinator;
import org.example.ticketmanagement.util.RedissonLockUtil;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.nio.file.Path;
import java.nio.file.Files;
import com.alipay.api.AlipayClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.AdditionalAnswers;
import org.example.ticketmanagement.TicketManagementApplication;
import org.example.ticketmanagement.constant.SeckillConstant;
import org.example.ticketmanagement.controller.TestDataController;
import org.example.ticketmanagement.service.EmailService;
import org.example.ticketmanagement.service.ReservationService;
import org.example.ticketmanagement.trade.payment.PaymentGateway;
import org.example.ticketmanagement.trade.payment.RefundService;
import org.example.ticketmanagement.dto.RedisReservation;
import org.example.ticketmanagement.serviceimpl.ReservationServiceImpl;
import org.example.ticketmanagement.serviceimpl.StockServiceImpl;
import org.example.ticketmanagement.serviceimpl.OrderRecoveryService;
import org.example.ticketmanagement.serviceimpl.OrderServiceImpl;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ChannelProxy;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.example.ticketmanagement.util.LuaScriptLoader;
import org.example.ticketmanagement.util.RedisUtil;
import org.example.ticketmanagement.pojo.StockReservation;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.test.util.AopTestUtils;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.example.ticketmanagement.consumer.SeckillOrderConsumer;
import org.example.ticketmanagement.util.AlipaySignatureUtil;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.mockito.ArgumentCaptor;
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
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @MockitoSpyBean
    TransactionTemplate transactions;

    @MockitoSpyBean
    OutboxStore durableEvents;

    @MockitoSpyBean
    OrderServiceImpl orderBusiness;

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
    OrderRecoveryService recovery;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void bundledFrontendRefreshDoesNotCaptureApisOrMissingAssets() {
        for (String path : List.of("/", "/events/7501", "/account/orders/7501", "/payment/return", "/admin", "/admin/events/1/edit", "/unknown-page")) {
            var response = http.getForEntity(path, String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode(), path);
            assertTrue(response.getBody().contains("youxi-frontend-routing-fixture"), path);
        }
        var asset = http.getForEntity("/assets/no-such-module.js", String.class);
        assertFalse(asset.getBody().contains("youxi-frontend-routing-fixture"));
        var api = http.getForEntity("/api/public/no-such-api", String.class);
        assertFalse(api.getBody().contains("youxi-frontend-routing-fixture"));
        JsonNode status = http.getForObject("/api/public/app-status", JsonNode.class);
        assertSuccess(status);
        assertTrue(status.path("data").path("ready").asBoolean());
        assertTrue(status.path("data").path("tradingEnabled").asBoolean());
    }

    @Test
    void frontendBrowserCompletesRealPurchaseAndAccountJourney() throws Exception {
        seedProtocolSale(7501, 5);
        seedAccount(7501, "USER");
        seedAccount(7502, "USER");
        db.update("UPDATE event SET name='隔离联调音乐会' WHERE id=7501");
        when(email.sendSimpleEmail(anyString(), anyString(), anyString())).thenReturn(true);
        when(gateway.create(ArgumentMatchers.any())).thenReturn("""
                <form method="post" action="https://openapi-sandbox.dl.alipaydev.com/gateway.do">
                  <input type="hidden" name="biz_content" value="isolated-acceptance">
                </form>
                """);
        when(gateway.query(anyString())).thenAnswer(invocation -> new PaymentGateway.Query(
                PaymentGateway.State.PAID, invocation.getArgument(0), "ISOLATED-BROWSER-PAID",
                new BigDecimal("200.00")));
        when(ai.call(ArgumentMatchers.any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("隔离验收客服回复")))));

        // 独立Vite只代理本测试的随机端口，浏览器不得拦截任何业务API。
        Path root = Path.of(System.getProperty("user.dir")).getParent();
        Path browserLog = root.resolve("output/frontend-live-browser.log");
        Files.createDirectories(browserLog.getParent());
        ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/d", "/c", "npm.cmd", "run", "test:e2e:live", "--", "purchase-flow.spec.ts");
        builder.directory(root.resolve("frontend").toFile());
        builder.environment().put("API_PROXY_TARGET", http.getRootUri());
        builder.environment().put("LIVE_ISOLATED_ACCEPTANCE", "true");
        builder.environment().put("LIVE_EVENT_ID", "7501");
        builder.redirectErrorStream(true);
        builder.redirectOutput(browserLog.toFile());
        Process browser = builder.start();
        try {
            assertTrue(browser.waitFor(150, TimeUnit.SECONDS), "浏览器验收超时，见 " + browserLog);
            assertEquals(0, browser.exitValue(), Files.readString(browserLog));
        } finally {
            if (browser.isAlive()) {
                browser.descendants().forEach(ProcessHandle::destroy);
                browser.destroyForcibly();
            }
        }

        // 浏览器结果还需由真实数据库与网关调用独立核对。
        assertEquals(1, db.queryForObject(
                "SELECT order_status FROM order_info WHERE user_id=7501", Integer.class));
        assertEquals(2, db.queryForObject(
                "SELECT order_status FROM order_info WHERE user_id=7502", Integer.class));
        assertEquals(3, stock(7501));
        assertEquals("真实联调现场朋友", db.queryForObject(
                "SELECT nickname FROM user WHERE id=7501", String.class));
        assertTrue(encoder.matches("browser-new-pass", db.queryForObject(
                "SELECT password FROM user WHERE id=7501", String.class)));
        assertEquals(1, db.queryForObject(
                "SELECT COUNT(*) FROM user_address WHERE user_id=7501 AND is_default=1", Integer.class));
        verify(gateway, times(1)).create(ArgumentMatchers.any());
        verify(ai, times(1)).call(ArgumentMatchers.any(Prompt.class));
        assertTrue(context.getBean(ChatMemory.class).get("user_7502").isEmpty());
    }

    @Test
    void accountPagesSaveThroughRealMappersAndRejectForeignAddresses() {
        seedAccount(7301, "USER");
        seedAccount(7302, "USER");
        String token = loginUser(7301);
        String other = loginUser(7302);

        // 部分资料更新保留原邮箱、密码、状态和角色，仍可继续登录。
        assertSuccess(accountWrite(HttpMethod.PUT, "/api/user/profile",
                Map.of("nickname", "现场朋友", "realName", "小林"), token, MediaType.APPLICATION_JSON));
        JsonNode profile = get("/api/user/profile", token);
        assertSuccess(profile);
        assertEquals("现场朋友", profile.path("data").path("nickname").asText());
        assertEquals("journey-7301@example.invalid", profile.path("data").path("email").asText());
        assertEquals(1, db.queryForObject("SELECT status FROM user WHERE id=7301", Integer.class));
        assertFalse(profile.path("data").has("password"));

        Map<String, Object> address = Map.of("receiverName", "小林", "receiverPhone", "13800138000",
                "province", "北京市", "city", "北京市", "district", "海淀区", "detailAddress", "现场路1号");
        assertSuccess(post("/api/user/address", address, token));
        JsonNode rows = get("/api/user/address", token);
        long id = rows.path("data").get(0).path("id").asLong();
        assertSuccess(post("/api/user/address/" + id + "/default", Map.of(), token));
        assertSuccess(accountWrite(HttpMethod.PUT, "/api/user/address/" + id, address,
                token, MediaType.APPLICATION_JSON));
        assertEquals(1, get("/api/user/address/default", token).path("data").path("isDefault").asInt());
        assertNotEquals(1, get("/api/user/address/" + id, other).path("code").asInt());
        assertNotEquals(1, accountWrite(HttpMethod.PUT, "/api/user/address/" + id, address,
                other, MediaType.APPLICATION_JSON).path("code").asInt());
        assertNotEquals(1, accountWrite(HttpMethod.DELETE, "/api/user/address/" + id, null,
                other, MediaType.APPLICATION_JSON).path("code").asInt());
        assertNotEquals(1, accountWrite(HttpMethod.DELETE, "/api/user/address/" + id, null,
                token, MediaType.APPLICATION_JSON).path("code").asInt(), "默认地址不能删除");
        assertEquals(1, get("/api/user/address", token).path("data").size());

        // 密码通过表单请求体发送，实际BCrypt写入后用新密码再次登录。
        MultiValueMap<String, String> passwords = new LinkedMultiValueMap<>();
        passwords.add("oldPassword", "journey-test-pass");
        passwords.add("newPassword", "changed-pass-42");
        assertSuccess(accountWrite(HttpMethod.PUT, "/api/user/password", passwords,
                token, MediaType.APPLICATION_FORM_URLENCODED));
        assertSuccess(post("/api/auth/user/login-or-register",
                Map.of("email", "journey-7301@example.invalid", "password", "changed-pass-42"), null));
        assertNotEquals(1, post("/api/auth/user/login-or-register",
                Map.of("email", "journey-7301@example.invalid", "password", "journey-test-pass"), null)
                .path("code").asInt());
        // 用改密前已打开表单的旧资料再保存，SQL不得覆盖最新密码和账户状态。
        assertSuccess(accountWrite(HttpMethod.PUT, "/api/user/profile",
                Map.of("nickname", "改密后继续保存资料"), token, MediaType.APPLICATION_JSON));
        String latestHash = db.queryForObject("SELECT password FROM user WHERE id=7301", String.class);
        assertTrue(encoder.matches("changed-pass-42", latestHash));
        assertEquals(1, db.queryForObject("SELECT status FROM user WHERE id=7301", Integer.class));
    }

    @Test
    void customerChatSeparatesUsersAndClearDoesNotCallModel() {
        seedAccount(7401, "USER");
        seedAccount(7402, "USER");
        String first = loginUser(7401);
        String second = loginUser(7402);
        when(ai.call(ArgumentMatchers.any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("测试回复")))));

        JsonNode firstReply = post("/api/user/ai-customer-service/chat",
                Map.of("message", "first-user-marker"), first);
        assertSuccess(firstReply);
        assertEquals("测试回复", firstReply.path("data").asText(), "回复正文必须放入data而不是msg");
        assertSuccess(post("/api/user/ai-customer-service/chat", Map.of("message", "second-user-marker"), second));
        assertSuccess(post("/api/user/ai-customer-service/chat", Map.of("message", "follow-up-marker"), first));
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(ai, times(3)).call(prompts.capture());
        assertFalse(prompts.getAllValues().get(1).getInstructions().stream()
                .anyMatch(message -> message.getText().contains("first-user-marker")));
        assertTrue(prompts.getAllValues().get(2).getInstructions().stream()
                .anyMatch(message -> message.getText().contains("first-user-marker")));
        var options = (ToolCallingChatOptions) prompts.getAllValues().get(0).getOptions();
        assertTrue(options.getToolCallbacks().stream().noneMatch(tool ->
                Set.of("updateUserProfile", "addUserAddress", "createOrder", "cancelOrder")
                        .contains(tool.getToolDefinition().name())));

        // 清空仅操作该用户的内存，不再额外请求模型。
        assertSuccess(accountWrite(HttpMethod.DELETE, "/api/user/ai-customer-service/clear-history",
                null, first, MediaType.APPLICATION_JSON));
        ChatMemory memory = context.getBean(ChatMemory.class);
        assertTrue(memory.get("user_7401").isEmpty());
        assertFalse(memory.get("user_7402").isEmpty());
        verify(ai, times(3)).call(ArgumentMatchers.any(Prompt.class));
        assertNotEquals(1, post("/api/user/ai-customer-service/chat", Map.of("message", " "), first)
                .path("code").asInt());
        verify(ai, times(3)).call(ArgumentMatchers.any(Prompt.class));
    }

    private JsonNode accountWrite(HttpMethod method, String path, Object body, String token, MediaType type) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("ticket-token", token);
        headers.setContentType(type);
        var response = http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    @Test
    void registrationKeepsChosenPasswordAndCanLoginAgainOverHttp() {
        String address = "frontend-registration@example.invalid";
        String password = "frontend-test-pass";
        when(email.sendVerificationCodeEmail(ArgumentMatchers.eq(address), anyString())).thenReturn(true);

        // 邮件被替换，验证码仍由真实服务写入隔离Redis并由HTTP接口核验。
        JsonNode challenge = post("/api/auth/user/login-or-register",
                Map.of("email", address, "password", password), null);
        assertEquals(200, challenge.path("code").asInt());
        String code = context.getBean(RedisUtil.class).getString("verification:code:" + address);
        assertNotNull(code);
        JsonNode registered = post("/api/auth/user/verify-registration",
                Map.of("email", address, "code", code, "password", password), null);
        assertSuccess(registered);
        assertFalse(registered.path("data").path("token").asText().isBlank());
        String stored = db.queryForObject("SELECT password FROM user WHERE email=?", String.class, address);
        assertTrue(encoder.matches(password, stored));

        // 后续登录必须能使用用户选定的原密码。
        JsonNode loggedIn = post("/api/auth/user/login-or-register",
                Map.of("email", address, "password", password), null);
        assertSuccess(loggedIn);
        assertEquals(registered.path("data").path("id"), loggedIn.path("data").path("id"));
    }

    @Test
    void completeContextMigratesBeforeStartupAndServesPublicHttp() {
        assertNotNull(context.getBean(ReservationService.class));
        assertNotNull(context.getBean(OutboxPublisher.class));
        assertNotNull(context.getBean(SeckillOrderConsumer.class));
        assertTrue(context.getBeansOfType(TestDataController.class).isEmpty());
        assertEquals(1, context.getBeansOfType(Clock.class).size());
        assertEquals(5, db.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class));
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
    void acceptedRedisReservationDoesNotDeductMysqlBeforeOrderConsumerRuns() {
        var listeners = context.getBean(RabbitListenerEndpointRegistry.class);
        LocalDateTime now = LocalDateTime.now();
        db.update("INSERT INTO city(id,name,code,province) VALUES(4301,'Quota city','QUOTA','Test')");
        db.update("INSERT INTO event_category(id,name) VALUES(4301,'Quota category')");
        db.update("""
                INSERT INTO event(id,name,category_id,city_id,venue,status,sale_start_time,sale_end_time,
                    event_start_time,event_end_time)
                VALUES(4301,'Quota sale',4301,4301,'Test',1,?,?,?,?)
                """, now.minusDays(1), now.plusDays(1), now.plusDays(2), now.plusDays(3));
        db.update("""
                INSERT INTO event_session(id,event_id,session_name,session_time,status)
                VALUES(4301,4301,'Quota session',?,1)
                """, now.plusDays(2));
        db.update("""
                INSERT INTO ticket_tier(id,event_id,session_id,tier_name,original_price,current_price,
                    total_stock,available_stock,status)
                VALUES(4301,4301,4301,'Quota tier',100,100,10,10,1)
                """);
        seedAccount(4301, "USER");
        String token = loginUser(4301);

        // 暂停真实消费者，只观察可靠受理阶段，不能用异步时序碰巧通过。
        listeners.stop();
        try {
            JsonNode accepted = post("/api/user/seckill",
                    Map.of("tierId", 4301, "quantity", 2, "requestKey", "quota-only"), token);
            assertSuccess(accepted);
            assertEquals(10, stock(4301), "入口不能扣 MySQL 票档库存");
            assertEquals("PENDING", db.queryForObject(
                    "SELECT inventory_state FROM stock_reservation WHERE id=?", String.class,
                    accepted.path("data").path("requestId").asLong()));
            String epoch = redis.<String, String>opsForHash().get("seckill:{4301}:meta", "epoch");
            assertEquals("8", redis.opsForValue().get("seckill:{4301}:quota:" + epoch));
            JsonNode replay = post("/api/user/seckill",
                    Map.of("tierId", 4301, "quantity", 2, "requestKey", "quota-only"), token);
            assertEquals(accepted.path("data").path("requestId"), replay.path("data").path("requestId"));
            assertEquals("8", redis.opsForValue().get("seckill:{4301}:quota:" + epoch));
        } finally {
            listeners.start();
        }
    }

    @Test
    void rejectedOrphanCannotBeAcceptedByALateThread() {
        seedProtocolSale(4101, 3);
        StockServiceImpl stockService = context.getBean(StockServiceImpl.class);
        ReservationServiceImpl application = context.getBean(ReservationServiceImpl.class);
        RedisReservation orphan = stockService.reserve(4101, 4101, 2, "orphan");
        assertEquals(1, stockService.getStock(4101L));
        assertEquals("REJECTED", application.sealRejected(orphan).getAdmissionStatus());
        stockService.release(orphan);
        assertThrows(IllegalStateException.class, () -> application.accept(orphan));
        stockService.release(orphan);
        assertEquals(3, stockService.getStock(4101L));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM stock_reservation WHERE tier_id=4101", Integer.class));
    }

    @Test
    void rebuildSubtractsPendingAndOldReleaseCannotAddQuota() {
        seedProtocolSale(4201, 5);
        StockServiceImpl stockService = context.getBean(StockServiceImpl.class);
        ReservationServiceImpl application = context.getBean(ReservationServiceImpl.class);
        var listeners = context.getBean(RabbitListenerEndpointRegistry.class);
        listeners.stop();
        try {
            RedisReservation original = stockService.reserve(4201, 4201, 2, "pending-rebuild");
            var accepted = application.accept(original);
            stockService.confirm(original);
            assertEquals(5, stock(4201));
            long rebuilt = stockService.rebuild(4201);
            assertTrue(rebuilt > original.epoch());
            assertEquals(3, stockService.getStock(4201L));
            assertFalse(redis.hasKey(StockServiceImpl.keys(4201, original.epoch()).get(1)),
                    "新代次生效后应退役旧配额，不能反复重建后永久累积");
            assertFalse(redis.hasKey(StockServiceImpl.keys(4201, original.epoch()).get(4)),
                    "退役token不能永久复制保留");
            assertEquals(-1L, redis.getExpire(StockServiceImpl.keys(4201, rebuilt).get(2)),
                    "活跃代次限购占位不能靠TTL释放");
            stockService.release(original);
            assertEquals(3, stockService.getStock(4201L), "旧代次不能释放新配额");
            assertThrows(IllegalStateException.class, () -> stockService.reserve(4201, 4201, 1, "second"));
            assertEquals(rebuilt, application.find(accepted.getId()).getStockEpoch());
        } finally {
            listeners.start();
        }
    }

    private void seedProtocolSale(long id, int stock) {
        LocalDateTime now = LocalDateTime.now();
        db.update("INSERT INTO city(id,name,code,province) VALUES(?,'Protocol city',?,'Test')", id, "P" + id);
        db.update("INSERT INTO event_category(id,name) VALUES(?,'Protocol category')", id);
        db.update("""
                INSERT INTO event(id,name,category_id,city_id,venue,status,sale_start_time,sale_end_time,
                    event_start_time,event_end_time) VALUES(?,'Protocol sale',?,?,'Test',1,?,?,?,?)
                """, id, id, id, now.minusDays(1), now.plusDays(1), now.plusDays(2), now.plusDays(3));
        db.update("INSERT INTO event_session(id,event_id,session_name,session_time,status) VALUES(?,?,'Protocol',?,1)",
                id, id, now.plusDays(2));
        db.update("""
                INSERT INTO ticket_tier(id,event_id,session_id,tier_name,original_price,current_price,
                    total_stock,available_stock,status) VALUES(?,?,?,'Protocol tier',100,100,?,?,1)
                """, id, id, id, stock, stock);
    }

    @Test
    void realRedisAdmissionAndAsyncMysqlCreationCannotOversell() throws Exception {
        seedProtocolSale(4501, 3);
        var stockService = context.getBean(StockServiceImpl.class);
        stockService.prepare(4501);
        var applications = context.getBean(ReservationServiceImpl.class);
        var listeners = context.getBean(RabbitListenerEndpointRegistry.class);
        listeners.stop();
        List<Long> accepted = new ArrayList<>();
        try {
            CountDownLatch start = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(12)) {
                List<Future<Long>> attempts = new ArrayList<>();
                for (long buyer = 1; buyer <= 24; buyer++) {
                    long user = 450100 + buyer;
                    attempts.add(pool.submit(() -> {
                        start.await();
                        try {
                            return ((Number) applications.reserve(user, 4501, 1, "competition").get("requestId")).longValue();
                        } catch (IllegalStateException soldOutOrBusy) {
                            return null;
                        }
                    }));
                }
                start.countDown();
                for (Future<Long> attempt : attempts) {
                    Long id = attempt.get();
                    if (id != null) {
                        accepted.add(id);
                    }
                }
            }
            assertEquals(3, accepted.size());
            assertEquals(3, stock(4501));
            assertEquals(0, stockService.getStock(4501L));
            for (Long id : accepted) {
                Long order = applications.createOrder(id);
                assertEquals(order, applications.createOrder(id));
            }
            assertEquals(0, stock(4501));
            assertEquals(3, db.queryForObject("SELECT COUNT(*) FROM order_info WHERE tier_id=4501", Integer.class));
        } finally {
            listeners.start();
        }
    }

    @Test
    void busyOrderTierDefersBeforeDeductionAndCanResumeWithoutBusinessFailure() throws Exception {
        seedProtocolSale(6201, 4);
        var listeners = context.getBean(RabbitListenerEndpointRegistry.class);
        var applications = context.getBean(ReservationServiceImpl.class);
        var coordinator = context.getBean(OrderCreationCoordinator.class);
        var locks = context.getBean(RedissonLockUtil.class);
        var stockService = context.getBean(StockServiceImpl.class);
        stockService.prepare(6201);
        listeners.stop();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var workers = Executors.newSingleThreadExecutor()) {
            long id = ((Number) applications.reserve(620101, 6201, 1, "order-busy").get("requestId")).longValue();
            var holder = workers.submit(() -> coordinator.execute(6201, () -> {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("测试释放信号超时");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return 1;
            }));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                ReflectionTestUtils.setField(coordinator, "waitMillis", 20L);
                assertThrows(OrderCreationDeferredException.class,
                        () -> applications.createOrder(id));
                assertEquals(4, stock(6201));
                assertEquals("PENDING", db.queryForObject(
                        "SELECT inventory_state FROM stock_reservation WHERE id=?", String.class, id));
                assertEquals(0, db.queryForObject(
                        "SELECT attempts FROM stock_reservation WHERE id=?", Integer.class, id));
                release.countDown();
                holder.get(5, TimeUnit.SECONDS);

                // 释放协调锁后复用原申请，不能重复扣库存或重建订单。
                Long order = applications.createOrder(id);
                assertEquals(order, applications.createOrder(id));
                assertEquals(3, stock(6201));
                assertEquals(3, stockService.getStock(6201L));
                assertFalse(locks.getLock("order:tier:6201").isLocked());
            } finally {
                release.countDown();
                ReflectionTestUtils.setField(coordinator, "waitMillis", 2000L);
            }
        } finally {
            listeners.start();
        }
    }

    @Test
    void twoJvmApplicationsShareQuotaAndIdempotencyWithoutOverselling() throws Exception {
        seedProtocolSale(5101, 6);
        var stockService = context.getBean(StockServiceImpl.class);
        stockService.prepare(5101);
        Map<String, String> endpoints = new HashMap<>();
        for (String name : List.of("spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
                "spring.rabbitmq.host", "spring.rabbitmq.port", "spring.rabbitmq.username", "spring.rabbitmq.password",
                "spring.data.redis.host", "spring.data.redis.port", "redisson.config")) {
            endpoints.put(name, environment.getRequiredProperty(name));
        }
        try (var second = new IsolatedSecondApplication(endpoints, Path.of("target", "dual-application"))) {
            var other = new TestRestTemplate();
            List<String> tokens = new ArrayList<>();
            for (int i = 0; i < 18; i++) {
                long user = 510100 + i;
                seedAccount(user, "USER");
                tokens.add(loginUser(user));
            }

            // 两个独立JVM同时接收相同请求；每个买家的两次调用复用同一个请求键。
            try (var workers = Executors.newFixedThreadPool(12)) {
                List<Future<JsonNode>> calls = new ArrayList<>();
                CountDownLatch start = new CountDownLatch(1);
                for (int i = 0; i < tokens.size(); i++) {
                    String token = tokens.get(i);
                    for (int instance = 0; instance < 2; instance++) {
                        int target = instance;
                        calls.add(workers.submit(() -> {
                            start.await();
                            HttpHeaders headers = new HttpHeaders();
                            headers.setContentType(MediaType.APPLICATION_JSON);
                            headers.set("ticket-token", token);
                            var body = new HttpEntity<>(Map.of("tierId", 5101, "quantity", 2,
                                    "requestKey", "two-jvm"), headers);
                            return target == 0 ? http.postForObject("/api/user/seckill", body, JsonNode.class)
                                    : other.postForObject("http://127.0.0.1:" + second.port + "/api/user/seckill", body, JsonNode.class);
                        }));
                    }
                }
                start.countDown();
                int successfulResponses = 0;
                for (Future<JsonNode> call : calls) {
                    JsonNode response = call.get(30, TimeUnit.SECONDS);
                    assertNotNull(response);
                    if (response.path("code").asInt() == 1) {
                        successfulResponses++;
                    }
                }
                assertTrue(successfulResponses >= 3);
            }
            awaitUntil(() -> db.queryForObject("SELECT COUNT(*) FROM order_info WHERE tier_id=5101", Integer.class) == 3);
            assertEquals(3, db.queryForObject("SELECT COUNT(*) FROM stock_reservation WHERE tier_id=5101", Integer.class));
            assertEquals(3, db.queryForObject("SELECT COUNT(DISTINCT user_id) FROM stock_reservation WHERE tier_id=5101", Integer.class));
            assertEquals(0, stock(5101));
            assertEquals(0, stockService.getStock(5101L));
            assertEquals(6, db.queryForObject("SELECT SUM(quantity) FROM stock_reservation WHERE tier_id=5101 AND inventory_state='HELD'", Integer.class));
        }
    }

    @Test
    void brokerPauseRetainsAcceptedApplicationAndResumesDelivery() throws Exception {
        seedProtocolSale(5201, 2);
        context.getBean(StockServiceImpl.class).prepare(5201);
        seedAccount(5201, "USER");
        String token = loginUser(5201);
        long requestId;
        BROKER.getDockerClient().pauseContainerCmd(BROKER.getContainerId()).exec();
        try {
            JsonNode accepted = post("/api/user/seckill",
                    Map.of("tierId", 5201, "quantity", 1, "requestKey", "broker-paused"), token);
            assertSuccess(accepted);
            requestId = accepted.path("data").path("requestId").asLong();
            Thread.sleep(6500);
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM stock_reservation WHERE id=?", Integer.class, requestId));
            assertTrue(db.queryForObject("SELECT COUNT(*) FROM message_outbox WHERE business_key=? AND publish_status<>'PUBLISHED'",
                    Integer.class, "create-order:" + requestId + ":0") > 0);
        } finally {
            BROKER.getDockerClient().unpauseContainerCmd(BROKER.getContainerId()).exec();
        }
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline && db.queryForObject(
                "SELECT COUNT(*) FROM order_info WHERE tier_id=5201", Integer.class) == 0) {
            Thread.sleep(200);
        }
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM order_info WHERE tier_id=5201", Integer.class));
        assertEquals(1, stock(5201));
        assertEquals(1, context.getBean(StockServiceImpl.class).getStock(5201L));
    }

    @Test
    void committedOrderSurvivesLostAckAndRealBrokerRedelivery() throws Exception {
        seedProtocolSale(5301, 2);
        context.getBean(StockServiceImpl.class).prepare(5301);
        var listeners = context.getBean(RabbitListenerEndpointRegistry.class);
        listeners.stop();
        var connection = context.getBean(ConnectionFactory.class).createConnection();
        Channel channel = connection.createChannel(false);
        try {
            long requestId = ((Number) context.getBean(ReservationService.class)
                    .reserve(5301, 5301, 1, "lost-ack").get("requestId")).longValue();
            var consumer = context.getBean(SeckillOrderConsumer.class);
            boolean processed = false;
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (!processed && System.nanoTime() < deadline) {
                var delivery = channel.basicGet("trade.order.v3", false);
                if (delivery == null) {
                    Thread.sleep(100);
                    continue;
                }
                MessageProperties properties = new MessageProperties();
                properties.setMessageId(delivery.getProps().getMessageId());
                properties.setDeliveryTag(delivery.getEnvelope().getDeliveryTag());
                Message message = new Message(delivery.getBody(), properties);
                long deliveredId = new ObjectMapper().readTree(delivery.getBody())
                        .path("payload").path("reservationId").asLong();
                if (deliveredId != requestId) {
                    consumer.handleSeckillOrder(message, channel);
                    continue;
                }

                // 真实业务提交后模拟ACK连接故障；消息仍由真实Broker持有。
                Channel lostAck = mock(Channel.class, AdditionalAnswers.delegatesTo(channel));
                doThrow(new IOException("isolated lost acknowledgement")).when(lostAck).basicAck(anyLong(), anyBoolean());
                assertThrows(IOException.class, () -> consumer.handleSeckillOrder(message, lostAck));
                assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM order_info WHERE tier_id=5301", Integer.class));
                Channel physical = channel instanceof ChannelProxy proxy ? proxy.getTargetChannel() : channel;
                physical.abort();
                processed = true;
            }
            assertTrue(processed, "未取得本次真实Broker消息");
            listeners.start();
            verify(orderBusiness, timeout(10000).atLeast(2)).createOrder(requestId);
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM order_info WHERE tier_id=5301", Integer.class));
            assertEquals(1, stock(5301));
            assertEquals(1L, db.queryForObject("SELECT stock_version FROM ticket_tier WHERE id=5301", Long.class));
        } finally {
            if (channel.isOpen()) {
                channel.close();
            }
            connection.close();
            listeners.start();
        }
    }

    @Test
    void historicalPaidOrderRemainsAnActiveRedisPurchaseGuard() {
        seedProtocolSale(4401, 10);
        db.update("UPDATE ticket_tier SET available_stock=8 WHERE id=4401");
        db.update("""
                INSERT INTO order_info(order_no,user_id,event_id,session_id,tier_id,quantity,total_amount,
                    order_status,create_time,update_time)
                VALUES('historical-protocol-order',4401,4401,4401,4401,2,200,1,NOW(),NOW())
                """);
        var stockService = context.getBean(StockServiceImpl.class);
        stockService.prepare(4401);
        assertEquals(8, stockService.getStock(4401L));
        assertThrows(IllegalStateException.class, () -> stockService.reserve(4401, 4401, 1, "repeat"));
    }

    @Test
    void anotherTierWithTheSameRequestKeyReleasesItsExtraRedisHold() {
        seedProtocolSale(4701, 5);
        seedProtocolSale(4702, 5);
        var applications = context.getBean(ReservationServiceImpl.class);
        var stockService = context.getBean(StockServiceImpl.class);
        applications.reserve(4701, 4701, 1, "cross-tier-key");
        assertThrows(IllegalArgumentException.class,
                () -> applications.reserve(4701, 4702, 1, "cross-tier-key"));
        assertEquals(5, stockService.getStock(4702L), "拒绝不同参数不能遗留额外预扣");
    }

    @Test
    void lostCommitResponseResolvesTheSameDurableApplication() {
        seedProtocolSale(4801, 5);
        var stockService = context.getBean(StockServiceImpl.class);
        stockService.prepare(4801);
        var applications = context.getBean(ReservationServiceImpl.class);
        AtomicBoolean lost = new AtomicBoolean();
        doAnswer(invocation -> {
            Object committed = invocation.callRealMethod();
            if (committed instanceof StockReservation && lost.compareAndSet(false, true)) {
                throw new TransactionSystemException("injected response lost after real commit");
            }
            return committed;
        }).when(transactions).execute(ArgumentMatchers.any());
        Map<String, Object> accepted = applications.reserve(4801, 4801, 2, "lost-response");
        Map<String, Object> replay = applications.reserve(4801, 4801, 2, "lost-response");
        assertTrue(lost.get());
        assertEquals(accepted.get("requestId"), replay.get("requestId"));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM stock_reservation WHERE tier_id=4801", Integer.class));
        assertEquals(3, stockService.getStock(4801L));
    }

    @Test
    void outboxFailureRollsBackAcceptanceBeforeRedisRelease() {
        seedProtocolSale(4901, 5);
        var applications = context.getBean(ReservationServiceImpl.class);
        var stockService = context.getBean(StockServiceImpl.class);
        stockService.prepare(4901);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("injected failure after writing outbox");
        }).when(AopTestUtils.<OutboxStore>getUltimateTargetObject(durableEvents))
                .enqueue(eq("CREATE_ORDER"), anyString(), ArgumentMatchers.any());
        assertThrows(IllegalStateException.class,
                () -> applications.reserve(4901, 4901, 2, "outbox-rollback"));
        assertEquals(5, stock(4901));
        assertEquals(5, stockService.getStock(4901L));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM stock_reservation WHERE tier_id=4901", Integer.class));
        assertEquals("REJECTED", db.queryForObject(
                "SELECT admission_status FROM seckill_admission WHERE user_id=4901", String.class));
    }

    @Test
    void interruptedRebuildRejectsOldThreadsAndCannotOverwriteANewerEpoch() {
        seedProtocolSale(5001, 5);
        var stockService = context.getBean(StockServiceImpl.class);
        var applications = context.getBean(ReservationServiceImpl.class);
        RedisReservation original = stockService.reserve(5001, 5001, 2, "interrupted-rebuild");
        var accepted = applications.accept(original);
        stockService.confirm(original);

        // 模拟重建者持久化PAUSED后退出，只留下半成品Redis代次。
        db.update("""
                UPDATE seckill_stock_epoch SET epoch=epoch+1,cache_status='PAUSED',build_token='interrupted',
                    updated_at=DATE_SUB(NOW(),INTERVAL 61 SECOND) WHERE tier_id=5001
                """);
        long incompleteEpoch = original.epoch() + 1;
        redis.opsForHash().putAll(StockServiceImpl.metaKey(5001), Map.of(
                "epoch", Long.toString(incompleteEpoch), "ready", "0", "expected", "1", "loaded", "0"));
        long rebuilt = stockService.prepare(5001);
        assertTrue(rebuilt > incompleteEpoch);
        assertEquals(3, stockService.getStock(5001L));
        assertThrows(IllegalStateException.class, () -> applications.accept(original));

        Long staleWrite = redis.execute(context.getBean(LuaScriptLoader.class).<Long>getScript("rebuildStockBatch.lua"),
                StockServiceImpl.keys(5001, incompleteEpoch), Long.toString(incompleteEpoch), "5", "0", "late-init", "[]", "1");
        assertEquals(-1L, staleWrite);
        assertEquals(Long.toString(rebuilt), redis.<String, String>opsForHash().get(StockServiceImpl.metaKey(5001), "epoch"));
        assertEquals(accepted.getId(), applications.findAdmission(5001, "interrupted-rebuild").getReservationId());
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
        LocalDateTime firstNotifyTime = db.queryForObject(
                "SELECT notify_time FROM payment_record WHERE payment_no=?", LocalDateTime.class, paidNo);
        assertNotNull(firstNotifyTime);
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM payment_notify_log WHERE payment_no=?",
                Integer.class, paidNo));
        assertEquals("success", notifyPaid(paidNo, "ALIPAY-JOURNEY-PAID", "200.00"));
        assertEquals(firstNotifyTime, db.queryForObject(
                "SELECT notify_time FROM payment_record WHERE payment_no=?", LocalDateTime.class, paidNo));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM payment_notify_log WHERE payment_no=?",
                Integer.class, paidNo));
        assertEquals(1, db.queryForObject("SELECT COUNT(DISTINCT trade_no) FROM payment_notify_log WHERE payment_no=?",
                Integer.class, paidNo));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM payment_notify_log WHERE payment_no=?"
                + " AND notify_status='TRADE_SUCCESS' AND process_status=1 AND notify_data='total_amount=200.00'",
                Integer.class, paidNo));
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
        assertEquals(lateOrder, userRefunds.path("data").get(0).path("order_id").asLong());
        assertTrue(get("/api/user/refunds", paidToken).path("data").isEmpty());
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

    @Test
    void adminBrowserConfiguresSaleAndProtectsPrivilegedAccounts() throws Exception {
        seedAccount(9101, "USER");
        seedAccount(9102, "SUPER_ADMIN");
        String viewer = loginUser(9101);
        String admin = loginAdmin(9102);

        // 普通用户无法读取后台；超级管理员继承现有ADMIN能力。
        assertNotEquals(1, get("/api/admin/dashboard/summary", viewer).path("code").asInt());
        assertSuccess(get("/api/admin/dashboard/summary", admin));
        assertNotEquals(1, put("/api/admin/user/9102/status?status=0", admin).path("code").asInt());

        Path root = Path.of("").toAbsolutePath().normalize();
        if (root.getFileName().toString().equals("ticket-management")) {
            root = root.getParent();
        }
        Path browserLog = root.resolve("output/admin-live-browser.log");
        Files.createDirectories(browserLog.getParent());
        ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/d", "/c", "npm.cmd", "run", "test:e2e:live", "--", "admin-flow.spec.ts");
        builder.directory(root.resolve("frontend").toFile());
        builder.environment().put("API_PROXY_TARGET", http.getRootUri());
        builder.environment().put("LIVE_ISOLATED_ACCEPTANCE", "true");
        builder.redirectErrorStream(true);
        builder.redirectOutput(browserLog.toFile());
        Process browser = builder.start();
        try {
            assertTrue(browser.waitFor(180, TimeUnit.SECONDS), "管理端浏览器验收超时");
            assertEquals(0, browser.exitValue(), Files.readString(browserLog));
        } finally {
            if (browser.isAlive()) {
                browser.descendants().forEach(ProcessHandle::destroy);
                browser.destroyForcibly();
            }
        }

        // 独立核对真实Mapper写入结果，而非只相信页面提示。
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM event WHERE name='隔离后台音乐会' AND status=1", Integer.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM event_session WHERE session_name='隔离后台晚场' AND status=0", Integer.class));
        assertEquals(20, db.queryForObject("SELECT available_stock FROM ticket_tier WHERE tier_name='隔离后台看台'", Integer.class));
        assertTrue(encoder.matches("journey-test-pass", db.queryForObject("SELECT password FROM user WHERE id=9101", String.class)));
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
        assertEquals("isolated-payment-page", result.path("data").path("paymentPageHtml").asText());
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
