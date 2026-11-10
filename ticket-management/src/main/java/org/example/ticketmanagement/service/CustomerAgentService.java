package org.example.ticketmanagement.service;

public interface CustomerAgentService {

    /** 使用服务端身份执行只读客服查询。 */
    String chat(long userId, String message);

    /** 清除本人上下文，不调用模型。 */
    void clearHistory(long userId);
}
