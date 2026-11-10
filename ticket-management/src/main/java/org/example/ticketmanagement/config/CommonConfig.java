package org.example.ticketmanagement.config;

import org.example.ticketmanagement.constant.CustomerAgentPrompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonConfig {

    @Bean
    public ChatClient chatClient(OpenAiChatModel openAiChatModel) {
        // 上下文由持久会话重建，不能叠加默认内存Advisor造成重复或跨会话污染。
        return ChatClient.builder(openAiChatModel)
                .defaultSystem(CustomerAgentPrompt.SYSTEM)
                .build();
    }
}
