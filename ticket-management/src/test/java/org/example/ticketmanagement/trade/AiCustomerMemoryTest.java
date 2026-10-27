package org.example.ticketmanagement.trade;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Validation;
import org.example.ticketmanagement.controller.AiCustomerServiceController;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiCustomerMemoryTest {

    @Test
    void clearRemovesOnlyCurrentUsersMemoryWithoutCallingModel() {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        AiCustomerServiceController controller = mock(AiCustomerServiceController.class, CALLS_REAL_METHODS);
        ChatMemory memory = MessageWindowChatMemory.builder().build();
        memory.add("user_91", new UserMessage("only-first-user"));
        memory.add("user_92", new UserMessage("only-second-user"));
        ReflectionTestUtils.setField(controller, "chatMemory", memory);
        try (var token = mockStatic(StpUtil.class)) {
            token.when(StpUtil::getLoginIdAsLong).thenReturn(91L);
            assertEquals(1, controller.clearHistory().getCode());
        }
        assertTrue(memory.get("user_91").isEmpty());
        assertEquals(1, memory.get("user_92").size());
        verifyNoInteractions(client);
    }

    @Test
    void maintenanceRejectsChatWithoutCallingExternalModel() {
        ChatClient client = mock(ChatClient.class);
        AiCustomerServiceController controller = mock(AiCustomerServiceController.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(controller, "chatClient", client);
        ReflectionTestUtils.setField(controller, "enabled", false);
        var result = controller.chat(new AiCustomerServiceController.ChatRequest("查询订单"));
        assertEquals(503, result.getCode());
        assertTrue(result.getMsg().contains("维护中"));
        verifyNoInteractions(client);
    }

    @Test
    void chatRejectsBlankAndOversizedMessagesBeforeCallingModel() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new AiCustomerServiceController.ChatRequest(" ")).isEmpty());
            assertFalse(validator.validate(new AiCustomerServiceController.ChatRequest("x".repeat(2001))).isEmpty());
            assertTrue(validator.validate(new AiCustomerServiceController.ChatRequest("如何查看订单？")).isEmpty());
        }
    }
}
