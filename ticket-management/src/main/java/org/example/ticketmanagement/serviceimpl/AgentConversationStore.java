package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.dto.AgentRunStart;
import org.example.ticketmanagement.dto.AgentCitation;
import org.example.ticketmanagement.mapper.typehandler.AgentCitationTypeHandler;
import org.example.ticketmanagement.exception.AgentRequestException;
import org.example.ticketmanagement.mapper.AgentConversationMapper;
import org.example.ticketmanagement.pojo.AgentConversation;
import org.example.ticketmanagement.pojo.AgentMessage;
import org.example.ticketmanagement.pojo.AgentRun;
import org.example.ticketmanagement.pojo.PageResult;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 持久化会话与运行状态；事务始终先锁会话，再更新运行与消息。 */
@Service
@RequiredArgsConstructor
public class AgentConversationStore {

    private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "FAILED", "TIMED_OUT");
    private final AgentConversationMapper mapper;
    private final Clock clock;

    public AgentConversation create(long userId, String title) {
        requireUser(userId);
        AgentConversation conversation = newConversation(userId, title == null ? "新会话" : title);
        mapper.insertConversation(conversation);
        return conversation;
    }

    @Transactional
    public AgentConversation defaultConversation(long userId) {
        requireUser(userId);
        AgentConversation conversation = newConversation(userId, "默认会话");
        mapper.ensureDefault(conversation);
        return mapper.defaultConversation(userId);
    }

    public PageResult<AgentConversation> list(long userId, int page, int size) {
        requireUser(userId);
        requirePage(page, size, 50);
        return new PageResult<>(mapper.conversations(userId, size, (page - 1) * size),
                mapper.countConversations(userId), page, size);
    }

    @Transactional
    public AgentConversation get(long userId, String id) {
        // 切换会话时返回当前占用，不能只依赖浏览器中缓存的旧activeRunId。
        AgentConversation conversation = owned(userId, id, true);
        expireActive(conversation);
        return conversation;
    }

    @Transactional
    public void rename(long userId, String id, String title) {
        owned(userId, id, true);
        mapper.rename(id, userId, validTitle(title), now());
    }

    @Transactional
    public void delete(long userId, String id) {
        AgentConversation conversation = owned(userId, id, true);
        requireIdle(conversation);
        mapper.clearMessages(id);
        mapper.eraseRunContent(id);
        mapper.eraseActions(id, now());
        mapper.deleteConversation(id, now());
    }

    @Transactional
    public void clear(long userId, String id) {
        AgentConversation conversation = owned(userId, id, true);
        requireIdle(conversation);
        mapper.clearMessages(id);
        mapper.eraseRunContent(id);
        mapper.eraseActions(id, now());
        mapper.active(id, null, now());
    }

    public PageResult<AgentMessage> messages(long userId, String id, int page, int size) {
        requirePage(page, size, 100);
        owned(userId, id, false);
        List<AgentMessage> messages = new ArrayList<>(mapper.messages(id, size, (page - 1) * size));
        Collections.reverse(messages);
        return new PageResult<>(messages, mapper.countMessages(id), page, size);
    }

    public List<Message> context(long userId, String id) {
        owned(userId, id, false);
        List<AgentMessage> messages = new ArrayList<>(mapper.context(id));
        Collections.reverse(messages);
        return messages.stream().map(message -> (Message) ("user".equals(message.getRole())
                ? new UserMessage(message.getContent()) : new AssistantMessage(message.getContent()))).toList();
    }

    @Transactional
    public AgentRunStart start(long userId, String conversationId, String clientId, String message, int seconds) {
        if (clientId == null || !clientId.matches("[a-zA-Z0-9_-]{1,64}") || message == null
                || message.isBlank() || message.length() > 2000 || seconds < 1 || seconds > 120) {
            throw new AgentRequestException(400, "消息或运行参数无效");
        }

        // 归属校验和运行占用共用数据库锁，跨节点请求也不会重复受理。
        AgentConversation conversation = owned(userId, conversationId, true);
        expireActive(conversation);
        String hash = hash(message.trim());
        AgentRun existing = mapper.byClientId(conversationId, clientId);
        if (existing != null) {
            if (!hash.equals(existing.getInputHash())) {
                throw new AgentRequestException(409, "同一消息标识不能用于不同内容");
            }
            return new AgentRunStart(expose(existing), false);
        }
        if (conversation.getActiveRunId() != null) {
            throw new AgentRequestException(409, "当前会话仍在处理中，请先查询原运行结果");
        }

        // 运行、用户消息和会话占用同事务落库，再交给外部模型处理。
        LocalDateTime now = now();
        AgentRun run = new AgentRun();
        run.setId(UUID.randomUUID().toString());
        run.setConversationId(conversationId);
        run.setClientMessageId(clientId);
        run.setInputHash(hash);
        run.setStatus("RUNNING");
        run.setToolTrace("[]");
        run.setCreatedAt(now);
        run.setDeadlineAt(now.plusSeconds(seconds));
        mapper.insertRun(run);
        mapper.insertMessage(message(conversationId, run.getId(), "user", message.trim(), now));
        mapper.active(conversationId, run.getId(), now);
        return new AgentRunStart(expose(run), true);
    }

    @Transactional
    public AgentRun getRun(long userId, String runId) {
        AgentRun run = mapper.run(runId);
        if (run == null) {
            throw inaccessible();
        }
        AgentConversation conversation = owned(userId, run.getConversationId(), true);
        expireActive(conversation);
        return expose(mapper.lockRun(runId));
    }

    @Transactional
    public boolean complete(long userId, String runId, String status, String reply,
                            String errorCode, String errorMessage) {
        if (!TERMINAL.contains(status)) {
            throw new IllegalArgumentException("运行终态无效");
        }
        AgentRun run = mapper.run(runId);
        if (run == null) {
            return false;
        }
        AgentConversation conversation = owned(userId, run.getConversationId(), true);
        run = mapper.lockRun(runId);
        if (!"RUNNING".equals(run.getStatus()) || !runId.equals(conversation.getActiveRunId())) {
            return false;
        }

        // 截止时间是持久化屏障，旧线程即使迟到也不能提交成功结果。
        LocalDateTime now = now();
        boolean expired = !now.isBefore(run.getDeadlineAt());
        run.setStatus(expired ? "TIMED_OUT" : status);
        run.setReply(expired ? null : reply);
        run.setErrorCode(expired ? "RUN_TIMEOUT" : errorCode);
        run.setErrorMessage(expired ? "本次查询已超时，请查看原运行状态后再提问" : errorMessage);
        run.setCompletedAt(now);
        if (mapper.finish(run) != 1) {
            return false;
        }
        if ("SUCCEEDED".equals(run.getStatus())) {
            mapper.insertMessage(message(run.getConversationId(), runId, "assistant", reply, now));
            mapper.messageCitations(runId, AgentCitationTypeHandler.encode(run.getCitations()));
        }
        mapper.active(run.getConversationId(), null, now);
        return true;
    }

    public boolean recordTool(String runId, int count, String trace) {
        return mapper.toolTrace(runId, count, trace, now()) == 1;
    }

    public boolean recordCitations(String runId, List<AgentCitation> citations) {
        return mapper.citations(runId, AgentCitationTypeHandler.encode(citations), now()) == 1;
    }

    private AgentConversation owned(long userId, String id, boolean lock) {
        requireUser(userId);
        AgentConversation conversation = lock ? mapper.lockConversation(id, userId) : mapper.conversation(id, userId);
        if (conversation == null) {
            throw inaccessible();
        }
        return conversation;
    }

    private void requireIdle(AgentConversation conversation) {
        expireActive(conversation);
        if (conversation.getActiveRunId() != null) {
            throw new AgentRequestException(409, "会话仍在运行，请待查询结束后操作");
        }
    }

    private void expireActive(AgentConversation conversation) {
        if (conversation.getActiveRunId() == null) {
            return;
        }
        AgentRun active = mapper.lockRun(conversation.getActiveRunId());
        if (active != null && "RUNNING".equals(active.getStatus()) && !now().isBefore(active.getDeadlineAt())) {
            active.setStatus("TIMED_OUT");
            active.setErrorCode("RUN_TIMEOUT");
            active.setErrorMessage("本次查询已超时，不会自动重发模型请求");
            active.setCompletedAt(now());
            mapper.finish(active);
        }
        if (active == null || !"RUNNING".equals(active.getStatus())) {
            mapper.active(conversation.getId(), null, now());
            conversation.setActiveRunId(null);
        }
    }

    private AgentConversation newConversation(long userId, String title) {
        AgentConversation conversation = new AgentConversation();
        conversation.setId(UUID.randomUUID().toString());
        conversation.setUserId(userId);
        conversation.setTitle(validTitle(title));
        conversation.setCreatedAt(now());
        conversation.setUpdatedAt(conversation.getCreatedAt());
        return conversation;
    }

    private AgentMessage message(String conversationId, String runId, String role, String content, LocalDateTime now) {
        AgentMessage message = new AgentMessage();
        message.setConversationId(conversationId);
        message.setRunId(runId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(now);
        return message;
    }

    private String validTitle(String title) {
        if (title == null || title.isBlank() || title.length() > 100) {
            throw new AgentRequestException(400, "会话名称须为1至100字");
        }
        return title.trim();
    }

    private void requirePage(int page, int size, int maxSize) {
        if (page < 1 || page > 10000 || size < 1 || size > maxSize) {
            throw new AgentRequestException(400, "分页参数无效");
        }
    }

    private void requireUser(long userId) {
        if (userId <= 0) {
            throw new AgentRequestException(401, "请先登录");
        }
    }

    private AgentRequestException inaccessible() {
        return new AgentRequestException(404, "会话或运行不存在或不可访问");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private AgentRun expose(AgentRun run) {
        // 前端使用绝对截止时间，避免浏览器与服务端时区不同时提前结束恢复轮询。
        run.setDeadlineEpochMillis(run.getDeadlineAt().atZone(clock.getZone()).toInstant().toEpochMilli());
        return run;
    }

    private String hash(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("当前环境不支持消息摘要", impossible);
        }
    }
}
