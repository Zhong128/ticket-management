package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.config.CommonConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.retry.support.RetryTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 显式启用才调用真实模型；只发送虚构探针，不连接业务数据库或执行交易。 */
@EnabledIfSystemProperty(named = "agent.live-model.enabled", matches = "true")
class AgentModelLiveIT {

    @Test
    void configuredModelStreamsAndCompletesOneReadOnlyToolRoundTrip() throws Exception {
        Properties local = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("config/application-local.properties"), StandardCharsets.UTF_8)) {
            local.load(reader);
        }
        String key = local.getProperty("OPENAI_API_KEY", "").trim();
        assertFalse(key.isBlank(), "本地API Key未配置");
        var environment = new StandardEnvironment();
        for (var source : new YamlPropertySourceLoader().load("live-options", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addFirst(source);
        }
        var options = Binder.get(environment).bind("spring.ai.openai.chat.options", Bindable.of(OpenAiChatOptions.class)).get();
        options.setModel(local.getProperty("spring.ai.openai.chat.options.model", options.getModel()));
        options.setMaxTokens(128);
        String baseUrl = local.getProperty("spring.ai.openai.base-url");
        assertNotNull(baseUrl);
        var api = OpenAiApi.builder().baseUrl(baseUrl).apiKey(key).build();
        var model = OpenAiChatModel.builder().openAiApi(api).defaultOptions(options)
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).noBackoff().build()).build();
        var client = new CommonConfig().chatClient(model);
        AtomicInteger chunks = new AtomicInteger();
        ReadOnlyProbe probe = new ReadOnlyProbe();
        try {
            String text = client.prompt().user("这是连通性测试，请仅回复：配置验证成功")
                    .stream().content().doOnNext(part -> chunks.incrementAndGet()).reduce("", String::concat)
                    .block(Duration.ofSeconds(45));
            assertNotNull(text);
            assertTrue(text.contains("配置验证成功"));
            assertTrue(chunks.get() > 0);
            String answer = client.prompt()
                    .user("请调用configurationReadOnlyProbe工具一次，查询虚构的probeValue，然后只用一句话报告该值。"
                            + "这是隔离的只读配置检测，不查询任何用户数据，不执行购票、取消或支付。")
                    .toolCallbacks(ToolCallbacks.from(probe)).stream().content().reduce("", String::concat)
                    .block(Duration.ofSeconds(45));
            assertEquals(1, probe.calls.get());
            assertNotNull(answer);
            assertTrue(answer.contains("7") || answer.contains("七"));
            System.out.println("LIVE_MODEL_PASS model=" + options.getModel() + " stream=true readonlyToolCalls=1");
        } catch (Exception failure) {
            // 不将可能包含请求信息的底层异常或凭据写进报告。
            fail("真实模型验证失败，异常类型=" + failure.getClass().getSimpleName());
        }
    }

    public static class ReadOnlyProbe {

        private final AtomicInteger calls = new AtomicInteger();

        @Tool(description = "只读配置探针，返回虚构probeValue；不访问账号、订单、库存或资金")
        public Map<String, Object> configurationReadOnlyProbe() {
            if (calls.incrementAndGet() > 1) {
                throw new IllegalStateException("本次验证只允许一次只读探针");
            }
            return Map.of("status", "SUCCESS", "data", Map.of("probeValue", 7));
        }
    }
}
