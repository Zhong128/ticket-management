package org.example.ticketmanagement.trade.messaging;

import cn.dev33.satoken.stp.StpUtil;
import com.alipay.api.AlipayClient;
import org.example.ticketmanagement.TicketManagementApplication;
import org.example.ticketmanagement.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 显式启用的隔离 JMeter 压测入口，不连接原数据库或中间件数据卷。
 */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "RUN_JMETER_LOAD", matches = "true")
@SpringBootTest(classes = TicketManagementApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "spring.config.location=classpath:application-trade-isolated-it.properties",
                "spring.config.import=", "logging.level.org.example.ticketmanagement=WARN" })
class TradeLoadIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("trade_load_it").withUsername("load_it").withPassword("isolated-test-only");

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
                + "  connectionMinimumIdleSize: 2\n  connectionPoolSize: 16\n"
                + "  subscriptionConnectionMinimumIdleSize: 1\n  subscriptionConnectionPoolSize: 4\n"
                + "threads: 4\nnettyThreads: 4\n");
    }

    @MockitoBean
    AlipayClient alipayClient;

    @MockitoBean
    EmailService email;

    @MockitoBean
    OpenAiChatModel ai;

    @Autowired
    JdbcTemplate db;

    @LocalServerPort
    int port;

    @Test
    void sustainedReservationsUseFreshUsersAndStock() throws Exception {
        when(email.sendSimpleEmail(anyString(), anyString(), anyString())).thenReturn(true);

        int duration = setting("LOAD_DURATION_SECONDS", 300);
        int targetPerMinute = setting("LOAD_TARGET_PER_MINUTE", 6000);
        int threads = setting("LOAD_THREADS", 100);
        int buyers = Math.max(100, targetPerMinute * duration / 60 + threads * 10);
        Path jmeter = Path.of(requiredSetting("JMETER_SCRIPT"));
        assertTrue(Files.isRegularFile(jmeter), "JMETER_SCRIPT 必须指向 JMeter 启动脚本");

        Path runDirectory = Path.of("target", "load-acceptance",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        Files.createDirectories(runDirectory);

        // 只在 Testcontainers 的新库中准备足够的库存和不同用户。
        seedSale(buyers + 1000);
        Path users = runDirectory.resolve("test-users.csv");
        seedUsersAndTokens(buyers, users);

        // 通过 JMeter CLI 按真实 HTTP 接口持续发送抢票请求。
        Path results = runDirectory.resolve("load.jtl");
        Path report = runDirectory.resolve("report");
        Path output = runDirectory.resolve("jmeter-console.log");
        List<String> command = List.of(jmeter.toString(), "-n", "-t",
                Path.of("..", "docs", "performance", "reservation.jmx").toAbsolutePath().toString(),
                "-Jhost=127.0.0.1", "-Jport=" + port, "-Jusers=" + users.toAbsolutePath(),
                "-Jthreads=" + threads, "-Jduration=" + duration,
                "-Jtarget_per_minute=" + targetPerMinute,
                "-l", results.toAbsolutePath().toString(), "-e", "-o", report.toAbsolutePath().toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(output.toFile()).start();
        boolean finished = process.waitFor(duration + 180L, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        assertTrue(finished, "JMeter 超时，详见 " + output);
        assertEquals(0, process.exitValue(), "JMeter 执行失败，详见 " + output);
        assertTrue(Files.isRegularFile(results), "未生成 JTL 文件");
        long jmeterSamples;
        long jmeterFailures;
        try (var lines = Files.lines(results, StandardCharsets.UTF_8)) {
            List<String> samples = lines.skip(1).toList();
            jmeterSamples = samples.size();
            jmeterFailures = samples.stream().filter(line -> line.contains(",text,false,")).count();
        }

        // 记录停压时的积压，再等待建单消息及订单通知收敛。
        int outboxAtStop = pendingOutbox();
        int emailsAtStop = pendingEmailTasks();
        long settleStart = System.nanoTime();
        long deadline = System.nanoTime() + Duration.ofMinutes(3).toNanos();
        while (System.nanoTime() < deadline && (pendingOutbox() > 0 || pendingEmailTasks() > 0)) {
            Thread.sleep(1000);
        }
        long settleSeconds = Duration.ofNanos(System.nanoTime() - settleStart).toSeconds();

        // 核对落单、库存及通知任务的最终状态。
        int invalidStock = db.queryForObject("SELECT COUNT(*) FROM ticket_tier WHERE available_stock<0 "
                + "OR available_stock>total_stock", Integer.class);
        int invalidBalance = db.queryForObject("SELECT COUNT(*) FROM (SELECT t.id FROM ticket_tier t "
                + "LEFT JOIN stock_reservation r ON r.tier_id=t.id "
                + "GROUP BY t.id,t.total_stock,t.available_stock HAVING t.total_stock<>t.available_stock+"
                + "COALESCE(SUM(CASE WHEN r.status IN ('RESERVED','ORDER_CREATED','PAID') "
                + "THEN r.quantity ELSE 0 END),0)) v", Integer.class);
        int accepted = db.queryForObject("SELECT COUNT(*) FROM stock_reservation WHERE tier_id=4001", Integer.class);
        int orders = db.queryForObject("SELECT COUNT(*) FROM order_info WHERE tier_id=4001", Integer.class);
        int manualEmails = db.queryForObject("SELECT COUNT(*) FROM payment_compensation "
                + "WHERE task_type='EMAIL_ORDER' AND task_status=3", Integer.class);
        int pendingEmails = pendingEmailTasks();

        String summary = "durationSeconds=" + duration + "\ntargetPerMinute=" + targetPerMinute
                + "\nthreads=" + threads + "\nbuyers=" + buyers
                + "\njmeterSamples=" + jmeterSamples + "\njmeterFailures=" + jmeterFailures
                + "\nreservations=" + accepted
                + "\norders=" + orders + "\noutboxAtStop=" + outboxAtStop
                + "\nemailsAtStop=" + emailsAtStop + "\nsettleSeconds=" + settleSeconds
                + "\npendingOutbox=" + pendingOutbox()
                + "\nmanualEmails=" + manualEmails + "\npendingEmails=" + pendingEmails
                + "\ninvalidStock=" + invalidStock + "\ninvalidBalance=" + invalidBalance + "\n";
        Files.writeString(runDirectory.resolve("database-summary.txt"), summary, StandardCharsets.UTF_8);
        assertEquals(0, jmeterFailures, summary);
        assertEquals(jmeterSamples, accepted, summary);
        assertEquals(0, invalidStock, summary);
        assertEquals(0, invalidBalance, summary);
        assertEquals(accepted, orders, summary);
        assertEquals(0, pendingOutbox(), summary);
        assertEquals(0, manualEmails, summary);
        assertEquals(0, pendingEmails, summary);
    }

    private void seedSale(int stock) {
        LocalDateTime now = LocalDateTime.now();
        db.update("INSERT INTO city(id,name,code,province) VALUES(4001,'Load city','LOAD','Test province')");
        db.update("INSERT INTO event_category(id,name) VALUES(4001,'Load category')");
        db.update("INSERT INTO event(id,name,category_id,city_id,venue,status,sale_start_time,sale_end_time,"
                + "event_start_time,event_end_time) VALUES(4001,'Load event',4001,4001,'Load venue',1,?,?,?,?)",
                now.minusDays(1), now.plusDays(1), now.plusDays(2), now.plusDays(3));
        db.update("INSERT INTO event_session(id,event_id,session_name,session_time,status) "
                + "VALUES(4001,4001,'Load session',?,1)", now.plusDays(2));
        db.update("INSERT INTO ticket_tier(id,event_id,session_id,tier_name,original_price,current_price,"
                + "total_stock,available_stock,status) VALUES(4001,4001,4001,'Load tier',100,100,?,?,1)",
                stock, stock);
    }

    private void seedUsersAndTokens(int buyers, Path users) throws IOException {
        List<Object[]> rows = new ArrayList<>(1000);
        try (BufferedWriter writer = Files.newBufferedWriter(users, StandardCharsets.UTF_8)) {
            writer.write("token,tierId\n");
            for (int i = 0; i < buyers; i++) {
                long userId = 100000L + i;
                rows.add(new Object[] { userId, "load-" + userId, "isolated-test-only",
                        "load-" + userId + "@example.invalid" });
                if (rows.size() == 1000 || i == buyers - 1) {
                    db.batchUpdate("INSERT INTO user(id,username,password,email,status,role) "
                            + "VALUES(?,?,?,?,1,'USER')", rows);
                    rows.clear();
                }

                // 使用项目原有 Sa-Token 建立真实 Redis 登录会话。
                writer.write(StpUtil.createLoginSession(userId));
                writer.write(",4001\n");
            }
        }
    }

    private int pendingOutbox() {
        return db.queryForObject("SELECT COUNT(*) FROM message_outbox WHERE publish_status<>'PUBLISHED'",
                Integer.class);
    }

    private int pendingEmailTasks() {
        return db.queryForObject("SELECT COUNT(*) FROM payment_compensation "
                + "WHERE task_type='EMAIL_ORDER' AND task_status<>2", Integer.class);
    }

    private int setting(String name, int defaultValue) {
        int value = Integer.parseInt(System.getenv().getOrDefault(name, String.valueOf(defaultValue)));
        assertTrue(value > 0, name + " 必须大于零");
        return value;
    }

    private String requiredSetting(String name) {
        String value = System.getenv(name);
        assertNotNull(value, name + " 未设置");
        return value;
    }
}
