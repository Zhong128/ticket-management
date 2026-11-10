package org.example.ticketmanagement.serviceimpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.example.ticketmanagement.dto.AgentStreamEvent;
import org.example.ticketmanagement.dto.AgentCitation;
import org.example.ticketmanagement.exception.AgentRunException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** 单次运行共享工具预算，禁止模型通过多轮工具调用绕过上限。 */
public class AgentRunBudget {

    private final String runId;
    private final Instant deadline;
    private final Clock clock;
    private final int limit;
    private final AgentConversationStore store;
    private final ObjectMapper json;
    private final Consumer<AgentStreamEvent> events;
    private final List<Map<String, Object>> trace = new ArrayList<>();
    private boolean stopped;

    public AgentRunBudget(String runId, Instant deadline, Clock clock, int limit,
                          AgentConversationStore store, ObjectMapper json, Consumer<AgentStreamEvent> events) {
        this.runId = runId;
        this.deadline = deadline;
        this.clock = clock;
        this.limit = limit;
        this.store = store;
        this.json = json;
        this.events = events;
    }

    public ToolCallback wrap(ToolCallback delegate) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return delegate.getToolDefinition();
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return delegate.getToolMetadata();
            }

            @Override
            public String call(String input) {
                return call(input, new ToolContext(Map.of()));
            }

            @Override
            public String call(String input, ToolContext context) {
                int slot = begin(delegate.getToolDefinition().name());
                long started = System.nanoTime();
                String result;
                try {
                    result = delegate.call(input, context);
                } catch (RuntimeException failure) {
                    end(slot, "ERROR", "TOOL_EXECUTION_FAILED", started);
                    throw failure;
                }
                String status = "SUCCESS";
                String errorCode = null;
                JsonNode action = null;
                try {
                    var parsed = json.readTree(result);
                    status = parsed.path("status").asText("SUCCESS");
                    errorCode = parsed.path("errorCode").isTextual() ? parsed.path("errorCode").asText() : null;
                    if ("SUCCESS".equals(status) && Set.of("proposePurchase", "proposeCancellation")
                            .contains(delegate.getToolDefinition().name()) && parsed.path("data").hasNonNull("id")) {
                        action = parsed.path("data");
                    }
                } catch (JsonProcessingException ignored) {
                    // 工具返回值仍交给模型，运行记录仅保留有限的执行摘要。
                }
                end(slot, status, errorCode, started);
                if (action != null) {
                    events.accept(new AgentStreamEvent("action_proposed", Map.of("runId", runId, "action", action)));
                }
                return result;
            }
        };
    }

    public synchronized void check() {
        if (stopped || !clock.instant().isBefore(deadline)) {
            throw new AgentRunException("RUN_TIMEOUT");
        }
    }

    public String runId() {
        return runId;
    }

    public synchronized void stop() {
        stopped = true;
    }

    public synchronized void sources(List<AgentCitation> citations) {
        check();
        if (!store.recordCitations(runId, citations)) {
            throw new AgentRunException("RUN_TIMEOUT");
        }
        events.accept(new AgentStreamEvent("knowledge_sources", Map.of("runId", runId, "citations", citations)));
    }

    private synchronized int begin(String name) {
        check();
        if (trace.size() >= limit) {
            throw new AgentRunException("TOOL_LIMIT");
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("runId", runId);
        item.put("toolName", name);
        item.put("status", "STARTED");
        item.put("toolCalls", trace.size() + 1);
        trace.add(item);
        persist();
        events.accept(new AgentStreamEvent("tool_status", Map.copyOf(item)));
        return trace.size() - 1;
    }

    private synchronized void end(int slot, String status, String errorCode, long started) {
        check();
        Map<String, Object> item = trace.get(slot);
        item.put("status", status);
        item.put("durationMs", (System.nanoTime() - started) / 1_000_000);
        if (errorCode != null) {
            item.put("errorCode", errorCode);
        }
        persist();
        events.accept(new AgentStreamEvent("tool_status", Map.copyOf(item)));
    }

    private void persist() {
        try {
            if (!store.recordTool(runId, trace.size(), json.writeValueAsString(trace))) {
                throw new AgentRunException("RUN_TIMEOUT");
            }
        } catch (JsonProcessingException failure) {
            throw new AgentRunException("TRACE_FAILED");
        }
    }
}
