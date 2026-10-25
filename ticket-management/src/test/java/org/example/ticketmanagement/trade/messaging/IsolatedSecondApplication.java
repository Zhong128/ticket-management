package org.example.ticketmanagement.trade.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import com.alipay.api.AlipayClient;
import org.example.ticketmanagement.TicketManagementApplication;
import org.example.ticketmanagement.service.EmailService;
import org.example.ticketmanagement.trade.payment.PaymentGateway;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 第二个独立JVM，端口动态分配，只连接调用方提供的临时中间件。 */
final class IsolatedSecondApplication implements AutoCloseable {
    private final Process process;
    private final Path stopFile;
    final int port;

    IsolatedSecondApplication(Map<String, String> endpoints, Path directory) throws Exception {
        Files.createDirectories(directory);
        Path config = directory.resolve("application.properties").toAbsolutePath();
        Properties values = new Properties();
        values.putAll(endpoints);
        values.setProperty("server.port", "0");
        values.setProperty("spring.flyway.enabled", "false");
        values.setProperty("logging.level.org.example.ticketmanagement", "WARN");
        try (var writer = Files.newBufferedWriter(config)) {
            values.store(writer, "Disposable test endpoints only");
        }
        Path portFile = directory.resolve("port.txt").toAbsolutePath();
        stopFile = directory.resolve("stop.signal").toAbsolutePath();
        Files.deleteIfExists(portFile);
        Files.deleteIfExists(stopFile);
        Files.deleteIfExists(directory.resolve("runtime-metrics.json"));
        Path arguments = directory.resolve("java.args").toAbsolutePath();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Files.writeString(arguments, "-Djdk.net.unixdomain.tmpdir=" + Path.of("target").toAbsolutePath().toString().replace("\\", "/") + "\n-cp\n\"" + classpath.replace("\\", "/") + "\"\n"
                + IsolatedSecondApplication.class.getName() + "\n\"" + portFile.toString().replace("\\", "/")
                + "\"\n\"--spring.config.location=classpath:application-trade-isolated-it.properties,"
                + config.toUri() + "\"\n--spring.config.import=\n", StandardCharsets.UTF_8);
        process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "@" + arguments).redirectErrorStream(true).redirectOutput(directory.resolve("application.log").toFile()).start();
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        while (process.isAlive() && !Files.exists(portFile) && System.nanoTime() < deadline) {
            Thread.sleep(250);
        }
        if (!Files.exists(portFile)) {
            close();
            throw new IllegalStateException("第二应用未启动，详见 " + directory.resolve("application.log"));
        }
        port = Integer.parseInt(Files.readString(portFile).trim());
    }

    @Override
    public void close() throws Exception {
        // Windows的destroy可能直接终止JVM；用本地信号请求保存指标并正常关闭。
        try {
            if (process.isAlive()) {
                Files.writeString(stopFile, "stop", StandardCharsets.UTF_8);
            }
            process.waitFor(15, TimeUnit.SECONDS);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly().waitFor();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String[] springArguments = Arrays.copyOfRange(args, 1, args.length);
        var context = (ServletWebServerApplicationContext) SpringApplication.run(
                new Class<?>[] {TicketManagementApplication.class, Channels.class}, springArguments);
        // 保存第二个JVM自己的指标，在正常关闭前读取，不依赖Windows终止进程时的钩子。
        MeterRegistry registry = context.getBean(MeterRegistry.class);
        var conflicts = registry.counter("seckill.stock.optimistic.conflicts");
        var busy = registry.counter("seckill.order.lock.busy");
        var wait = registry.timer("seckill.order.lock.wait");
        var releaseFailures = registry.counter("seckill.order.lock.release.failures");
        Path metricsFile = Path.of(args[0]).getParent().resolve("runtime-metrics.json");
        Files.writeString(Path.of(args[0]), Integer.toString(context.getWebServer().getPort()));
        while (!Files.exists(Path.of(args[0]).getParent().resolve("stop.signal"))) {
            Thread.sleep(100);
        }
        Files.writeString(metricsFile, new ObjectMapper().writeValueAsString(Map.of(
                "optimisticConflicts", conflicts.count(), "orderLockBusy", busy.count(),
                "orderLockWaitSeconds", wait.totalTime(TimeUnit.SECONDS),
                "orderLockReleaseFailures", releaseFailures.count())), StandardCharsets.UTF_8);
        context.close();
    }

    @TestConfiguration
    static class Channels {
        @Bean
        @Primary
        EmailService isolatedEmail() {
            EmailService email = mock(EmailService.class);
            when(email.sendSimpleEmail(anyString(), anyString(), anyString())).thenReturn(true);
            return email;
        }

        @Bean
        @Primary
        PaymentGateway isolatedGateway() {
            return mock(PaymentGateway.class);
        }

        @Bean
        @Primary
        OpenAiChatModel isolatedAi() {
            return mock(OpenAiChatModel.class);
        }

        @Bean
        @Primary
        AlipayClient isolatedAlipay() {
            return mock(AlipayClient.class);
        }
    }
}
