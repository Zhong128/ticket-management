package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.exception.AgentRequestException;
import org.example.ticketmanagement.mapper.AgentActionMapper;
import org.example.ticketmanagement.mapper.AgentConversationMapper;
import org.example.ticketmanagement.pojo.AgentAction;
import org.example.ticketmanagement.pojo.AgentConversation;
import org.example.ticketmanagement.pojo.AgentRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 短事务状态机；持锁期间不调用模型、Redis或交易Service。 */
@Service
@RequiredArgsConstructor
public class AgentActionStore {

    private static final Set<String> UNRESOLVED = Set.of("EXECUTING", "UNKNOWN");
    private final AgentActionMapper actions;
    private final AgentConversationMapper conversations;
    private final Clock clock;

    @Transactional
    public AgentAction create(long userId, String runId, AgentAction draft) {
        AgentRun initial = conversations.run(runId);
        if (initial == null) {
            throw inaccessible();
        }
        AgentConversation conversation = ownedConversation(userId, initial.getConversationId());
        AgentRun run = conversations.lockRun(runId);
        if (run == null || !"RUNNING".equals(run.getStatus()) || !now().isBefore(run.getDeadlineAt())
                || !runId.equals(conversation.getActiveRunId())) {
            throw new AgentRequestException(409, "来源运行已结束，不能再生成操作草稿");
        }

        // 同目标全局串行化；原结果未知时不能换会话或换幂等键重新申请。
        actions.ensureGuard(userId, draft.getType(), draft.getTargetId());
        String previousId = actions.lockGuard(userId, draft.getType(), draft.getTargetId());
        AgentAction previous = previousId == null ? null : actions.lock(previousId);
        if (previous != null && UNRESOLVED.contains(previous.getStatus())) {
            throw new AgentRequestException(409, "该目标已有未确认结果，请先查询原申请或订单");
        }
        if (previous != null && "PENDING".equals(previous.getStatus())) {
            if (!previous.isContentErased() && runId.equals(previous.getRunId())
                    && previous.getQuantity() == draft.getQuantity()
                    && previous.getUnitPrice().compareTo(draft.getUnitPrice()) == 0
                    && now().isBefore(previous.getExpiresAt())) {
                return previous;
            }
            actions.pendingState(previous.getId(), "EXPIRED", "已由新卡片替代", now());
        }

        // 每个运行仅保留一个待确认操作，参数变更必须生成新卡片。
        actions.supersede(runId, now());
        draft.setId(UUID.randomUUID().toString());
        draft.setUserId(userId);
        draft.setConversationId(conversation.getId());
        draft.setRunId(runId);
        draft.setRequestKey("agent-" + UUID.randomUUID());
        draft.setStatus("PENDING");
        draft.setMessage("请核对内容后点击确认；尚未执行交易");
        draft.setCreatedAt(now());
        draft.setUpdatedAt(now());
        draft.setExpiresAt(now().plusMinutes(5));
        actions.insert(draft);
        actions.bindGuard(draft);
        return draft;
    }

    @Transactional
    public AgentAction get(long userId, String id) {
        Locked locked = locked(userId, id);
        expire(locked.action(), locked.run());
        return actions.lock(id);
    }

    public List<AgentAction> list(long userId, String conversationId) {
        if (userId <= 0 || conversations.conversation(conversationId, userId) == null) {
            throw inaccessible();
        }
        return actions.list(conversationId, userId);
    }

    @Transactional
    public Claim claim(long userId, String id) {
        Locked locked = locked(userId, id);
        AgentAction action = locked.action();
        expire(action, locked.run());
        action = actions.lock(id);
        if (!"PENDING".equals(action.getStatus())) {
            return new Claim(action, false);
        }
        if (!"SUCCEEDED".equals(locked.run().getStatus())) {
            throw new AgentRequestException(409, "请等待来源回复成功结束后再确认");
        }
        if (!id.equals(locked.guardId())) {
            actions.pendingState(id, "EXPIRED", "已有新卡片，请重新核对", now());
            return new Claim(actions.lock(id), false);
        }

        // 唯一执行权先落库，事务提交后才调用交易Service。
        boolean execute = actions.pendingState(id, "EXECUTING", "已确认，正在核验业务结果", now()) == 1;
        return new Claim(actions.lock(id), execute);
    }

    @Transactional
    public AgentAction dismiss(long userId, String id) {
        Locked locked = locked(userId, id);
        expire(locked.action(), locked.run());
        actions.pendingState(id, "DISMISSED", "已放弃，未执行交易", now());
        return actions.lock(id);
    }

    public void result(AgentAction original, String status, String requestStatus, Long orderId, String message) {
        // 执行和查询恢复共享CAS；迟到线程不能覆盖较新结果，也不恢复擦除快照。
        actions.result(original.getId(), original.getVersion(), status, requestStatus, orderId, message, now());
    }

    public AgentAction unresolved(long userId, String type, long targetId) {
        return userId <= 0 ? null : actions.unresolved(userId, type, targetId);
    }

    private Locked locked(long userId, String id) {
        if (id == null || !id.matches("[a-fA-F0-9-]{36}")) {
            throw inaccessible();
        }
        AgentAction initial = actions.find(id);
        if (userId <= 0 || initial == null || initial.getUserId() != userId || initial.isContentErased()) {
            throw inaccessible();
        }

        // 延续会话→运行锁序，再锁目标与卡片，和清空及运行完成保持一致。
        ownedConversation(userId, initial.getConversationId());
        AgentRun run = conversations.lockRun(initial.getRunId());
        actions.ensureGuard(userId, initial.getType(), initial.getTargetId());
        String guardId = actions.lockGuard(userId, initial.getType(), initial.getTargetId());
        AgentAction action = actions.lock(id);
        if (action == null || action.isContentErased() || run == null) {
            throw inaccessible();
        }
        return new Locked(action, run, guardId);
    }

    private void expire(AgentAction action, AgentRun run) {
        boolean invalidRun = !"SUCCEEDED".equals(run.getStatus())
                && (!"RUNNING".equals(run.getStatus()) || !now().isBefore(run.getDeadlineAt()));
        if ("PENDING".equals(action.getStatus()) && (invalidRun || !now().isBefore(action.getExpiresAt()))) {
            actions.pendingState(action.getId(), "EXPIRED", "卡片或来源已失效，请重新核对", now());
        }
    }

    private AgentConversation ownedConversation(long userId, String id) {
        AgentConversation conversation = userId <= 0 ? null : conversations.lockConversation(id, userId);
        if (conversation == null) {
            throw inaccessible();
        }
        return conversation;
    }

    private AgentRequestException inaccessible() {
        return new AgentRequestException(404, "操作卡片或会话不存在或不可访问");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public record Claim(AgentAction action, boolean execute) {
    }

    private record Locked(AgentAction action, AgentRun run, String guardId) {
    }
}
