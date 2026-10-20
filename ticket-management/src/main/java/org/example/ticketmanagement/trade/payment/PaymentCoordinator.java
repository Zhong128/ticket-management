package org.example.ticketmanagement.trade.payment;

import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.dto.CreatePaymentDTO;
import org.example.ticketmanagement.pojo.PaymentRecord;
import org.example.ticketmanagement.trade.core.TradeOperations;
import org.example.ticketmanagement.trade.messaging.OutboxStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentCoordinator implements TradePaymentPort {

    private final PaymentRepository repository;
    private final PaymentGateway gateway;
    private final TradeOperations trade;
    private final OutboxStore outbox;
    private final RefundService refunds;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Value("${trade.enabled:false}")
    private boolean enabled;

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("交易暂未启用");
        }
    }

    public record Created(PaymentRecord payment, String page) {
    }

    public Created create(long user, CreatePaymentDTO dto) {
        requireEnabled();

        // 1. 确定要支付的订单，未指定时只接受唯一待支付订单。
        if (!Integer.valueOf(1).equals(dto.getPayType())) {
            throw new IllegalArgumentException("仅支持支付宝支付");
        }
        Long order = dto.getOrderId();
        if (order == null) {
            var candidates = repository.payableOrders(user, now());
            if (candidates.size() != 1) {
                throw new IllegalArgumentException("请指定需要支付的订单ID");
            }
            order = candidates.getFirst();
        }
        final long orderId = order;

        // 2. 锁订单并检查归属、状态和支付截止时间。
        var payment = transactions.execute(status -> {
            var o = repository.lockOrder(orderId);
            if (((Number) o.get("user_id")).longValue() != user) {
                throw new IllegalArgumentException("无权操作此订单");
            }
            if (((Number) o.get("order_status")).intValue() != 0) {
                throw new IllegalArgumentException("订单不是待支付状态");
            }
            Object deadline = o.get("expire_time");
            LocalDateTime expires = deadline instanceof Timestamp t ? t.toLocalDateTime() : (LocalDateTime) deadline;
            if (expires == null || !now().isBefore(expires)) {
                throw new IllegalArgumentException("订单已过期");
            }
            // 3. 同一订单复用未结束的支付流水，避免重复创建。
            var active = repository.paymentsForOrder(orderId).stream()
                    .filter(p -> p.getPayStatus() == 0 || p.getPayStatus() == 1).findFirst();
            if (active.isPresent()) {
                return active.get();
            }

            // 4. 新建支付流水；事务提交后再调用渠道生成支付页面。
            var p = new PaymentRecord();
            p.setPaymentNo("PAY" + UUID.randomUUID().toString().replace("-", ""));
            p.setOrderId(orderId);
            p.setOrderNo((String) o.get("order_no"));
            p.setUserId(user);
            p.setTotalAmount((BigDecimal) o.get("total_amount"));
            p.setPayStatus(0);
            p.setPayType(1);
            p.setSubject("票务订单 " + p.getOrderNo());
            p.setExpireTime(expires);
            p.setCreateTime(now());
            p.setUpdateTime(now());
            repository.insert(p);
            return p;
        });

        return new Created(payment, gateway.create(payment));
    }

    /**
     * 支付回调、主动查询和关单时发现支付成功，最终都从这里入账。
     */
    public boolean settle(String no, String tradeNo, BigDecimal amount) {
        requireEnabled();

        // 1. 核对渠道传回的流水号和金额。
        if (tradeNo == null || tradeNo.isBlank() || amount == null) {
            throw new IllegalArgumentException("渠道付款数据缺失");
        }
        var initial = repository.find(no);
        if (initial == null) {
            throw new IllegalArgumentException("支付流水不存在");
        }

        // 2. 按订单、支付流水的固定顺序加锁。
        return Boolean.TRUE.equals(transactions.execute(s -> {
            repository.lockOrder(initial.getOrderId());
            var p = repository.lock(no);
            if (p.getTotalAmount().compareTo(amount) != 0
                    || (p.getTradeNo() != null && !p.getTradeNo().equals(tradeNo))) {
                throw new IllegalArgumentException("支付渠道绑定或金额不匹配");
            }
            repository.paid(p.getId(), tradeNo, now());
            p.setTradeNo(tradeNo);

            // 3. 订单接收本次支付；迟到或重复付款则生成退款任务。
            boolean applied = trade.applyPaid(p.getOrderId(), p.getId());
            if (applied) {
                outbox.enqueue("PAYMENT_SUCCESS", "PAYMENT_SUCCESS:" + no, Map.of("paymentNo", no));
            } else {
                refunds.request(p);
            }
            return applied;
        }));
    }

    public PaymentRecord owned(String no, long user) {
        var p = repository.find(no);
        if (p == null || p.getUserId() != user) {
            throw new IllegalArgumentException("无权查看此支付记录");
        }
        return p;
    }

    public void validateNotification(String no, String tradeNo, BigDecimal amount) {
        requireEnabled();
        var p = repository.find(no);
        if (p == null || tradeNo == null || tradeNo.isBlank() || amount == null
                || p.getTotalAmount().compareTo(amount) != 0
                || (p.getTradeNo() != null && !p.getTradeNo().equals(tradeNo))) {
            throw new IllegalArgumentException("支付回调绑定或金额不匹配");
        }
    }

    public void synchronize(String no) {
        requireEnabled();
        var p = repository.find(no);
        if (p == null) {
            return;
        }

        // 先在事务外查询支付渠道，再根据结果更新本地状态。
        var q = gateway.query(no);
        if (q.state() == PaymentGateway.State.CLOSED) {
            transactions.executeWithoutResult(s -> repository.close(p.getId(), now()));
        } else {
            applyQuery(p, q);
        }
    }

    private boolean applyQuery(PaymentRecord p, PaymentGateway.Query query) {
        if (query.state() != PaymentGateway.State.PAID) {
            return false;
        }
        if (!p.getPaymentNo().equals(query.paymentNo())) {
            throw new IllegalArgumentException("渠道支付流水不匹配");
        }
        return settle(p.getPaymentNo(), query.tradeNo(), query.amount());
    }

    public CloseDecision closeForOrder(long orderId) {
        requireEnabled();
        boolean paid = false;
        boolean unknown = false;

        // 逐笔确认订单关联的支付流水，任何未知状态都暂缓释放库存。
        for (var p : repository.paymentsForOrder(orderId)) {
            if (p.getPayStatus() == 2) {
                paid |= settle(p.getPaymentNo(), p.getTradeNo(), p.getTotalAmount());
                continue;
            }
            if (p.getPayStatus() == 5) {
                continue;
            }
            // 即使本地曾标记关闭，也要重新向渠道确认。
            var q = gateway.query(p.getPaymentNo());
            if (q.state() == PaymentGateway.State.PAID) {
                paid |= applyQuery(p, q);
                continue;
            }
            if (q.state() == PaymentGateway.State.UNKNOWN) {
                unknown = true;
                continue;
            }
            boolean closed = q.state() == PaymentGateway.State.CLOSED;

            // 支付页面可能在查询后才提交，渠道未找到也需要主动关单。
            if (q.state() == PaymentGateway.State.WAITING || q.state() == PaymentGateway.State.NOT_FOUND) {
                closed = gateway.close(p.getPaymentNo());
                if (!closed) {
                    var again = gateway.query(p.getPaymentNo());
                    if (again.state() == PaymentGateway.State.PAID) {
                        paid |= applyQuery(p, again);
                        continue;
                    }
                    closed = again.state() == PaymentGateway.State.CLOSED
                            || (again.state() == PaymentGateway.State.NOT_FOUND && p.getExpireTime() != null
                                    && !now().isBefore(p.getExpireTime()));
                }
            }

            // 只有确认关闭才更新本地流水；其余状态留待补偿重查。
            if (closed) {
                transactions.executeWithoutResult(s -> repository.close(p.getId(), now()));
            } else {
                unknown = true;
            }
        }

        return paid ? CloseDecision.PAID : unknown ? CloseDecision.UNKNOWN : CloseDecision.CLOSED;
    }

    public boolean closeOwned(String no, long user) {
        var p = owned(no, user);
        return closeForOrder(p.getOrderId()) == CloseDecision.CLOSED;
    }

    public int compensate() {
        if (!enabled) {
            return 0;
        }
        int count = 0;
        for (var p : repository.waiting()) {
            try {
                synchronize(p.getPaymentNo());
                count++;
            } catch (RuntimeException ignored) {
            } finally {
                repository.reconciled(p.getId(), now());
            }
        }
        return count;
    }

    public int closeExpired() {
        if (!enabled) {
            return 0;
        }
        int count = 0;
        for (long id : repository.expiredOrders(now())) {
            try {
                if (trade.beginCancel(id, null) && closeForOrder(id) == CloseDecision.CLOSED
                        && trade.finishCancel(id)) {
                    count++;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return count;
    }
}
