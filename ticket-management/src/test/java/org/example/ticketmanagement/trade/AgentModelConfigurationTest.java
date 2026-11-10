package org.example.ticketmanagement.trade;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.*;

class AgentModelConfigurationTest {

    @Test
    void flashConfigurationDisablesThinkingHistoryAndPreservesVendorParameterNames() throws Exception {
        var environment = new StandardEnvironment();
        for (var source : new YamlPropertySourceLoader().load("model-test", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addFirst(source);
        }
        var options = Binder.get(environment).bind("spring.ai.openai.chat.options",
                Bindable.of(OpenAiChatOptions.class)).get();
        assertEquals("qwen3.8-flash", options.getModel());
        assertNotNull(options.getExtraBody());
        assertEquals(false, options.getExtraBody().get("enable_thinking"));
        assertEquals(false, options.getExtraBody().get("preserve_thinking"));
    }
}
