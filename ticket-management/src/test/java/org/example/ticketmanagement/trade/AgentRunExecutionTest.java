package org.example.ticketmanagement.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ticketmanagement.dto.AgentRunStart;
import org.example.ticketmanagement.dto.AgentStreamEvent;
import org.example.ticketmanagement.exception.AgentRunException;
import org.example.ticketmanagement.pojo.AgentRun;
import org.example.ticketmanagement.serviceimpl.AgentConversationService;
import org.example.ticketmanagement.serviceimpl.AgentConversationStore;
import org.example.ticketmanagement.serviceimpl.AgentModelGateway;
import org.example.ticketmanagement.serviceimpl.AgentRunBudget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentRunExecutionTest {

    private final AgentConversationStore store = mock(AgentConversationStore.class);
    private final AgentModelGateway gateway = mock(AgentModelGateway.class);
    private final Clock clock = Clock.systemUTC();

    @Test
    void streamPersistsOneReplyAndDisconnectDoesNotReplayOrCancelRun() {
        AgentRun run = running();
        when(store.start(eq(91L), eq("conversation"), eq("client"), eq("问题"), anyInt()))
                .thenReturn(new AgentRunStart(run, true));
        when(store.context(91L, "conversation")).thenReturn(List.of());
        when(gateway.stream(eq(91L), anyList(), eq("问题"), any())).thenReturn(Flux.just("回", "复"));
        AgentRun done = running();
        done.setStatus("SUCCEEDED");
        done.setReply("回复");
        when(store.getRun(91L, "run")).thenReturn(done);
        when(store.complete(anyLong(), anyString(), anyString(), any(), any(), any())).thenReturn(true);
        var service = service();
        List<AgentStreamEvent> events = new ArrayList<>();
        service.stream(91L, "conversation", "client", "问题", events::add);
        assertEquals(List.of("run_started", "text_delta", "text_delta", "done"),
                events.stream().map(AgentStreamEvent::name).toList());
        verify(store).complete(91L, "run", "SUCCEEDED", "回复", null, null);
        verify(gateway, times(1)).stream(anyLong(), anyList(), anyString(), any());

        reset(gateway);
        when(gateway.stream(eq(91L), anyList(), eq("问题"), any())).thenReturn(Flux.just("回复"));
        service.stream(91L, "conversation", "client", "问题", event -> { throw new IllegalStateException("断流"); });
        verify(store, times(2)).complete(91L, "run", "SUCCEEDED", "回复", null, null);
    }

    @Test
    void replayOnlyReturnsExistingRunWithoutModelCall() {
        when(store.start(anyLong(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new AgentRunStart(running(), false));
        List<AgentStreamEvent> events = new ArrayList<>();
        service().stream(91L, "conversation", "client", "问题", events::add);
        assertEquals(List.of("run_started", "done"), events.stream().map(AgentStreamEvent::name).toList());
        verifyNoInteractions(gateway);
    }

    @Test
    void emptyOrOversizedReplyFailsWithoutFakeSuccess() {
        when(store.start(anyLong(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new AgentRunStart(running(), true));
        when(store.context(anyLong(), anyString())).thenReturn(List.of());
        when(store.getRun(anyLong(), anyString())).thenReturn(running());
        when(gateway.stream(anyLong(), anyList(), anyString(), any())).thenReturn(Flux.empty());
        service().stream(91L, "conversation", "client", "问题", event -> {});
        verify(store).complete(eq(91L), eq("run"), eq("FAILED"), any(), eq("EMPTY_REPLY"), anyString());
        var service = service();
        ReflectionTestUtils.setField(service, "maxReplyChars", 3);
        when(gateway.stream(anyLong(), anyList(), anyString(), any())).thenReturn(Flux.just("超长回复"));
        service.stream(91L, "conversation", "client", "问题", event -> {});
        verify(store).complete(eq(91L), eq("run"), eq("FAILED"), any(), eq("REPLY_TOO_LARGE"), anyString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void absoluteDeadlineDoesNotResetOnContinuousTextOrWaitForever(boolean continuous) throws Exception {
        AgentRun run = running();
        run.setDeadlineAt(LocalDateTime.now(clock).plusSeconds(1));
        when(store.start(anyLong(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new AgentRunStart(run, true));
        when(store.context(anyLong(), anyString())).thenReturn(List.of());
        when(gateway.stream(anyLong(), anyList(), anyString(), any())).thenReturn(continuous
                ? Flux.interval(Duration.ofMillis(100)).map(index -> "片") : Flux.never());
        when(store.getRun(anyLong(), anyString())).thenReturn(run);
        CountDownLatch completed = new CountDownLatch(1);
        service().stream(91L, "conversation", "client", "问题", event -> {
            if (event.name().equals("done")) {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        verify(store).complete(eq(91L), eq("run"), eq("TIMED_OUT"), any(), eq("RUN_TIMEOUT"), anyString());
    }

    @Test
    void toolBudgetStopsNinthCallAndNeverPersistsArgumentsOrResults() {
        when(store.recordTool(anyString(), anyInt(), anyString())).thenReturn(true);
        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.getToolDefinition()).thenReturn(ToolDefinition.builder().name("query")
                .description("查询").inputSchema("{}").build());
        when(delegate.call(anyString(), any())).thenReturn("{\"status\":\"SUCCESS\",\"data\":\"private-result\"}");
        var budget = new AgentRunBudget("run", clock.instant().plusSeconds(60), clock, 8,
                store, new ObjectMapper(), event -> {});
        ToolCallback callback = budget.wrap(delegate);
        for (int i = 0; i < 8; i++) {
            callback.call("private-arguments");
        }
        assertEquals("TOOL_LIMIT", assertThrows(AgentRunException.class,
                () -> callback.call("private-arguments")).getCode());
        verify(delegate, times(8)).call(anyString(), any());
        verify(store, atLeastOnce()).recordTool(eq("run"), anyInt(), argThat(trace -> !trace.contains("private")));
    }

    private AgentConversationService service() {
        return new AgentConversationService(store, gateway, new ObjectMapper(), clock, Schedulers.immediate());
    }

    private AgentRun running() {
        AgentRun run = new AgentRun();
        run.setId("run");
        run.setConversationId("conversation");
        run.setClientMessageId("client");
        run.setStatus("RUNNING");
        run.setDeadlineAt(LocalDateTime.now(clock).plusSeconds(60));
        return run;
    }
}
