package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.service.CustomerAgentService;
import org.springframework.stereotype.Service;

/** 兼容旧聊天接口，使用本人默认持久会话及相同的运行预算。 */
@Service
@RequiredArgsConstructor
public class CustomerAgentServiceImpl implements CustomerAgentService {

    private final AgentConversationService conversations;
    private final AgentConversationStore store;

    @Override
    public String chat(long userId, String message) {
        return conversations.legacyChat(userId, message);
    }

    @Override
    public void clearHistory(long userId) {
        store.clear(userId, store.defaultConversation(userId).getId());
    }
}
