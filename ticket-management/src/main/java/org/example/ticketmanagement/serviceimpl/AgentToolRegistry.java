package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class AgentToolRegistry {

    private static final Set<String> EVENT_TOOLS = Set.of("queryEventsByCity", "queryEventsByArtist",
            "getEventDetail", "getEventSessions", "getSessionDetail", "getSessionTiers", "getRealTimeStock");
    private static final Set<String> ACCOUNT_TOOLS = Set.of("getUserProfile", "getUserAddresses");
    private static final Set<String> ORDER_TOOLS = Set.of("getUserOrders", "getOrderDetail",
            "getReservationStatus", "checkSeckillQualification");

    private final CustomerServiceTools tools;
    private final AgentActionTools actions;

    public ToolCallback[] callbacks(AgentRunBudget budget) {
        // 显式分组白名单，新加注解不会自动获得模型调用权限。
        return Stream.concat(Arrays.stream(ToolCallbacks.from(tools)), Arrays.stream(ToolCallbacks.from(actions)))
                .filter(tool -> allowed(tool.getToolDefinition().name()))
                .map(budget::wrap)
                .toArray(ToolCallback[]::new);
    }

    private boolean allowed(String name) {
        return EVENT_TOOLS.contains(name) || ACCOUNT_TOOLS.contains(name) || ORDER_TOOLS.contains(name)
                || Set.of("proposePurchase", "proposeCancellation").contains(name);
    }
}
