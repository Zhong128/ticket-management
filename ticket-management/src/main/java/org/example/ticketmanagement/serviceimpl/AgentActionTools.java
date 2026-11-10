package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.dto.AgentActionView;
import org.example.ticketmanagement.dto.AgentToolResult;
import org.example.ticketmanagement.exception.AgentRequestException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/** 模型仅能提草稿，工具中不存在确认、支付或取消执行方法。 */
@Service
@RequiredArgsConstructor
public class AgentActionTools {

    private final AgentActionService service;

    @Tool(description = "仅为用户明确选择的票档和数量生成购票确认草稿，不预占库存或下单；必须让用户点击服务端卡片确认")
    public AgentToolResult<AgentActionView> proposePurchase(@ToolParam(description = "已明确选择的票档ID") Long tierId,
            @ToolParam(description = "已明确选择的数量，1至5张") Integer quantity, ToolContext context) {
        return draft(() -> {
            Identity identity = identity(context);
            if (tierId == null || quantity == null) {
                throw new AgentRequestException(400, "请先明确票档和数量");
            }
            return service.proposePurchase(identity.userId(), identity.runId(), tierId, quantity);
        });
    }

    @Tool(description = "仅为用户明确选择的本人待支付订单生成取消确认草稿，不执行取消；必须让用户点击服务端卡片确认")
    public AgentToolResult<AgentActionView> proposeCancellation(@ToolParam(description = "已明确选择的本人订单ID") Long orderId,
            ToolContext context) {
        return draft(() -> {
            Identity identity = identity(context);
            if (orderId == null) {
                throw new AgentRequestException(400, "请先明确需要取消的订单");
            }
            return service.proposeCancellation(identity.userId(), identity.runId(), orderId);
        });
    }

    private Identity identity(ToolContext context) {
        Object user = context == null ? null : context.getContext().get("userId");
        Object run = context == null ? null : context.getContext().get("runId");
        if (!(user instanceof Number number) || number.longValue() <= 0 || !(run instanceof String id) || id.isBlank()) {
            throw new AgentRequestException(401, "缺少服务端身份或运行信息，不能生成草稿");
        }
        return new Identity(number.longValue(), id);
    }

    private AgentToolResult<AgentActionView> draft(Supplier<AgentActionView> operation) {
        try {
            return AgentToolResult.success(operation.get());
        } catch (AgentRequestException rejected) {
            return AgentToolResult.rejected("DRAFT_REJECTED", rejected.getMessage());
        } catch (IllegalArgumentException rejected) {
            return AgentToolResult.rejected("DRAFT_REJECTED", "目标不存在或不可操作，请重新查询本人订单或票档");
        } catch (RuntimeException unavailable) {
            return AgentToolResult.error();
        }
    }

    private record Identity(long userId, String runId) {
    }
}
