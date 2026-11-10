package org.example.ticketmanagement.trade;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Validation;
import org.example.ticketmanagement.controller.AiCustomerServiceController;
import org.example.ticketmanagement.service.CustomerAgentService;
import org.example.ticketmanagement.pojo.AgentConversation;
import org.example.ticketmanagement.serviceimpl.AgentConversationStore;
import org.example.ticketmanagement.serviceimpl.AgentConversationService;
import org.example.ticketmanagement.serviceimpl.CustomerAgentServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiCustomerMemoryTest {

    @Test
    void clearUsesCurrentUsersDefaultPersistentConversationWithoutCallingModel() {
        AgentConversationStore store = mock(AgentConversationStore.class);
        AgentConversationService conversations = mock(AgentConversationService.class);
        AgentConversation conversation = new AgentConversation();
        conversation.setId("default-91");
        when(store.defaultConversation(91L)).thenReturn(conversation);
        AiCustomerServiceController controller = mock(AiCustomerServiceController.class, CALLS_REAL_METHODS);
        CustomerAgentService service = new CustomerAgentServiceImpl(conversations, store);
        ReflectionTestUtils.setField(controller, "customerAgentService", service);
        try (var token = mockStatic(StpUtil.class)) {
            token.when(StpUtil::getLoginIdAsLong).thenReturn(91L);
            assertEquals(1, controller.clearHistory().getCode());
        }
        verify(store).clear(91L, "default-91");
        verifyNoInteractions(conversations);
    }

    @Test
    void maintenanceRejectsChatWithoutCallingExternalModel() {
        CustomerAgentService service = mock(CustomerAgentService.class);
        AiCustomerServiceController controller = mock(AiCustomerServiceController.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(controller, "customerAgentService", service);
        ReflectionTestUtils.setField(controller, "enabled", false);
        var result = controller.chat(new AiCustomerServiceController.ChatRequest("查询订单"));
        assertEquals(503, result.getCode());
        assertTrue(result.getMsg().contains("维护中"));
        verifyNoInteractions(service);
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
