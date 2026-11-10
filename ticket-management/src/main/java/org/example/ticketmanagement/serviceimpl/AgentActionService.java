package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.constant.OrderConstant;
import org.example.ticketmanagement.dto.AgentActionView;
import org.example.ticketmanagement.exception.AgentRequestException;
import org.example.ticketmanagement.exception.ConfirmedPriceChangedException;
import org.example.ticketmanagement.exception.ReservationRejectedException;
import org.example.ticketmanagement.mapper.EventMapper;
import org.example.ticketmanagement.mapper.EventSessionMapper;
import org.example.ticketmanagement.mapper.TicketTierMapper;
import org.example.ticketmanagement.pojo.AgentAction;
import org.example.ticketmanagement.pojo.Event;
import org.example.ticketmanagement.pojo.EventSession;
import org.example.ticketmanagement.pojo.TicketTier;
import org.example.ticketmanagement.service.OrderService;
import org.example.ticketmanagement.vo.OrderInfoVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 草稿无交易副作用；用户确认才取得执行权，外部业务调用不持有卡片事务。 */
@Service
@RequiredArgsConstructor
public class AgentActionService {

    private static final Set<String> UNRESOLVED = Set.of("EXECUTING", "UNKNOWN");
    private final AgentActionStore store;
    private final TicketTierMapper tiers;
    private final EventSessionMapper sessions;
    private final EventMapper events;
    private final SeckillServiceImpl seckill;
    private final ReservationServiceImpl reservations;
    private final OrderService orders;
    private final Clock clock;

    @Value("${customer-service.enabled:true}")
    private boolean customerServiceEnabled;
    @Value("${trade.enabled:false}")
    private boolean tradeEnabled;

    public AgentActionView proposePurchase(long userId, String runId, long tierId, int quantity) {
        requireEnabled();
        if (userId <= 0 || tierId <= 0 || quantity < 1 || quantity > 5) {
            throw new AgentRequestException(400, "票档或数量无效，购买数量须为1至5张");
        }
        reconcilePrevious(userId, "PURCHASE", tierId);
        var qualification = seckill.getSeckillQualification(userId, tierId, quantity);
        if (!qualification.qualified()) {
            throw new AgentRequestException(409, qualification.message());
        }
        AgentAction draft = purchaseSnapshot(tierId, quantity);
        return view(store.create(userId, runId, draft));
    }

    public AgentActionView proposeCancellation(long userId, String runId, long orderId) {
        requireEnabled();
        if (userId <= 0 || orderId <= 0) {
            throw new AgentRequestException(400, "订单参数无效");
        }
        reconcilePrevious(userId, "CANCEL", orderId);
        OrderInfoVO order = orders.getOrderDetail(orderId, userId);
        if (!Integer.valueOf(OrderConstant.ORDER_STATUS_PENDING).equals(order.getOrderStatus())) {
            throw new AgentRequestException(409, "仅待支付订单可生成取消确认卡片");
        }
        AgentAction draft = new AgentAction();
        draft.setType("CANCEL");
        draft.setTargetId(orderId);
        draft.setTitle("确认取消未支付订单");
        draft.setEventName(order.getEventName());
        draft.setSessionName(order.getSessionName());
        draft.setSessionTime(order.getSessionTime());
        draft.setTierName(order.getTierName());
        draft.setQuantity(order.getQuantity());
        draft.setTotalAmount(order.getTotalAmount());
        draft.setUnitPrice(order.getTotalAmount().divide(BigDecimal.valueOf(order.getQuantity()), 2, RoundingMode.HALF_UP));
        return view(store.create(userId, runId, draft));
    }

    public List<AgentActionView> list(long userId, String conversationId) {
        return store.list(userId, conversationId).stream().map(action -> get(userId, action.getId())).toList();
    }

    public AgentActionView get(long userId, String id) {
        AgentAction action = store.get(userId, id);
        if (UNRESOLVED.contains(action.getStatus())) {
            reconcile(action);
            action = store.get(userId, id);
        }
        return view(action);
    }

    public AgentActionView dismiss(long userId, String id) {
        return view(store.dismiss(userId, id));
    }

    public AgentActionView confirm(long userId, String id) {
        requireEnabled();
        AgentActionStore.Claim claim = store.claim(userId, id);
        AgentAction action = claim.action();
        if (!claim.execute()) {
            return get(userId, id);
        }

        // 确认快照重新核对；参数取服务端卡片，不接受HTTP覆盖。
        if ("PURCHASE".equals(action.getType())) {
            executePurchase(action);
        } else {
            executeCancellation(action);
        }
        return get(userId, id);
    }

    private void executePurchase(AgentAction action) {
        try {
            AgentAction latest = purchaseSnapshot(action.getTargetId(), action.getQuantity());
            if (!samePurchase(action, latest)) {
                store.result(action, "REJECTED", null, null, "价格或演出信息已变化，请生成新卡片重新确认");
                return;
            }
            var qualification = seckill.getSeckillQualification(action.getUserId(), action.getTargetId(), action.getQuantity());
            if (!qualification.qualified()) {
                store.result(action, "REJECTED", null, null, "当前不满足购买条件，请重新查询");
                return;
            }
        } catch (AgentRequestException unavailable) {
            store.result(action, "REJECTED", null, null, "当前票档或场次不可购买，请重新选择");
            return;
        } catch (RuntimeException unavailable) {
            // 尚未调用受理接口，确定无交易发生，可以安全结束本卡片。
            store.result(action, "REJECTED", null, null, "核验暂不可用，未发起购票申请");
            return;
        }

        try {
            Map<String, Object> result = seckill.reserveConfirmedApplication(action.getUserId(), action.getTargetId(),
                    action.getQuantity(), action.getRequestKey(), action.getUnitPrice());
            applyPurchaseResult(action, result);
        } catch (ConfirmedPriceChangedException changed) {
            store.result(action, "REJECTED", "REJECTED", null, "受理时价格已变化，未提交申请，请重新确认");
        } catch (ReservationRejectedException rejected) {
            store.result(action, "REJECTED", "REJECTED", null, "当前库存或购买条件已变化，未受理申请，请重新查询");
        } catch (RuntimeException uncertain) {
            store.result(action, "UNKNOWN", "UNKNOWN", null, "申请结果暂未确认，请查询原申请，不要重复下单");
        }
    }

    private void executeCancellation(AgentAction action) {
        try {
            OrderInfoVO order = orders.getOrderDetail(action.getTargetId(), action.getUserId());
            if (!Integer.valueOf(OrderConstant.ORDER_STATUS_PENDING).equals(order.getOrderStatus())) {
                applyCancellationResult(action, order);
                return;
            }
        } catch (RuntimeException unavailable) {
            store.result(action, "REJECTED", null, null, "订单核验暂不可用，未发起取消");
            return;
        }
        try {
            // true/false都不能代替实时终态，原服务可能返回取消处理中。
            orders.userCancelOrder(action.getTargetId(), action.getUserId());
            applyCancellationResult(action, orders.getOrderDetail(action.getTargetId(), action.getUserId()));
        } catch (RuntimeException uncertain) {
            store.result(action, "UNKNOWN", "UNKNOWN", action.getTargetId(), "取消结果暂未确认，请查询原订单");
        }
    }

    private void reconcile(AgentAction action) {
        try {
            if ("PURCHASE".equals(action.getType())) {
                applyPurchaseResult(action, reservations.requestByKey(action.getUserId(), action.getRequestKey()));
            } else {
                applyCancellationResult(action, orders.getOrderDetail(action.getTargetId(), action.getUserId()));
            }
        } catch (RuntimeException unavailable) {
            // 只读恢复失败仍保留原结果，不能据此推断交易失败或重新执行。
            if ("EXECUTING".equals(action.getStatus()) && executionLeaseExpired(action)) {
                store.result(action, "UNKNOWN", "UNKNOWN", action.getOrderId(), "结果查询暂不可用，请稍后查询原申请或订单");
            }
        }
    }

    private void reconcilePrevious(long userId, String type, long targetId) {
        // 即使来源会话已清空/删除，仍核对最小记录，既不回显旧内容也不永久占用目标。
        AgentAction previous = store.unresolved(userId, type, targetId);
        if (previous != null) {
            reconcile(previous);
        }
    }

    private void applyPurchaseResult(AgentAction action, Map<String, Object> result) {
        String state = Objects.toString(result.get("requestStatus"), "UNKNOWN");
        Long orderId = result.get("orderId") instanceof Number number ? number.longValue() : null;
        if (orderId != null && !"RELEASED".equals(state)) {
            store.result(action, "SUCCEEDED", state, orderId, "已生成订单，请前往订单页核对并付款");
        } else if (Set.of("RELEASED", "REJECTED").contains(state)) {
            store.result(action, "REJECTED", state, orderId, "原申请已终止，请查看申请或订单状态");
        } else if (!"UNKNOWN".equals(state)) {
            store.result(action, "EXECUTING", state, null, "申请已受理，正在等待落单，请查询结果");
        } else if (!"EXECUTING".equals(action.getStatus()) || executionLeaseExpired(action)) {
            store.result(action, "UNKNOWN", state, null, "尚不能确认原申请结果，请勿重复下单");
        }
    }

    private void applyCancellationResult(AgentAction action, OrderInfoVO order) {
        Integer state = order.getOrderStatus();
        if (Integer.valueOf(OrderConstant.ORDER_STATUS_CANCELED).equals(state)) {
            store.result(action, "SUCCEEDED", "CANCELED", action.getTargetId(), "订单已取消，未支付取消不会产生退款");
        } else if (Integer.valueOf(OrderConstant.ORDER_STATUS_CANCELING).equals(state)) {
            store.result(action, "EXECUTING", "CANCELING", action.getTargetId(), "取消确认中，请查询原订单结果");
        } else if (Integer.valueOf(OrderConstant.ORDER_STATUS_PAID).equals(state)
                || Integer.valueOf(OrderConstant.ORDER_STATUS_COMPLETED).equals(state)) {
            store.result(action, "REJECTED", "NOT_CANCELABLE", action.getTargetId(), "订单已支付或完成，不能普通取消");
        } else if (executionLeaseExpired(action) || "UNKNOWN".equals(action.getStatus())) {
            store.result(action, "UNKNOWN", "UNKNOWN", action.getTargetId(), "尚不能确认取消结果，请查询原订单");
        }
    }

    private AgentAction purchaseSnapshot(long tierId, int quantity) {
        TicketTier tier = tiers.selectById(tierId);
        if (tier == null || !Integer.valueOf(1).equals(tier.getStatus()) || tier.getCurrentPrice() == null
                || tier.getCurrentPrice().signum() <= 0) {
            throw new AgentRequestException(409, "票档不可购买");
        }
        EventSession session = sessions.selectById(tier.getSessionId());
        Event event = events.selectById(tier.getEventId());
        if (session == null || event == null || !Integer.valueOf(1).equals(session.getStatus())
                || !Integer.valueOf(1).equals(event.getStatus()) || !event.getId().equals(session.getEventId())) {
            throw new AgentRequestException(409, "演出或场次不可购买");
        }
        AgentAction action = new AgentAction();
        action.setType("PURCHASE");
        action.setTargetId(tierId);
        action.setTitle("确认提交购票申请");
        action.setEventName(event.getName());
        action.setSessionName(session.getSessionName());
        action.setSessionTime(session.getSessionTime());
        action.setTierName(tier.getTierName());
        action.setQuantity(quantity);
        action.setUnitPrice(tier.getCurrentPrice());
        action.setTotalAmount(tier.getCurrentPrice().multiply(BigDecimal.valueOf(quantity)));
        return action;
    }

    private boolean samePurchase(AgentAction expected, AgentAction actual) {
        return expected.getUnitPrice().compareTo(actual.getUnitPrice()) == 0
                && Objects.equals(expected.getEventName(), actual.getEventName())
                && Objects.equals(expected.getSessionName(), actual.getSessionName())
                && Objects.equals(expected.getSessionTime(), actual.getSessionTime())
                && Objects.equals(expected.getTierName(), actual.getTierName());
    }

    private boolean executionLeaseExpired(AgentAction action) {
        return !LocalDateTime.now(clock).isBefore(action.getUpdatedAt().plusSeconds(60));
    }

    private AgentActionView view(AgentAction action) {
        return new AgentActionView(action.getId(), action.getConversationId(), action.getRunId(), action.getType(),
                action.getStatus(), Long.toString(action.getTargetId()), Objects.toString(action.getTitle(), "交易确认"),
                Objects.toString(action.getEventName(), "演出"), Objects.toString(action.getSessionName(), "场次"),
                action.getSessionTime(), Objects.toString(action.getTierName(), "票档"), action.getQuantity(),
                action.getUnitPrice(), action.getTotalAmount(), publicOrderId(action),
                action.getRequestStatus(), action.getMessage(), action.getExpiresAt(),
                action.getExpiresAt().atZone(clock.getZone()).toInstant().toEpochMilli());
    }

    private String publicOrderId(AgentAction action) {
        if (action.getOrderId() != null) {
            return action.getOrderId().toString();
        }
        return "CANCEL".equals(action.getType()) ? Long.toString(action.getTargetId()) : null;
    }

    private void requireEnabled() {
        if (!customerServiceEnabled || !tradeEnabled) {
            throw new AgentRequestException(503, "客服交易操作维护中，请使用现有页面查看状态");
        }
    }
}
