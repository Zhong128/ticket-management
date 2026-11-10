package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.serviceimpl.AgentActionService;
import org.example.ticketmanagement.serviceimpl.AgentActionTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentActionToolsTest {

    @Test
    void missingServerIdentityCannotCreateDraft() {
        AgentActionService service = mock(AgentActionService.class);
        var tools = new AgentActionTools(service);
        assertEquals("REJECTED", tools.proposePurchase(1L, 1, new ToolContext(Map.of())).status());
        assertEquals("REJECTED", tools.proposeCancellation(1L, new ToolContext(Map.of("userId", 1L))).status());
        verifyNoInteractions(service);
    }

    @Test
    void validDraftUsesOnlyServerContextIdentity() {
        AgentActionService service = mock(AgentActionService.class);
        var tools = new AgentActionTools(service);
        tools.proposePurchase(5L, 2, new ToolContext(Map.of("userId", 9L, "runId", "server-run")));
        verify(service).proposePurchase(9L, "server-run", 5L, 2);
        verifyNoMoreInteractions(service);
    }
}
