package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.pojo.AgentConversation;
import org.example.ticketmanagement.pojo.AgentMessage;
import org.example.ticketmanagement.pojo.AgentRun;
import org.example.ticketmanagement.pojo.AgentKnowledgeEntry;
import org.example.ticketmanagement.serviceimpl.AgentKnowledgeService;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.serviceimpl.AgentConversationService;
import org.example.ticketmanagement.serviceimpl.AgentConversationStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;

@RestController
@RequestMapping("/api/user/ai-customer-service")
@RequiredArgsConstructor
@SaCheckLogin
public class AgentConversationController {

    private final AgentConversationStore store;
    private final AgentConversationService service;
    private final AgentKnowledgeService knowledge;

    @GetMapping("/knowledge/{entryId}")
    public Result<AgentKnowledgeEntry> knowledge(@PathVariable String entryId,
            @RequestParam(required = false) Integer version) {
        return Result.success(knowledge.get(entryId, version));
    }

    @PostMapping("/conversations")
    public Result<AgentConversation> create(@Valid @RequestBody CreateConversation request) {
        return Result.success(store.create(userId(), request.title()));
    }

    @GetMapping("/conversations")
    public Result<PageResult<AgentConversation>> list(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return Result.success(store.list(userId(), page, size));
    }

    @PatchMapping("/conversations/{id}")
    public Result<Void> rename(@PathVariable String id, @Valid @RequestBody RenameConversation request) {
        store.rename(userId(), id, request.title());
        return Result.success("会话已重命名");
    }

    @GetMapping("/conversations/{id}")
    public Result<AgentConversation> conversation(@PathVariable String id) {
        return Result.success(store.get(userId(), id));
    }

    @DeleteMapping("/conversations/{id}")
    public Result<Void> delete(@PathVariable String id) {
        store.delete(userId(), id);
        return Result.success("会话已删除");
    }

    @DeleteMapping("/conversations/{id}/messages")
    public Result<Void> clear(@PathVariable String id) {
        store.clear(userId(), id);
        return Result.success("会话历史已清空");
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<PageResult<AgentMessage>> messages(@PathVariable String id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "50") int size) {
        return Result.success(store.messages(userId(), id, page, size));
    }

    @GetMapping("/runs/{runId}")
    public Result<AgentRun> run(@PathVariable String runId) {
        return Result.success(store.getRun(userId(), runId));
    }

    @PostMapping(value = "/conversations/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id, @Valid @RequestBody SendMessage request) {
        long currentUser = userId();
        SseEmitter emitter = new SseEmitter(130_000L);
        AtomicBoolean disconnected = new AtomicBoolean();
        emitter.onCompletion(() -> disconnected.set(true));
        emitter.onTimeout(() -> {
            disconnected.set(true);
            emitter.complete();
        });
        emitter.onError(failure -> disconnected.set(true));
        // 受理和归属校验仍在请求线程；订阅开始后所有身份由服务端上下文传递。
        service.stream(currentUser, id, request.clientMessageId(), request.message(), event -> {
            if (disconnected.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(event.name()).data(event.data(), MediaType.APPLICATION_JSON));
                if ("done".equals(event.name()) || "error".equals(event.name())
                        && event.data() instanceof Map<?, ?> data
                        && "RESULT_PENDING".equals(data.get("errorCode"))) {
                    emitter.complete();
                }
            } catch (Exception closed) {
                disconnected.set(true);
                emitter.complete();
            }
        });
        return emitter;
    }

    private long userId() {
        return StpUtil.getLoginIdAsLong();
    }

    public record CreateConversation(@Size(min = 1, max = 100) String title) {
    }

    public record RenameConversation(@NotBlank @Size(max = 100) String title) {
    }

    public record SendMessage(@NotBlank @Size(max = 2000) String message,
                              @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String clientMessageId) {
    }
}
