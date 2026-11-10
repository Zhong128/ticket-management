package org.example.ticketmanagement.serviceimpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.AgentStreamEvent;
import org.example.ticketmanagement.exception.AgentRequestException;
import org.example.ticketmanagement.exception.AgentRunException;
import org.example.ticketmanagement.pojo.AgentRun;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
public class AgentConversationService {

    private final AgentConversationStore store;
    private final AgentModelGateway gateway;
    private final ObjectMapper json;
    private final Clock clock;
    private final Scheduler scheduler;

    @Value("${customer-service.enabled:true}")
    private boolean enabled = true;
    @Value("${customer-service.max-run-seconds:60}")
    private int maxRunSeconds = 60;
    @Value("${customer-service.max-tool-calls:8}")
    private int maxToolCalls = 8;
    @Value("${customer-service.max-reply-chars:32768}")
    private int maxReplyChars = 32768;

    public AgentConversationService(AgentConversationStore store, AgentModelGateway gateway, ObjectMapper json,
                                    Clock clock, @Qualifier("agentModelScheduler") Scheduler scheduler) {
        this.store = store;
        this.gateway = gateway;
        this.json = json;
        this.clock = clock;
        this.scheduler = scheduler;
    }

    public void stream(long userId, String conversationId, String clientId, String message,
                       Consumer<AgentStreamEvent> consumer) {
        if (!enabled) {
            throw new AgentRequestException(503, "智能客服功能维护中，请稍后再试");
        }

        // 持久受理在任何模型调用之前完成；相同客户端消息只返回原运行。
        var accepted = store.start(userId, conversationId, clientId, message, boundedSeconds());
        AgentRun run = accepted.run();
        Consumer<AgentStreamEvent> events = event -> safeSend(consumer, event);
        events.accept(new AgentStreamEvent("run_started", Map.of("runId", run.getId(),
                "conversationId", conversationId, "status", run.getStatus(), "replayed", !accepted.created())));
        if (!accepted.created()) {
            events.accept(new AgentStreamEvent("done", run));
            return;
        }

        Instant deadline = run.getDeadlineAt().atZone(clock.getZone()).toInstant();
        AgentRunBudget budget = new AgentRunBudget(run.getId(), deadline, clock,
                Math.clamp(maxToolCalls, 1, 8), store, json, events);
        StringBuffer reply = new StringBuffer();
        Duration remaining = Duration.between(clock.instant(), deadline);
        if (remaining.isNegative() || remaining.isZero()) {
            finish(userId, run, budget, reply, new AgentRunException("RUN_TIMEOUT"), events);
            return;
        }

        // 独立总时限不会因收到新分片而重置；连接断开不取消后端运行或自动重发。
        Mono<Long> deadlineSignal = Mono.delay(remaining)
                .flatMap(ignored -> Mono.error(new AgentRunException("RUN_TIMEOUT")));
        Flux.defer(() -> gateway.stream(userId, store.context(userId, conversationId), message.trim(), budget))
                .subscribeOn(scheduler)
                .takeUntilOther(deadlineSignal)
                .subscribe(text -> {
                    budget.check();
                    if (reply.length() + text.length() > Math.clamp(maxReplyChars, 1, 32768)) {
                        throw new AgentRunException("REPLY_TOO_LARGE");
                    }
                    reply.append(text);
                    events.accept(new AgentStreamEvent("text_delta", Map.of("runId", run.getId(), "text", text)));
                }, failure -> finish(userId, run, budget, reply, failure, events),
                        () -> finish(userId, run, budget, reply,
                                reply.toString().isBlank() ? new AgentRunException("EMPTY_REPLY") : null, events));
    }

    public String legacyChat(long userId, String message) {
        String conversationId = store.defaultConversation(userId).getId();
        CompletableFuture<AgentRun> result = new CompletableFuture<>();
        stream(userId, conversationId, UUID.randomUUID().toString(), message, event -> {
            if ("done".equals(event.name())) {
                result.complete((AgentRun) event.data());
            } else if ("error".equals(event.name())) {
                // 终态随后由done给出；数据库不可用时也让同步入口尽快返回。
                var data = (Map<?, ?>) event.data();
                if ("RESULT_PENDING".equals(data.get("errorCode"))) {
                    result.completeExceptionally(new AgentRequestException(503, "结果暂未确认，请查询会话记录"));
                }
            }
        });
        try {
            AgentRun run = result.get(boundedSeconds() + 5L, TimeUnit.SECONDS);
            if (!"SUCCEEDED".equals(run.getStatus())) {
                throw new AgentRequestException(503, run.getErrorMessage() == null ? "查询未完成" : run.getErrorMessage());
            }
            return run.getReply();
        } catch (AgentRequestException failure) {
            throw failure;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AgentRequestException(503, "连接已中断，运行结果请查看会话记录");
        } catch (Exception failure) {
            throw new AgentRequestException(503, "查询结果暂未确认，请查看会话记录");
        }
    }

    private void finish(long userId, AgentRun run, AgentRunBudget budget, StringBuffer reply,
                        Throwable failure, Consumer<AgentStreamEvent> events) {
        budget.stop();
        String code = failure == null ? null : failureCode(failure);
        String status = failure == null ? "SUCCEEDED" : "RUN_TIMEOUT".equals(code) ? "TIMED_OUT" : "FAILED";
        String message = failure == null ? null : failureMessage(code);
        try {
            store.complete(userId, run.getId(), status, reply.toString(), code, message);
            AgentRun terminal = store.getRun(userId, run.getId());
            if (!"SUCCEEDED".equals(terminal.getStatus())) {
                events.accept(new AgentStreamEvent("error", Map.of("runId", run.getId(),
                        "status", terminal.getStatus(), "errorCode", terminal.getErrorCode() == null ? "QUERY_FAILED" : terminal.getErrorCode(),
                        "message", terminal.getErrorMessage() == null ? "查询未完成" : terminal.getErrorMessage())));
            }
            events.accept(new AgentStreamEvent("done", terminal));
        } catch (Exception persistenceFailure) {
            // 不把未落库结果伪装为成功；客户端只能查询原运行，不能自动重新请求模型。
            log.warn("客服结果保存暂不可用: runId={}, exception={}", run.getId(),
                    persistenceFailure.getClass().getSimpleName());
            events.accept(new AgentStreamEvent("error", Map.of("runId", run.getId(), "status", "RUNNING",
                    "errorCode", "RESULT_PENDING", "message", "结果暂未确认，请查询原运行状态")));
        }
    }

    private int boundedSeconds() {
        return Math.clamp(maxRunSeconds, 1, 120);
    }

    private void safeSend(Consumer<AgentStreamEvent> consumer, AgentStreamEvent event) {
        try {
            consumer.accept(event);
        } catch (Exception disconnected) {
            // 浏览器离开不影响已受理运行；最终回复仍写入数据库供恢复。
        }
    }

    private String failureCode(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof AgentRunException limit) {
                return limit.getCode();
            }
        }
        return "MODEL_UNAVAILABLE";
    }

    private String failureMessage(String code) {
        return switch (code) {
            case "RUN_TIMEOUT" -> "本次查询已超时，不会自动重发，请查看运行状态后重新提问";
            case "TOOL_LIMIT" -> "本次查询已达到工具调用上限，请缩小问题范围";
            case "REPLY_TOO_LARGE" -> "回复过长，本次运行已停止，请缩小问题范围";
            case "EMPTY_REPLY" -> "暂未收到有效回复，请稍后重新提问";
            case "KNOWLEDGE_UNAVAILABLE" -> "站内规则查询暂不可用，本次未生成答复，请稍后重试或使用业务页面核对";
            default -> "智能客服暂时不可用，请稍后重试";
        };
    }
}
