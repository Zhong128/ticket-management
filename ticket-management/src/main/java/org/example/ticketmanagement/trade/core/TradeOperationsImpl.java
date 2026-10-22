package org.example.ticketmanagement.trade.core;

import org.example.ticketmanagement.mapper.TradeCoreMapper;
import org.example.ticketmanagement.trade.messaging.OutboxStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * MySQL owns acceptance, inventory and all terminal transitions; Redis is a projection.
 */
public class TradeOperationsImpl implements TradeOperations {

    private final TradeCoreMapper mapper;
    private final TransactionTemplate tx;
    private final OutboxStore outbox;
    private final Clock clock;
    private final boolean enabled;
    private final Semaphore reservationSlots;

    public TradeOperationsImpl(TradeCoreMapper mapper, PlatformTransactionManager manager, OutboxStore outbox,
            Clock clock, boolean enabled) {
        this(mapper, manager, outbox, clock, enabled, 6);
    }

    @Autowired
    public TradeOperationsImpl(TradeCoreMapper mapper, PlatformTransactionManager manager, OutboxStore outbox,
            Clock clock, @Value("${trade.enabled:false}") boolean enabled,
            @Value("${trade.reservation.max-concurrent:6}") int maxConcurrent) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("抢票事务并发数必须大于零");
        }
        this.mapper = mapper;
        this.tx = new TransactionTemplate(manager);
        this.outbox = outbox;
        this.clock = clock;
        this.enabled = enabled;
        this.reservationSlots = new Semaphore(maxConcurrent, true);
    }

    @Override
    public Map<String, Object> reserve(long userId, long tierId, int quantity, String requestKey) {
        checkEnabled();

        // 1. 校验购买数量和幂等键。
        if (userId <= 0 || tierId <= 0 || quantity < 1 || quantity > 5) {
            throw new IllegalArgumentException("每单购买数量必须为1至5张");
        }
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 64) {
            throw new IllegalArgumentException("requestKey不能为空且最多64字符");
        }

        // 控制抢票事务占用的连接数，给落单和 Outbox 发布留处理空间。
        return withReservationSlot(() -> tx.execute(s -> {
            LocalDateTime now = now();

            // 2. 锁定用户与票档，重复请求直接返回原预占。
            mapper.insertPurchaseGuard(userId, tierId, now);
            Map<String, Object> guard = one(mapper.lockPurchaseGuard(userId, tierId));
            Map<String, Object> old = mapper.findReservationByRequest(userId, requestKey);
            if (old != null) {
                if (number(old, "tier_id") != tierId || number(old, "quantity") != quantity) {
                    throw new IllegalArgumentException("requestKey已用于不同购买参数");
                }
                return response(old);
            }
            if (guard.get("reservation_id") != null) {
                throw new IllegalStateException("已持有该票档的有效申请");
            }
            if (mapper.countActiveOrders(userId, tierId) > 0) {
                throw new IllegalStateException("已存在该票档有效订单（含历史订单冲突）");
            }

            // 3. 锁定票档，检查售卖状态、时间和价格。
            Map<String, Object> tier = one(mapper.lockSaleTier(tierId));
            if (number(tier, "status") != 1 || number(tier, "event_status") != 1
                    || number(tier, "session_status") != 1) {
                throw new IllegalStateException("票档、场次或演出不可售");
            }
            if ((time(tier, "sale_start_time") != null && now.isBefore(time(tier, "sale_start_time")))
                    || (time(tier, "sale_end_time") != null && !now.isBefore(time(tier, "sale_end_time")))) {
                throw new IllegalStateException("不在售票时间内");
            }
            BigDecimal price = (BigDecimal) tier.get("current_price");
            if (price == null || price.signum() <= 0) {
                throw new IllegalStateException("票档价格无效");
            }

            // 4. 在同一事务中扣库存、写预占和 Outbox。
            if (mapper.deductAvailableStock(quantity, tierId) != 1) {
                throw new IllegalStateException("库存不足");
            }
            String orderNo = UUID.randomUUID().toString().replace("-", "");
            long id = insertReservation(userId, tierId, tier, requestKey, orderNo, quantity, price, now);
            mapper.attachPurchaseGuard(id, now, userId, tierId);
            outbox.enqueue("CREATE_ORDER", "create-order:" + id + ":0", Map.of("reservationId", id));
            outbox.enqueue("ORDER_DEADLINE", "deadline:" + id, Map.of("reservationId", id));
            stockChanged(tierId);

            return response(one(mapper.findReservation(id)));
        }));
    }

    private Map<String, Object> withReservationSlot(Supplier<Map<String, Object>> action) {
        boolean acquired;
        try {
            acquired = reservationSlots.tryAcquire(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("抢票请求已中断", e);
        }
        if (!acquired) {
            throw new IllegalStateException("抢票请求繁忙，请稍后重试");
        }

        try {
            return action.get();
        } finally {
            reservationSlots.release();
        }
    }

    @Override
    public Map<String, Object> request(long id, long userId) {
        checkEnabled();
        Map<String, Object> r = one(mapper.findOwnedReservation(id, userId));
        return response(r);
    }

    @Override
    public Long createOrder(long id) {
        checkEnabled();
        return tx.execute(s -> {
            // 订单创建可能被消息重复触发，先检查预占状态。
            Map<String, Object> r = one(mapper.lockReservation(id));
            if (r.get("order_id") != null) {
                return number(r, "order_id");
            }
            if (!"RESERVED".equals(r.get("status"))) {
                return null;
            }
            if (!now().isBefore(time(r, "expires_at"))) {
                release(r, "EXPIRED_BEFORE_ORDER");
                return null;
            }

            // 创建订单、更新预占、写通知事件一起提交。
            long orderId = insertOrder(r);
            mapper.markOrderCreated(orderId, now(), id);
            outbox.enqueue("ORDER_CREATED", "order-created:" + id, Map.of("reservationId", id));
            return orderId;
        });
    }

    @Override
    public boolean beginCancel(long orderId, Long userId) {
        checkEnabled();
        return Boolean.TRUE.equals(tx.execute(s -> {
            // 先锁订单，校验归属和当前状态。
            Map<String, Object> o = mapper.lockOrder(orderId);
            if (o == null) {
                return false;
            }
            if (userId != null && number(o, "user_id") != userId) {
                throw new IllegalArgumentException("无权操作此订单");
            }
            int status = (int) number(o, "order_status");
            if (status != 0 && status != 4) {
                return false;
            }

            // 先进入取消中，支付渠道确认关闭后才释放库存。
            mapper.markCancelRequested(now(), orderId);
            mapper.insertOrderCloseTask(orderId, (String) o.get("order_no"), number(o, "user_id"), "order:" + orderId,
                    "{\"orderId\":" + orderId + "}", now(), now(), now());
            return true;
        }));
    }

    @Override
    public boolean finishCancel(long orderId) {
        checkEnabled();
        return Boolean.TRUE.equals(tx.execute(s -> {
            Map<String, Object> o = mapper.lockOrder(orderId);
            if (o == null || number(o, "order_status") != 4) {
                return false;
            }

            // 新订单释放预占；旧订单按原库存扣减方式归还。
            Map<String, Object> r = mapper.lockReservationByOrder(orderId);
            if (r != null) {
                release(r, "ORDER_CANCELED");
            } else {
                // Historical pending orders already deducted MySQL stock on
                // creation.
                increase(number(o, "tier_id"), (int) number(o, "quantity"));
            }
            mapper.markOrderCanceled(now(), orderId);
            return true;
        }));
    }

    @Override
    public boolean applyPaid(long orderId, long paymentId) {
        checkEnabled();
        return Boolean.TRUE.equals(tx.execute(s -> {
            Map<String, Object> o = mapper.lockOrder(orderId);
            if (o == null) {
                return false;
            }
            Map<String, Object> p = mapper.findPayment(paymentId);
            if (p == null || number(p, "pay_status") != 2 || number(p, "order_id") != orderId
                    || number(p, "user_id") != number(o, "user_id")
                    || ((BigDecimal) p.get("total_amount")).compareTo((BigDecimal) o.get("total_amount")) != 0) {
                return false;
            }

            // 已取消或已释放库存的订单不得重新转成已支付。
            int status = (int) number(o, "order_status");
            if (status == 2) {
                return false;
            }
            Map<String, Object> r = mapper.lockReservationByOrder(orderId);
            if (r != null && "RELEASED".equals(r.get("status"))) {
                return false;
            }
            if (o.get("active_payment_id") != null) {
                return number(o, "active_payment_id") == paymentId && (status == 1 || status == 3);
            }
            if (status != 0 && status != 4) {
                return false;
            }

            // 订单与预占同步记为已支付，保存真正成功的流水。
            mapper.markOrderPaid(paymentId, now(), now(), orderId);
            if (r != null) {
                mapper.markReservationPaid(now(), number(r, "id"));
            }
            return true;
        }));
    }

    @Override
    public void releaseUnordered(long id, String reason) {
        checkEnabled();
        tx.executeWithoutResult(s -> {
            var r = one(mapper.lockReservation(id));
            if (r.get("order_id") == null) {
                release(r, reason);
            }
        });
    }

    @Override
    public void creationFailed(long id, String error) {
        checkEnabled();
        tx.executeWithoutResult(s -> {
            var r = one(mapper.lockReservation(id));
            if (!"RESERVED".equals(r.get("status")) || r.get("order_id") != null) {
                return;
            }
            int attempts = (int) number(r, "attempts") + 1;
            mapper.updateCreationAttempts(attempts, now(), id);

            // 到期或三次失败后归还库存，否则按次数延迟重试。
            if (attempts >= 3 || !now().isBefore(time(r, "expires_at"))) {
                release(r, "ORDER_CREATION_FAILED: " + error);
                return;
            }
            outbox.enqueueAt("CREATE_ORDER", "create-order:" + id + ":" + attempts, Map.of("reservationId", id),
                    now().plusSeconds(attempts == 1 ? 10 : 30));
        });
    }

    private void release(Map<String, Object> r, String reason) {
        if ("RELEASED".equals(r.get("status")) || "PAID".equals(r.get("status"))) {
            return;
        }

        // 条件更新成功才归还库存，重复释放不会重复加库存。
        String safe = Objects.toString(reason, "RELEASED");
        if (mapper.releaseReservation(safe.substring(0, Math.min(255, safe.length())), now(), number(r, "id")) != 1) {
            return;
        }
        increase(number(r, "tier_id"), (int) number(r, "quantity"));
        mapper.clearPurchaseGuard(now(), number(r, "user_id"), number(r, "tier_id"), number(r, "id"));
    }

    private void increase(long tierId, int quantity) {
        if (mapper.restoreAvailableStock(quantity, tierId) != 1) {
            throw new IllegalStateException("库存释放失败");
        }
        stockChanged(tierId);
    }

    private void stockChanged(long tierId) {
        var tier = one(mapper.findStockProjection(tierId));
        long version = number(tier, "stock_version");
        outbox.enqueue("STOCK_CHANGED", "stock:" + tierId + ":" + version,
                Map.of("tierId", tierId, "version", version, "stock", tier.get("available_stock")));
    }

    private Map<String, Object> response(Map<String, Object> r) {
        Map<String, Object> result = new LinkedHashMap<>();
        String[] source = { "id", "status", "order_id", "order_no", "expires_at", "tier_id", "quantity", "unit_price",
                "total_amount" };
        String[] target = { "requestId", "requestStatus", "orderId", "orderNo", "expireTime", "tierId", "quantity",
                "price", "totalAmount" };
        for (int i = 0; i < source.length; i++) {
            result.put(target[i], "expires_at".equals(source[i]) ? time(r, source[i]) : r.get(source[i]));
        }
        return result;
    }

    private long insertReservation(long userId, long tierId, Map<String, Object> tier, String requestKey,
            String orderNo, int quantity, BigDecimal price, LocalDateTime now) {
        Map<String, Object> reservation = new LinkedHashMap<>();
        reservation.put("user_id", userId);
        reservation.put("tier_id", tierId);
        reservation.put("event_id", tier.get("event_id"));
        reservation.put("session_id", tier.get("session_id"));
        reservation.put("request_key", requestKey);
        reservation.put("order_no", orderNo);
        reservation.put("quantity", quantity);
        reservation.put("unit_price", price);
        reservation.put("total_amount", price.multiply(BigDecimal.valueOf(quantity)));
        reservation.put("expires_at", now.plusMinutes(30));
        reservation.put("create_time", now);
        reservation.put("update_time", now);
        mapper.insertReservation(reservation);
        return number(reservation, "id");
    }

    private long insertOrder(Map<String, Object> reservation) {
        Map<String, Object> order = new LinkedHashMap<>(reservation);
        order.remove("id");
        order.put("create_time", now());
        order.put("update_time", now());
        mapper.insertOrder(order);
        return number(order, "id");
    }

    private Map<String, Object> one(Map<String, Object> row) {
        if (row == null) {
            throw new IllegalArgumentException("记录不存在或无权访问");
        }
        return row;
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static LocalDateTime time(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : v instanceof Timestamp t ? t.toLocalDateTime() : (LocalDateTime) v;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private void checkEnabled() {
        if (!enabled) {
            throw new IllegalStateException("交易功能尚未启用");
        }
    }
}
