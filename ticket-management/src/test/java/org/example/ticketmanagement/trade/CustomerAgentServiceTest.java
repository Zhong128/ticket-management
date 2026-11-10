package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.config.CommonConfig;
import org.example.ticketmanagement.constant.CustomerAgentPrompt;
import org.example.ticketmanagement.serviceimpl.AgentModelGateway;
import org.example.ticketmanagement.serviceimpl.AgentRunBudget;
import org.example.ticketmanagement.serviceimpl.AgentToolRegistry;
import org.example.ticketmanagement.serviceimpl.CustomerServiceTools;
import org.example.ticketmanagement.serviceimpl.AgentKnowledgeService;
import org.example.ticketmanagement.serviceimpl.AgentActionTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerAgentServiceTest {

    @Test
    void streamingClientUsesOnlyExplicitHistoryAndServerToolIdentity() {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(
                List.of(new Generation(new AssistantMessage("查询回复"))))));
        var tools = mock(CustomerServiceTools.class);
        var registry = new AgentToolRegistry(tools, mock(AgentActionTools.class));
        var knowledge = mock(AgentKnowledgeService.class);
        when(knowledge.search(anyString())).thenReturn(List.of());
        when(knowledge.citations(anyList())).thenReturn(List.of());
        var gateway = new AgentModelGateway(new CommonConfig().chatClient(model), registry, knowledge, new ObjectMapper());
        AgentRunBudget budget = mock(AgentRunBudget.class);
        when(budget.runId()).thenReturn("server-run");
        when(budget.wrap(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals("查询回复", gateway.stream(91L, List.of(new UserMessage("first-user-marker")),
                "follow-up", budget).reduce("", String::concat).block());
        gateway.stream(92L, List.of(), "second-user-marker", budget).blockLast();
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).stream(prompts.capture());
        assertTrue(prompts.getAllValues().getFirst().getInstructions().toString().contains("first-user-marker"));
        assertFalse(prompts.getAllValues().get(1).getInstructions().toString().contains("first-user-marker"));
        var options = (ToolCallingChatOptions) prompts.getAllValues().getFirst().getOptions();
        assertEquals(91L, options.getToolContext().get("userId"));
        assertEquals("server-run", options.getToolContext().get("runId"));
        assertEquals(15, options.getToolCallbacks().size());
        assertTrue(options.getToolCallbacks().stream().anyMatch(tool ->
                tool.getToolDefinition().name().equals("proposePurchase")));
        options.getToolCallbacks().stream()
                .filter(tool -> tool.getToolDefinition().name().startsWith("propose"))
                .forEach(tool -> {
                    assertFalse(tool.getToolDefinition().inputSchema().contains("userId"));
                    assertFalse(tool.getToolDefinition().inputSchema().contains("runId"));
                });
        assertEquals(CustomerAgentPrompt.SYSTEM, prompts.getAllValues().getFirst().getSystemMessage().getText());
        assertTrue(options.getToolCallbacks().stream().noneMatch(tool ->
                List.of("createOrder", "cancelOrder", "updateUserProfile", "addUserAddress")
                        .contains(tool.getToolDefinition().name())));
    }
}
