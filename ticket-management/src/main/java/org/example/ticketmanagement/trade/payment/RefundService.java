package org.example.ticketmanagement.trade.payment;

import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.mapper.TradeRefundMapper;
import org.example.ticketmanagement.pojo.PaymentRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final TradeRefundMapper mapper;
    private final PaymentGateway gateway;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Value("${trade.enabled:false}")
    private boolean enabled;

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /**
     * 在支付入账事务中创建退款请求，调用方已锁定支付流水。
     */
    public void request(PaymentRecord p) {
        // 退款单号独立生成，后续重试始终使用同一笔持久化请求。
        mapper.request("RF" + UUID.randomUUID().toString().replace("-", ""), p, "LATE_OR_DUPLICATE_PAYMENT", now());
    }

    public List<Map<String, Object>> forUser(long user) {
        return mapper.forUser(user);
    }

    public List<Map<String, Object>> forAdmin() {
        return mapper.forAdmin();
    }

    public boolean retry(String no) {
        requireEnabled();
        return mapper.retry(no, now()) == 1;
    }

    public int processDueRefunds() {
        if (!enabled) {
            return 0;
        }

        // 先处理重试耗尽的租约，再领取当前到期任务。
        mapper.exhaustExpiredLeases(now());
        var ids = mapper.findDueIds(now());
        int done = 0;
        for (long id : ids) {
            try {
                if (process(id)) {
                    done++;
                }
            } catch (RuntimeException ignored) {
                // 本次执行失败时保留租约，过期后由后续轮次恢复。
            }
        }
        return done;
    }

    private boolean process(long id) {
        String token = UUID.randomUUID().toString();

        // 1. 短事务领取任务，避免多个实例同时退款。
        Map<String, Object> r = transactions.execute(s -> {
            var claimedAt = now();
            int changed = mapper.claim(id, token, claimedAt.plusMinutes(2), claimedAt);
            return changed == 1 ? mapper.findById(id) : null;
        });
        if (r == null) {
            return false;
        }
        String payment = (String) r.get("payment_no");
        String no = (String) r.get("refund_no");
        BigDecimal amount = (BigDecimal) r.get("amount");

        // 2. 先查渠道退款结果，再决定是否发起退款请求。
        var result = gateway.queryRefund(payment, no, amount);
        if (result.state() != PaymentGateway.RefundState.SUCCESS) {
            result = gateway.refund(payment, no, amount);
        }
        var finalResult = result;

        // 3. 短事务保存渠道结果；成功时同步更新支付状态。
        transactions.executeWithoutResult(s -> {
            long paymentId = ((Number) r.get("payment_id")).longValue();
            mapper.lockPayment(paymentId);
            int attempts = ((Number) r.get("attempts")).intValue();
            String state = finalResult.state() == PaymentGateway.RefundState.SUCCESS ? "SUCCESS"
                    : attempts >= 10 ? "MANUAL_REQUIRED" : "UNKNOWN";
            int changed = mapper.complete(id, token, state, finalResult.tradeNo(), finalResult.detail(),
                    now().plusMinutes(Math.min(60, 1L << Math.min(6, attempts))), now());
            if (changed == 1 && "SUCCESS".equals(state)) {
                mapper.markPaymentRefunded(paymentId, now());
            }
        });

        return result.state() == PaymentGateway.RefundState.SUCCESS;
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("交易暂未启用");
        }
    }
}
