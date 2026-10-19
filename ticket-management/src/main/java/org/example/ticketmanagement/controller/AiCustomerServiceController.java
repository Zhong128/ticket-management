package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.serviceimpl.CustomerServiceTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/user/ai-customer-service")
@RequiredArgsConstructor
@Tag(name = "AI智能客服", description = "智能客服对话接口")
@SaCheckLogin
public class AiCustomerServiceController {

    private final ChatClient chatClient;
    private final CustomerServiceTools customerServiceTools;

    /**
     * 发送消息给AI客服
     */
    @Operation(summary = "发送消息", tags = {"AI智能客服"})
    @PostMapping("/chat")
    public Result<String> chat(@RequestBody ChatRequest request) {
        log.info("用户咨询AI客服: userId={}, message={}",
                StpUtil.getLoginIdAsLong(), request.message);

        try {
            String response = chatClient.prompt()
                    .user(request.message)
                    .tools(customerServiceTools)
                    .call()
                    .content();

            log.info("AI客服回复成功");
            return Result.success(response);
        } catch (Exception e) {
            log.error("AI客服对话失败", e);
            return Result.error("智能客服暂时不可用，请稍后重试");
        }
    }

    /**
     * 清空对话历史
     */
    @Operation(summary = "清空对话历史", tags = {"AI智能客服"})
    @DeleteMapping("/clear-history")
    public Result<Void> clearHistory() {
        log.info("用户清空对话历史");

        try {
            long userId = StpUtil.getLoginIdAsLong();
            String conversationId = "user_" + userId;

            chatClient.prompt()
                    .system("")
                    .user("clear")
                    .advisors(advisorSpec -> advisorSpec.param(
                            "chat_memory_conversation_id",
                            conversationId
                    ))
                    .call();

            return Result.success("对话历史已清空");
        } catch (Exception e) {
            log.error("清空对话历史失败", e);
            return Result.error("操作失败");
        }
    }

    public static class ChatRequest {
        public String message;

        public ChatRequest() {}

        public ChatRequest(String message) {
            this.message = message;
        }
    }
}
