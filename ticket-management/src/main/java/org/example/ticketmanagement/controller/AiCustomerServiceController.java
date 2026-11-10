package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.service.CustomerAgentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@Slf4j
@RestController
@RequestMapping("/api/user/ai-customer-service")
@RequiredArgsConstructor
@Tag(name = "AI智能客服", description = "智能客服对话接口")
@SaCheckLogin
public class AiCustomerServiceController {

    private final CustomerAgentService customerAgentService;

    @Value("${customer-service.enabled:true}")
    private boolean enabled = true;

    @Operation(summary = "发送消息", tags = {"AI智能客服"})
    @PostMapping("/chat")
    public Result<String> chat(@Valid @RequestBody ChatRequest request) {
        if (!enabled) {
            return Result.error(503, "智能客服功能维护中，请稍后再试");
        }

        long userId = StpUtil.getLoginIdAsLong();
        log.info("用户咨询AI客服: userId={}, messageLength={}", userId, request.message.length());

        try {
            String response = customerAgentService.chat(userId, request.message);
            return Result.success("回复成功", response);
        } catch (Exception e) {
            log.warn("AI客服对话失败: userId={}, exception={}", userId, e.getClass().getSimpleName());
            return Result.error("智能客服暂时不可用，请稍后重试");
        }
    }

    @Operation(summary = "清空对话历史", tags = {"AI智能客服"})
    @DeleteMapping("/clear-history")
    public Result<Void> clearHistory() {
        long userId = StpUtil.getLoginIdAsLong();
        try {
            // 直接清除当前用户的记忆，不向模型发送伪清空消息或产生外部调用。
            customerAgentService.clearHistory(userId);
            return Result.success("对话历史已清空");
        } catch (Exception e) {
            log.error("清空对话历史失败: userId={}", userId, e);
            return Result.error("操作失败");
        }
    }

    public static class ChatRequest {
        @NotBlank(message = "咨询内容不能为空")
        @Size(max = 2000, message = "咨询内容不能超过2000字")
        public String message;

        public ChatRequest() {
        }

        public ChatRequest(String message) {
            this.message = message;
        }
    }
}
