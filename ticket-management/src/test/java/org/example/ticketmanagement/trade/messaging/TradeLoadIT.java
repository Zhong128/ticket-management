package org.example.ticketmanagement.trade.messaging;

import java.util.Arrays;
import cn.dev33.satoken.stp.StpUtil;
import com.alipay.api.AlipayClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ticketmanagement.TicketManagementApplication;
import org.example.ticketmanagement.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.test.util.ReflectionTestUtils;
import org.example.ticketmanagement.serviceimpl.StockServiceImpl;
import org.example.ticketmanagement.serviceimpl.OrderCreationCoordinator;
import org.example.ticketmanagement.serviceimpl.SeckillServiceImpl;
import io.micrometer.core.instrument.MeterRegistry;
import org.example.ticketmanagement.trade.payment.PaymentGateway;
import org.example.ticketmanagement.pojo.PaymentRecord;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 显式启用的隔离 JMeter 压测入口，不连接原数据库或中间件数据卷。
 */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "RUN_JMETER_LOAD", matches = "true")
@Import(LoadSqlDiagnostics.DiagnosticConfiguration.class)
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
    PaymentGateway gateway;

    private final Map<String, PaymentRecord> channelPayments = new ConcurrentHashMap<>();

    @MockitoBean
    OpenAiChatModel ai;

    @Autowired
    JdbcTemplate db;

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext context;
    @Autowired
    Environment environment;
    private int tierId = 4000;
    private int lastTierId = 4000;
    private int userSequence;

    @Test
    void sustainedReservationsUseFreshUsersAndStock() throws Exception {
        boolean campaign = Boolean.parseBoolean(System.getenv().getOrDefault("LOAD_CAMPAIGN", "false"));
        if (!campaign) {
            assertTrue(runScenario(System.getenv().getOrDefault("LOAD_MODE", "abundant"),
                    setting("LOAD_DURATION_SECONDS", 30), setting("LOAD_TARGET_PER_MINUTE", 1200), 1, true, 20));
            return;
        }
        int sustainable = Integer.parseInt(System.getenv().getOrDefault("LOAD_SUSTAINABLE_PER_MINUTE", "0"));
        for (int rate : sustainable > 0 ? new int[0] : new int[] {300, 600, 1200, 2400}) {
            if (runScenario("abundant", 30, rate, 0, true, 20)) {
                sustainable = rate;
            } else {
                break;
            }
        }
        assertTrue(sustainable > 0, "最低阶梯未通过，先排查，不能直接持续压测");
        boolean sustained = false;
        while (sustainable >= 60 && !sustained) {
            sustained = true;
            for (int round = 1; round <= 3; round++) {
                if (!runScenario("abundant", 300, sustainable, round, true, 20)) {
                    sustained = false;
                    sustainable /= 2;
                    break;
                }
            }
        }
        assertTrue(sustained, "低负载也无法持续成功落单，应先排查业务链路");
        for (String mode : new String[] {"soldout", "duplicate", "mixed", "dual", "recovery"}) {
            assertTrue(runScenario(mode, 30, sustainable, 1, true, 20), mode);
        }
        assertTrue(runScenario("abundant", 30, sustainable, 1, true, 40));
        assertTrue(runScenario("abundant", 30, sustainable, 1, false, 20));
    }

    private boolean runScenario(String mode, int duration, int targetPerMinute, int round,
            boolean locking, int segments) throws Exception {
        when(email.sendSimpleEmail(anyString(), anyString(), anyString())).thenReturn(true);
        when(gateway.create(any(PaymentRecord.class))).thenAnswer(call -> {
            PaymentRecord payment = call.getArgument(0);
            channelPayments.put(payment.getPaymentNo(), payment);
            return "isolated-load-form";
        });
        when(gateway.query(anyString())).thenAnswer(call -> {
            String no = call.getArgument(0);
            PaymentRecord payment = channelPayments.get(no);
            return payment == null ? new PaymentGateway.Query(PaymentGateway.State.NOT_FOUND, no, null, null)
                    : new PaymentGateway.Query(PaymentGateway.State.PAID, no, "LOAD-" + no, payment.getTotalAmount());
        });
        when(gateway.close(anyString())).thenReturn(true);

        int threads = setting("LOAD_THREADS", 60);
        tierId = lastTierId + 1;
        boolean multiTier = mode.contains("multi");
        lastTierId = multiTier ? tierId + 2 : tierId;
        var seckill = context.getBean(SeckillServiceImpl.class);
        ReflectionTestUtils.setField(seckill, "segments", segments);
        ReflectionTestUtils.setField(seckill, "lockingEnabled", locking);
        int buyers = Math.max(100, targetPerMinute * duration / 60 + threads * 10);
        Path jmeter = Path.of(requiredSetting("JMETER_SCRIPT"));
        assertTrue(Files.isRegularFile(jmeter), "JMETER_SCRIPT 必须指向 JMeter 启动脚本");

        Path runDirectory = Path.of("target", "load-acceptance",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + mode + "-" + tierId);
        Files.createDirectories(runDirectory);

        // 只在 Testcontainers 的新库中准备足够的库存和不同用户。
        int firstTier = tierId;
        for (int current = firstTier; current <= lastTierId; current++) {
            tierId = current;
            seedSale(mode.equals("soldout") ? 30 : buyers * 3 + 1000);
            context.getBean(StockServiceImpl.class).prepare(tierId);
        }
        tierId = firstTier;
        Path users = runDirectory.resolve("test-users.csv");
        seedUsersAndTokens(buyers, users, mode);

        // 三组独立运行；历史票档的库存与本轮新买家隔离。
        String history = System.getenv().getOrDefault("LOAD_EXPIRY_HISTORY", "none");
        assertTrue(List.of("none", "future", "due").contains(history), "未知历史订单模式");
        int historyTier = lastTierId + 100;
        if (!history.equals("none")) {
            assertEquals("abundant", mode, "到期对照仅适用于单热点独立运行");
            prepareHistory(seckill, historyTier, runDirectory);
        }

        var listeners = context.getBean(RabbitListenerEndpointRegistry.class);
        LoadSqlDiagnostics diagnostics = context.getBeanProvider(LoadSqlDiagnostics.class).getIfAvailable();
        SimpleMessageListenerContainer orderListener = listeners.getListenerContainers().stream()
                .filter(SimpleMessageListenerContainer.class::isInstance)
                .map(SimpleMessageListenerContainer.class::cast)
                .filter(listener -> Arrays.asList(listener.getQueueNames()).contains("trade.order.v3"))
                .findFirst().orElseThrow();

        // 仅诊断开关允许改变建单队列并发，其他监听及业务配置保持原样。
        int diagnosticConsumers = System.getenv().containsKey("LOAD_ORDER_CONSUMERS")
                ? setting("LOAD_ORDER_CONSUMERS", 1) : 0;
        if (Boolean.parseBoolean(System.getenv().getOrDefault("LOAD_DIAGNOSTICS", "false"))) {
            assertNotNull(diagnostics, "诊断开关开启但MyBatis采集器未加载");
        }
        if (diagnosticConsumers > 0) {
            assertNotNull(diagnostics, "调整建单监听并发仅用于显式诊断模式");
            orderListener.stop();
            orderListener.setConcurrentConsumers(diagnosticConsumers);
            orderListener.setMaxConcurrentConsumers(diagnosticConsumers);
            orderListener.start();
        }
        IsolatedSecondApplication second = null;
        if (mode.startsWith("dual")) {
            Map<String, String> endpoints = new HashMap<>();
            for (String name : List.of("spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
                    "spring.rabbitmq.host", "spring.rabbitmq.port", "spring.rabbitmq.username", "spring.rabbitmq.password",
                    "spring.data.redis.host", "spring.data.redis.port", "redisson.config")) {
                endpoints.put(name, environment.getRequiredProperty(name));
            }
            second = new IsolatedSecondApplication(endpoints, runDirectory.resolve("second-application"));
        }
        if (mode.equals("recovery")) {
            listeners.stop();
        }
        MeterRegistry metrics = context.getBean(MeterRegistry.class);
        double lockWaitBefore = lockWait(metrics);
        double conflictsBefore = conflicts(metrics);
        if (diagnostics != null) {
            diagnostics.reset();
        }

        // JMeter仅发送HTTP；数据库断言在停压和收敛后独立执行。
        Path results = runDirectory.resolve("load.jtl");
        Path report = runDirectory.resolve("report");
        Path output = runDirectory.resolve("jmeter-console.log");
        List<String> command = List.of(jmeter.toString(), "-n", "-t",
                Path.of("..", "docs", "performance", mode.equals("mixed") ? "reservation-mixed.jmx" : "reservation.jmx").toAbsolutePath().toString(),
                "-Jhost=127.0.0.1", "-Jport=" + port, "-Jport2=" + (second == null ? port : second.port), "-Jmode=" + mode, "-Jusers=" + users.toAbsolutePath(),
                "-Jthreads=" + threads, "-Jduration=" + duration,
                "-Jtarget_per_minute=" + targetPerMinute,
                "-l", results.toAbsolutePath().toString(), "-e", "-o", report.toAbsolutePath().toString());
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
            builder.environment().put("JVM_ARGS", "-Djdk.net.unixdomain.tmpdir=" + Path.of("target").toAbsolutePath());
            long loadStarted = System.nanoTime();
            Process process = builder.start();
            if (history.equals("due")) {
                // 只移动隔离夹具的截止时间，通过真实数据库兜底扫描触发关单。
                LocalDateTime expiry = LocalDateTime.now().plusMinutes(2);
                db.update("UPDATE stock_reservation SET expires_at=? WHERE tier_id=?", expiry, historyTier);
                db.update("UPDATE order_info SET expire_time=? WHERE tier_id=?", expiry, historyTier);
                Files.writeString(runDirectory.resolve("history-deadline.txt"), expiry.toString());
            }
            boolean finished = process.waitFor(duration + 180L, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            assertTrue(finished, "JMeter 超时，详见 " + output);
            assertEquals(0, process.exitValue(), "JMeter 执行失败，详见 " + output);
            assertTrue(Files.isRegularFile(results), "未生成 JTL 文件");
            List<String[]> samples;
            try (var lines = Files.lines(results, StandardCharsets.UTF_8)) {
                samples = lines.skip(1).map(this::csvRow).toList();
            }
            String[] header = csvRow(Files.readAllLines(results, StandardCharsets.UTF_8).getFirst());
            List<String> columns = Arrays.asList(header);
            int successColumn = columns.indexOf("success");
            int labelColumn = columns.indexOf("label");
            int messageColumn = columns.indexOf("responseMessage");
            int elapsedColumn = columns.indexOf("elapsed");
            int timestampColumn = columns.indexOf("timeStamp");
            long jmeterFailures = samples.stream().filter(row -> !row[successColumn].equals("true")).count();
            var reservations = samples.stream().filter(row -> row[labelColumn].equals("reserve")
                    || row[labelColumn].startsWith("reserve-tier-")).toList();
            long jmeterSamples = reservations.size();
            long acceptedResponses = reservations.stream().filter(row -> row[messageColumn].equals("ACCEPTED")).count();
            long rejectedResponses = reservations.stream().filter(row -> row[messageColumn].equals("SOLD_OUT")).count();
            List<Long> latency = reservations.stream().map(row -> Long.parseLong(row[elapsedColumn])).sorted().toList();
            long first = samples.stream().mapToLong(row -> Long.parseLong(row[timestampColumn])).min().orElseThrow();
            long last = samples.stream().mapToLong(row -> Long.parseLong(row[timestampColumn])
                    + Long.parseLong(row[elapsedColumn])).max().orElseThrow();
            double actualSeconds = (last - first) / 1000.0;

            // 记录停压时的积压，再等待建单消息及订单通知收敛。
            int outboxAtStop = pendingOutbox();
            int emailsAtStop = pendingEmailTasks();
            int pendingAtStop = db.queryForObject("SELECT COUNT(*) FROM stock_reservation WHERE tier_id BETWEEN ? AND ? AND inventory_state='PENDING'", Integer.class, tierId, lastTierId);
            int orderConsumersAtStop = orderListener.getActiveConsumerCount();
            if (mode.equals("recovery")) {
                listeners.start();
            }
            long settleStart = System.nanoTime();
            long deadline = System.nanoTime() + Duration.ofMinutes(3).toNanos();
            while (System.nanoTime() < deadline && (pendingOutbox() > 0 || pendingEmailTasks() > 0
                    || pendingReservations() > 0 || !quotaConverged()
                    || (history.equals("due") && historicalClosed(historyTier) != 1000))) {
                Thread.sleep(1000);
            }
            long settleSeconds = Duration.ofNanos(System.nanoTime() - settleStart).toSeconds();
            if (!history.equals("none")) {
                int expectedClosed = history.equals("due") ? 1000 : 0;
                int closed = historicalClosed(historyTier);
                int released = db.queryForObject("SELECT COUNT(*) FROM stock_reservation "
                        + "WHERE tier_id=? AND inventory_state='RELEASED' AND status='RELEASED'",
                        Integer.class, historyTier);
                int stock = db.queryForObject("SELECT available_stock FROM ticket_tier WHERE id=?",
                        Integer.class, historyTier);
                int quota = context.getBean(StockServiceImpl.class).getStock((long) historyTier);
                Files.writeString(runDirectory.resolve("history-summary.txt"),
                        "mode=" + history + "\nclosed=" + closed + "\nreleased=" + released
                                + "\nstock=" + stock + "\nquota=" + quota + "\n");
                assertEquals(expectedClosed, closed, "历史订单关闭数");
                assertEquals(expectedClosed, released, "历史申请释放数");
                assertEquals(expectedClosed, stock, "历史库存只回补一次");
                assertEquals(stock, quota, "历史票档Redis配额");
            }

            double completionSeconds = (System.nanoTime() - loadStarted) / 1_000_000_000.0;

            // 核对落单、库存及通知任务的最终状态。
            int invalidStock = db.queryForObject("SELECT COUNT(*) FROM ticket_tier WHERE available_stock<0 "
                    + "OR available_stock>total_stock", Integer.class);
            int invalidBalance = db.queryForObject("SELECT COUNT(*) FROM (SELECT t.id FROM ticket_tier t "
                    + "LEFT JOIN stock_reservation r ON r.tier_id=t.id "
                    + "GROUP BY t.id,t.total_stock,t.available_stock HAVING t.total_stock<>t.available_stock+"
                    + "COALESCE(SUM(CASE WHEN r.inventory_state='HELD' "
                    + "THEN r.quantity ELSE 0 END),0)) v", Integer.class);
            int accepted = db.queryForObject("SELECT COUNT(*) FROM stock_reservation WHERE tier_id BETWEEN ? AND ?", Integer.class, tierId, lastTierId);
            int orders = db.queryForObject("SELECT COUNT(*) FROM order_info WHERE tier_id BETWEEN ? AND ?", Integer.class, tierId, lastTierId);
            int manualEmails = db.queryForObject("SELECT COUNT(*) FROM payment_compensation "
                    + "WHERE task_type IN ('EMAIL_ORDER','EMAIL_PAYMENT') AND task_status=3", Integer.class);
            int pendingEmails = pendingEmailTasks();

            int paidOrders = db.queryForObject("SELECT COUNT(*) FROM order_info WHERE tier_id BETWEEN ? AND ? AND order_status=1", Integer.class, tierId, lastTierId);
            int canceledOrders = db.queryForObject("SELECT COUNT(*) FROM order_info WHERE tier_id BETWEEN ? AND ? AND order_status=2", Integer.class, tierId, lastTierId);
            int databaseStock = db.queryForObject("SELECT SUM(available_stock) FROM ticket_tier WHERE id BETWEEN ? AND ?", Integer.class, tierId, lastTierId);
            int pendingQuantity = db.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM stock_reservation WHERE tier_id BETWEEN ? AND ? AND inventory_state='PENDING'", Integer.class, tierId, lastTierId);
            int redisQuota = 0;
            for (int current = tierId; current <= lastTierId; current++) {
                redisQuota += context.getBean(StockServiceImpl.class).getStock((long) current);
            }
            int quotaDifference = redisQuota - (databaseStock - pendingQuantity);
            int duplicateGuards = db.queryForObject("SELECT COUNT(*) FROM (SELECT user_id,tier_id FROM stock_reservation WHERE tier_id BETWEEN ? AND ? AND status<>'RELEASED' GROUP BY user_id,tier_id HAVING COUNT(*)>1) x", Integer.class, tierId, lastTierId);
            int manualRetries = db.queryForObject("SELECT COUNT(*) FROM payment_compensation WHERE task_type='MESSAGE_RETRY' AND task_status=3", Integer.class);
            List<Long> orderLatency = db.queryForList("SELECT GREATEST(0,TIMESTAMPDIFF(MICROSECOND,r.create_time,o.create_time)/1000) FROM stock_reservation r JOIN order_info o ON o.id=r.order_id WHERE r.tier_id BETWEEN ? AND ? ORDER BY 1", Long.class, tierId, lastTierId);
            String summary = "mode=" + mode + "\nround=" + round + "\nlocking=" + locking + "\nsegments=" + segments
                    + "\nactualSeconds=" + actualSeconds + "\nacceptedResponses=" + acceptedResponses
                    + "\nexpectedSoldOutResponses=" + rejectedResponses
                    + "\nacceptedUniquePerSecond=" + accepted / actualSeconds
                    + "\ncompletionSecondsIncludingStartupAndDrain=" + completionSeconds
                    + "\ncompletedOrdersPerSecond=" + orders / completionSeconds
                    + "\np50Millis=" + percentile(latency, .50) + "\np95Millis=" + percentile(latency, .95) + "\np99Millis=" + percentile(latency, .99)
                    + "\norderP95MillisCoarse=" + percentile(orderLatency, .95)
                    + "\nlockWaitSecondsParent=" + (lockWait(metrics) - lockWaitBefore)
                    + "\noptimisticConflictsParent=" + (conflicts(metrics) - conflictsBefore)
                    + "\nquotaDifference=" + quotaDifference + "\nduplicateGuards=" + duplicateGuards
                    + "\npaidOrders=" + paidOrders + "\ncanceledOrders=" + canceledOrders
                    + "\nmanualRetries=" + manualRetries + "\npendingAtStop=" + pendingAtStop
                    + "\ndurationSeconds=" + duration + "\ntargetPerMinute=" + targetPerMinute
                    + "\nthreads=" + threads + "\nbuyers=" + buyers
                    + "\njmeterSamples=" + jmeterSamples + "\njmeterFailures=" + jmeterFailures
                    + "\nreservations=" + accepted
                    + "\norders=" + orders + "\noutboxAtStop=" + outboxAtStop
                    + "\nemailsAtStop=" + emailsAtStop + "\nsettleSeconds=" + settleSeconds
                    + "\npendingOutbox=" + pendingOutbox()
                    + "\nmanualEmails=" + manualEmails + "\npendingEmails=" + pendingEmails
                    + "\ninvalidStock=" + invalidStock + "\ninvalidBalance=" + invalidBalance + "\n";
            Files.writeString(runDirectory.resolve("database-summary.txt"), summary, StandardCharsets.UTF_8);
            int releasedWithoutOrder = db.queryForObject("SELECT COUNT(*) FROM stock_reservation WHERE tier_id BETWEEN ? AND ? AND inventory_state='RELEASED' AND order_id IS NULL", Integer.class, tierId, lastTierId);
            var releaseReasons = db.queryForList("SELECT release_reason,COUNT(*) AS requests FROM stock_reservation WHERE tier_id BETWEEN ? AND ? AND order_id IS NULL GROUP BY release_reason", tierId, lastTierId);
            Files.writeString(runDirectory.resolve("release-reasons.txt"), releaseReasons.toString(), StandardCharsets.UTF_8);
            Files.writeString(runDirectory.resolve("database-summary.txt"), "releasedWithoutOrder=" + releasedWithoutOrder + "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
            if (diagnostics != null) {
                writeDiagnostics(runDirectory, diagnostics, orderListener, orderConsumersAtStop);
            }
            if (multiTier) {
                writeTierResults(runDirectory, reservations, labelColumn, elapsedColumn, successColumn,
                        mode.startsWith("dual"));
            }
            boolean acceptedLoad = jmeterFailures == 0 && releasedWithoutOrder == 0;
            if (!mode.equals("duplicate")) {
                assertEquals(acceptedResponses, accepted, summary);
            }
            assertEquals(0, quotaDifference, summary);
            assertEquals(0, duplicateGuards, summary);
            assertEquals(0, manualRetries, summary);
            assertEquals(0, invalidStock, summary);
            assertEquals(0, invalidBalance, summary);
            assertEquals(accepted, orders + releasedWithoutOrder, summary);
            assertEquals(0, pendingOutbox(), summary);
            assertEquals(0, manualEmails, summary);
            assertEquals(0, pendingEmails, summary);
            if (mode.equals("mixed")) {
                assertTrue(paidOrders > 0, summary);
                assertTrue(canceledOrders > 0, summary);
            }
            return acceptedLoad;
        } finally {
            if (mode.equals("recovery")) {
                listeners.start();
            }
            if (second != null) {
                second.close();
                if (multiTier) {
                    Path secondMetrics = runDirectory.resolve("second-application/runtime-metrics.json");
                    assertTrue(Files.isRegularFile(secondMetrics),
                            "第二应用必须保存自己的指标，不能只以主应用结果代替双实例结果");
                    var node = context.getBean(ObjectMapper.class).readTree(Files.readString(secondMetrics));
                    assertTrue(node.path("orderLockWaitSeconds").asDouble() > 0,
                            "第二应用必须实际参与建单，不能仅启动后接收HTTP请求");
                }
            }
        }
    }

    private void seedSale(int stock) {
        LocalDateTime now = LocalDateTime.now();
        db.update("INSERT INTO city(id,name,code,province) VALUES(?,'Load city',?,'Test province')", tierId, "L" + tierId);
        db.update("INSERT INTO event_category(id,name) VALUES(?,'Load category')", tierId);
        db.update("INSERT INTO event(id,name,category_id,city_id,venue,status,sale_start_time,sale_end_time,"
                + "event_start_time,event_end_time) VALUES(?,'Load event',?,?,'Load venue',1,?,?,?,?)",
                tierId, tierId, tierId, now.minusDays(1), now.plusDays(1), now.plusDays(2), now.plusDays(3));
        db.update("INSERT INTO event_session(id,event_id,session_name,session_time,status) "
                + "VALUES(?,?,'Load session',?,1)", tierId, tierId, now.plusDays(2));
        db.update("INSERT INTO ticket_tier(id,event_id,session_id,tier_name,original_price,current_price,"
                + "total_stock,available_stock,status) VALUES(?,?,?,'Load tier',100,100,?,?,1)",
                tierId, tierId, tierId, stock, stock);
    }

    private int historicalClosed(int historyTier) {
        return db.queryForObject("SELECT COUNT(*) FROM order_info WHERE tier_id=? AND order_status=2",
                Integer.class, historyTier);
    }

    private void prepareHistory(SeckillServiceImpl seckill, int historyTier, Path directory) throws Exception {
        int activeTier = tierId;
        tierId = historyTier;
        try {
            seedSale(1000);
            context.getBean(StockServiceImpl.class).prepare(historyTier);
            long firstUser = 100000L + userSequence;
            seedUsersAndTokens(1000, directory.resolve("history-users.csv"), "abundant");
            for (long user = firstUser; user < firstUser + 1000; user++) {
                seckill.reserveApplication(user, historyTier, 1, "history-" + user);
            }
        } finally {
            tierId = activeTier;
        }

        // 通过真实Outbox和消费者建单，预热的发布及通知须先排空。
        long deadline = System.nanoTime() + Duration.ofMinutes(3).toNanos();
        while (System.nanoTime() < deadline) {
            int orders = db.queryForObject("SELECT COUNT(*) FROM order_info WHERE tier_id=?",
                    Integer.class, historyTier);
            if (orders == 1000 && pendingOutbox() == 0 && pendingEmailTasks() == 0) {
                return;
            }
            Thread.sleep(1000);
        }
        fail("历史订单准备未收敛");
    }

    private void seedUsersAndTokens(int buyers, Path users, String mode) throws IOException {
        List<Object[]> rows = new ArrayList<>(1000);
        try (BufferedWriter writer = Files.newBufferedWriter(users, StandardCharsets.UTF_8)) {
            writer.write("token,tierId,requestKey,userIndex,quantity\n");
            for (int i = 0; i < buyers; i++) {
                long userId = 100000L + userSequence++;
                rows.add(new Object[] { userId, "load-" + userId, "isolated-test-only",
                        "load-" + userId + "@example.invalid" });
                if (rows.size() == 1000 || i == buyers - 1) {
                    db.batchUpdate("INSERT INTO user(id,username,password,email,status,role) "
                            + "VALUES(?,?,?,?,1,'USER')", rows);
                    rows.clear();
                }

                // 使用项目原有 Sa-Token 建立真实 Redis 登录会话。
                String token = StpUtil.createLoginSession(userId);
                int targetTier = mode.contains("multi") ? LoadTierTraffic.targetTier(tierId, i) : tierId;
                String row = token + "," + targetTier + ",load-" + userId + "," + i + ","
                        + (mode.equals("mixed") ? i % 5 + 1 : 1) + "\n";
                writer.write(row);
                if (mode.equals("duplicate")) {
                    writer.write(row);
                }
            }
        }
    }

    private int pendingOutbox() {
        return db.queryForObject("SELECT COUNT(*) FROM message_outbox WHERE publish_status<>'PUBLISHED'",
                Integer.class);
    }

    /** 诊断数据只含具名SQL计数和聚合耗时，不记录Token、参数或消息正文。 */
    private void writeDiagnostics(Path directory, LoadSqlDiagnostics diagnostics,
            SimpleMessageListenerContainer orderListener, int consumersAtStop) throws IOException {
        assertTrue(diagnostics.snapshot().stream().anyMatch(timing -> timing.statement().endsWith(".deduct")),
                "真实扣库存Mapper没有采集到，不能生成诊断结论");
        diagnostics.write(directory.resolve("sql-timings.tsv"));

        // 首发确认耗时与建单等待分开记录，不能把秒级退避当成Broker确认耗时。
        List<Long> firstPublication = db.queryForList("""
                SELECT GREATEST(0,TIMESTAMPDIFF(MICROSECOND,m.created_at,m.updated_at)/1000)
                FROM message_outbox m JOIN stock_reservation r
                  ON m.business_key=CONCAT('create-order:',r.id,':0')
                WHERE r.tier_id BETWEEN ? AND ? AND m.publish_status='PUBLISHED'
                ORDER BY 1
                """, Long.class, tierId, lastTierId);
        Map<String, Object> report = new LinkedHashMap<>();
        // 建单锁单列指标，不与入口用户分段锁等待混淆。
        OrderCreationCoordinator coordinator = context.getBean(OrderCreationCoordinator.class);
        MeterRegistry metrics = context.getBean(MeterRegistry.class);
        var orderWait = metrics.find("seckill.order.lock.wait").timer();
        var orderBusy = metrics.find("seckill.order.lock.busy").counter();
        report.put("orderCoordinationEnabled", ReflectionTestUtils.getField(coordinator, "enabled"));
        report.put("orderLockWaitMillis", ReflectionTestUtils.getField(coordinator, "waitMillis"));
        report.put("orderLockWaitSecondsParent", orderWait == null ? 0 : orderWait.totalTime(TimeUnit.SECONDS));
        report.put("orderLockBusyParent", orderBusy == null ? 0 : orderBusy.count());
        report.put("orderConcurrentMinimum", ReflectionTestUtils.getField(orderListener, "concurrentConsumers"));
        report.put("orderConcurrentMaximum", ReflectionTestUtils.getField(orderListener, "maxConcurrentConsumers"));
        report.put("orderConsumersAtStop", consumersAtStop);
        report.put("firstPublicationCount", firstPublication.size());
        report.put("firstPublicationP95Millis", percentile(firstPublication, .95));
        report.put("firstPublicationMaxMillis", percentile(firstPublication, 1));
        report.put("retryDistribution", db.queryForList("""
                SELECT task_status,retry_count,COUNT(*) AS tasks
                FROM payment_compensation WHERE task_type='MESSAGE_RETRY'
                GROUP BY task_status,retry_count ORDER BY task_status,retry_count
                """));
        report.put("outboxDistribution", db.queryForList("""
                SELECT event_type,publish_status,COUNT(*) AS events,SUM(attempts) AS publication_retries
                FROM message_outbox GROUP BY event_type,publish_status ORDER BY event_type,publish_status
                """));

        // 仅在停压后读取执行计划，避免诊断查询干扰发压阶段。
        LocalDateTime now = LocalDateTime.now();
        report.put("outboxFindDuePlan", db.queryForList("""
                EXPLAIN SELECT * FROM message_outbox
                WHERE (publish_status='NEW' AND next_attempt_at<=?)
                   OR (publish_status='IN_FLIGHT' AND lease_until<=?)
                ORDER BY next_attempt_at LIMIT 100
                """, now, now));
        Files.writeString(directory.resolve("load-diagnostics.json"),
                context.getBean(ObjectMapper.class).writerWithDefaultPrettyPrinter().writeValueAsString(report),
                StandardCharsets.UTF_8);
    }

    private int pendingEmailTasks() {
        return db.queryForObject("SELECT COUNT(*) FROM payment_compensation "
                + "WHERE task_type IN ('EMAIL_ORDER','EMAIL_PAYMENT') AND task_status<>2", Integer.class);
    }

    private boolean quotaConverged() {
        for (int current = tierId; current <= lastTierId; current++) {
            int available = db.queryForObject("SELECT available_stock FROM ticket_tier WHERE id=?", Integer.class, current);
            int pending = db.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM stock_reservation WHERE tier_id=? AND inventory_state='PENDING'", Integer.class, current);
            try {
                if (context.getBean(StockServiceImpl.class).getStock((long) current) != available - pending) {
                    return false;
                }
            } catch (IllegalStateException recovering) {
                return false;
            }
        }
        return true;
    }

    /** 分票档断言，防止总量正确却隐藏普通票档饥饿或不同票档配额差额抵消。 */
    private void writeTierResults(Path directory, List<String[]> samples, int labelColumn,
            int elapsedColumn, int successColumn, boolean dual) throws IOException {
        List<Map<String, Object>> reports = new ArrayList<>();
        for (int current = tierId; current <= lastTierId; current++) {
            String prefix = "reserve-tier-" + current + "-node-";
            List<String[]> selected = samples.stream().filter(row -> row[labelColumn].startsWith(prefix)).toList();
            List<Long> httpLatency = selected.stream().map(row -> Long.parseLong(row[elapsedColumn])).sorted().toList();
            List<Long> orderLatency = db.queryForList("""
                    SELECT GREATEST(0,TIMESTAMPDIFF(MICROSECOND,r.create_time,o.create_time)/1000)
                    FROM stock_reservation r JOIN order_info o ON o.id=r.order_id
                    WHERE r.tier_id=? ORDER BY 1
                    """, Long.class, current);
            int accepted = db.queryForObject("SELECT COUNT(*) FROM stock_reservation WHERE tier_id=?", Integer.class, current);
            int orders = db.queryForObject("SELECT COUNT(*) FROM order_info WHERE tier_id=?", Integer.class, current);
            int available = db.queryForObject("SELECT available_stock FROM ticket_tier WHERE id=?", Integer.class, current);
            int quota = context.getBean(StockServiceImpl.class).getStock((long) current);
            long failures = selected.stream().filter(row -> !row[successColumn].equals("true")).count();
            long firstNode = selected.stream().filter(row -> row[labelColumn].endsWith("-node-0")).count();
            long secondNode = selected.size() - firstNode;
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("tierId", current);
            report.put("kind", current == tierId ? "HOT" : "NORMAL");
            report.put("requests", selected.size());
            report.put("failures", failures);
            report.put("httpP95Millis", percentile(httpLatency, .95));
            report.put("httpP99Millis", percentile(httpLatency, .99));
            report.put("orderP95MillisCoarse", percentile(orderLatency, .95));
            report.put("orderMaxMillisCoarse", percentile(orderLatency, 1));
            report.put("accepted", accepted);
            report.put("orders", orders);
            report.put("databaseStock", available);
            report.put("redisQuota", quota);
            report.put("firstTargetRequests", firstNode);
            report.put("secondTargetRequests", secondNode);
            reports.add(report);
        }
        Files.writeString(directory.resolve("tier-results.json"), context.getBean(ObjectMapper.class)
                .writerWithDefaultPrettyPrinter().writeValueAsString(reports), StandardCharsets.UTF_8);

        // 每个普通票档必须真实受理并落单；双节点模式还必须覆盖各票档的两个HTTP入口。
        for (Map<String, Object> report : reports) {
            assertTrue(((Number) report.get("requests")).intValue() > 0, report.toString());
            assertEquals(0L, report.get("failures"), report.toString());
            assertEquals(((Number) report.get("requests")).intValue(), report.get("accepted"), report.toString());
            assertEquals(report.get("accepted"), report.get("orders"), report.toString());
            assertEquals(report.get("databaseStock"), report.get("redisQuota"), report.toString());
            if (dual) {
                assertTrue(((Number) report.get("firstTargetRequests")).longValue() > 0, report.toString());
                assertTrue(((Number) report.get("secondTargetRequests")).longValue() > 0, report.toString());
            }
        }
    }

    private int pendingReservations() {
        return db.queryForObject("SELECT COUNT(*) FROM stock_reservation WHERE inventory_state='PENDING'", Integer.class);
    }

    private double lockWait(MeterRegistry metrics) {
        var timer = metrics.find("seckill.lock.wait").timer();
        return timer == null ? 0 : timer.totalTime(TimeUnit.SECONDS);
    }

    private double conflicts(MeterRegistry metrics) {
        var counter = metrics.find("seckill.stock.optimistic.conflicts").counter();
        return counter == null ? 0 : counter.count();
    }

    private long percentile(List<Long> sorted, double fraction) {
        return sorted.isEmpty() ? 0 : sorted.get(Math.max(0, (int) Math.ceil(sorted.size() * fraction) - 1));
    }

    private String[] csvRow(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields.toArray(String[]::new);
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
