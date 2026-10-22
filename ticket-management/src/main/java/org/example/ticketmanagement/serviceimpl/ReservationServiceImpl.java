package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.exception.StockVersionConflictException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.example.ticketmanagement.dto.RedisReservation;
import org.example.ticketmanagement.mapper.SeckillAdmissionMapper;
import org.example.ticketmanagement.mapper.TradeCoreMapper;
import org.example.ticketmanagement.pojo.SeckillAdmission;
import org.example.ticketmanagement.pojo.StockReservation;
import org.example.ticketmanagement.service.ReservationService;
import org.example.ticketmanagement.trade.messaging.OutboxStore;
import org.example.ticketmanagement.util.OrderNoGenerator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static org.example.ticketmanagement.util.ReservationValues.number;
import static org.example.ticketmanagement.util.ReservationValues.time;

/**
 * 申请与订单的短事务规则；Redis网络调用由抢票和库存Service在事务外编排。
 * 通过原有Service分层向支付、消费者和调度提供统一状态变更。
 */
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final TradeCoreMapper mapper;
    private final SeckillAdmissionMapper admissions;
    private final TransactionTemplate transactions;
    private final OutboxStore outbox;
    private final StockServiceImpl stock;
    private final OrderNoGenerator orderNumbers;
    private final ObjectProvider<SeckillServiceImpl> seckill;
    private final Clock clock;

    @Autowired(required = false)
    private MeterRegistry metrics;

    @Value("${trade.enabled:false}")
    private boolean enabled;

    @Override
    public Map<String, Object> reserve(long userId, long tierId, int quantity, String requestKey) {
        return seckill.getObject().reserveApplication(userId, tierId, quantity, requestKey);
    }

    public StockReservation accept(RedisReservation pending) {
        requireEnabled();
        return transactions.execute(status -> {
            stock.requireReady(pending.tierId(), pending.epoch());
            admissions.initAdmission(pending, now());
            SeckillAdmission log = admissions.lockAdmission(pending.userId(), pending.requestKey());
            verifyParameters(log, pending.tierId(), pending.quantity());
            if ("ACCEPTED".equals(log.getAdmissionStatus())) {
                return admissions.reservation(log.getReservationId());
            }
            if (!"NEW".equals(log.getAdmissionStatus()) || !pending.token().equals(log.getRedisToken())) {
                throw new IllegalStateException("该请求已终止，请查询原结果");
            }

            // 同用户的持久化防线仍保留，但不会锁热点票档库存行。
            mapper.insertPurchaseGuard(pending.userId(), pending.tierId(), now());
            Map<String, Object> guard = mapper.lockPurchaseGuard(pending.userId(), pending.tierId());
            if (guard.get("reservation_id") != null || mapper.countActiveOrders(pending.userId(), pending.tierId()) > 0) {
                throw new IllegalStateException("已存在该票档有效申请或订单");
            }
            Map<String, Object> sale = requireSale(pending.tierId());
            LocalDateTime expires = LocalDateTime.ofInstant(Instant.ofEpochMilli(pending.expiresAt()), clock.getZone());
            if (!now().isBefore(expires)) {
                throw new IllegalStateException("申请已过期");
            }

            StockReservation row = new StockReservation();
            row.setUserId(pending.userId());
            row.setTierId(pending.tierId());
            row.setEventId(number(sale, "event_id"));
            row.setSessionId(number(sale, "session_id"));
            row.setRequestKey(pending.requestKey());
            row.setOrderNo(orderNumbers.generateOrderNo());
            row.setQuantity(pending.quantity());
            row.setUnitPrice((BigDecimal) sale.get("current_price"));
            row.setTotalAmount(row.getUnitPrice().multiply(BigDecimal.valueOf(pending.quantity())));
            row.setExpiresAt(expires);
            row.setCreateTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(pending.createdAt()), clock.getZone()));
            row.setUpdateTime(now());
            row.setRedisToken(pending.token());
            row.setStockEpoch(pending.epoch());
            admissions.insertReservation(row);
            mapper.attachPurchaseGuard(row.getId(), now(), pending.userId(), pending.tierId());
            admissions.accepted(pending.userId(), pending.requestKey(), row.getId(), now());
            outbox.enqueue("CREATE_ORDER", "create-order:" + row.getId() + ":0", Map.of("reservationId", row.getId()));
            outbox.enqueue("ORDER_DEADLINE", "deadline:" + row.getId(), Map.of("reservationId", row.getId()));
            outbox.enqueue("RESERVATION_ACCEPTED", "accepted:" + row.getId(), Map.of("reservationId", row.getId()));
            return admissions.reservation(row.getId());
        });
    }

    public SeckillAdmission findAdmission(long userId, String key) {
        return admissions.findAdmission(userId, key);
    }

    /** 封存与受理争用相同唯一键，提交未知时只根据此事务结果决定能否释放。 */
    public SeckillAdmission sealRejected(RedisReservation pending) {
        requireEnabled();
        return transactions.execute(status -> {
            stock.requireReady(pending.tierId(), pending.epoch());
            admissions.initAdmission(pending, now());
            SeckillAdmission log = admissions.lockAdmission(pending.userId(), pending.requestKey());
            if ("NEW".equals(log.getAdmissionStatus())) {
                admissions.rejected(pending.userId(), pending.requestKey(), now());
            }
            return admissions.findAdmission(pending.userId(), pending.requestKey());
        });
    }

    public void verifyParameters(SeckillAdmission log, long tierId, int quantity) {
        if (log != null && (log.getTierId() != tierId || log.getQuantity() != quantity)) {
            throw new IllegalArgumentException("requestKey已用于不同购买参数");
        }
    }

    public StockReservation find(long id) {
        return admissions.reservation(id);
    }

    @Override
    public Map<String, Object> request(long id, long userId) {
        requireEnabled();
        StockReservation row = admissions.reservation(id);
        if (row == null || row.getUserId() != userId) {
            throw new IllegalArgumentException("记录不存在或无权访问");
        }
        return view(row);
    }

    public Map<String, Object> requestByKey(long userId, String key) {
        requireEnabled();
        if (key == null || key.isBlank() || key.length() > 64) {
            throw new IllegalArgumentException("requestKey不能为空且最多64字符");
        }
        SeckillAdmission log = admissions.findAdmission(userId, key);
        if (log == null) {
            return Map.of("requestKey", key, "requestStatus", "UNKNOWN");
        }
        if (!"ACCEPTED".equals(log.getAdmissionStatus())) {
            return Map.of("requestKey", key, "requestStatus", log.getAdmissionStatus());
        }
        return request(log.getReservationId(), userId);
    }

    @Override
    public Long createOrder(long id) {
        requireEnabled();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return transactions.execute(status -> createInTransaction(id));
            } catch (StockVersionConflictException conflict) {
                if (attempt == 2) {
                    throw conflict;
                }
            }
        }
        throw new IllegalStateException("库存更新重试耗尽");
    }

    private Long createInTransaction(long id) {
        StockReservation initial = requireReservation(id);
        stock.requireReady(initial.getTierId(), null);
        StockReservation row = admissions.lockReservation(id);
        if (row.getOrderId() != null) {
            return row.getOrderId();
        }
        if (!"RESERVED".equals(row.getStatus())) {
            return null;
        }
        if (!now().isBefore(row.getExpiresAt())) {
            release(row, "EXPIRED_BEFORE_ORDER");
            return null;
        }

        Map<String, Object> sale;
        try {
            sale = requireSale(row.getTierId());
            if (number(sale, "event_id") != row.getEventId() || number(sale, "session_id") != row.getSessionId()) {
                release(row, "SALE_IDENTITY_CHANGED");
                return null;
            }
        } catch (IllegalArgumentException | IllegalStateException permanent) {
            release(row, "NOT_ON_SALE");
            return null;
        }
        if ("PENDING".equals(row.getInventoryState())) {
            if (number(sale, "available_stock") < row.getQuantity()) {
                release(row, "DATABASE_STOCK_INSUFFICIENT");
                return null;
            }
            if (admissions.deduct(row.getTierId(), row.getQuantity(), number(sale, "stock_version")) != 1) {
                if (metrics != null) {
                    metrics.counter("seckill.stock.optimistic.conflicts").increment();
                }
                throw new StockVersionConflictException();
            }
            admissions.held(id);
            stockChanged(row.getTierId());
        }

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("order_no", row.getOrderNo());
        order.put("user_id", row.getUserId());
        order.put("tier_id", row.getTierId());
        order.put("event_id", row.getEventId());
        order.put("session_id", row.getSessionId());
        order.put("quantity", row.getQuantity());
        order.put("total_amount", row.getTotalAmount());
        order.put("expires_at", row.getExpiresAt());
        order.put("create_time", now());
        order.put("update_time", now());
        mapper.insertOrder(order);
        long orderId = number(order, "id");
        mapper.markOrderCreated(orderId, now(), id);
        outbox.enqueue("ORDER_CREATED", "order-created:" + id, Map.of("reservationId", id));
        return orderId;
    }

    @Override
    public void lockOrderEpoch(long orderId) {
        Map<String, Object> order = admissions.order(orderId);
        if (order != null) {
            stock.requireReady(number(order, "tier_id"), null);
        } else {
            throw new IllegalArgumentException("订单不存在");
        }
    }

    @Override
    public boolean beginCancel(long orderId, Long userId) {
        requireEnabled();
        return Boolean.TRUE.equals(transactions.execute(status -> {
            lockOrderEpoch(orderId);
            Map<String, Object> order = mapper.lockOrder(orderId);
            if (order == null) {
                return false;
            }
            if (userId != null && number(order, "user_id") != userId) {
                throw new IllegalArgumentException("无权取消此订单");
            }
            if (number(order, "order_status") != 0 && number(order, "order_status") != 4) {
                return false;
            }
            mapper.markCancelRequested(now(), orderId);
            mapper.insertOrderCloseTask(orderId, (String) order.get("order_no"), number(order, "user_id"),
                    "order:" + orderId, "{\"orderId\":" + orderId + "}", now(), now(), now());
            return true;
        }));
    }

    @Override
    public boolean finishCancel(long orderId) {
        requireEnabled();
        return Boolean.TRUE.equals(transactions.execute(status -> {
            lockOrderEpoch(orderId);
            Map<String, Object> order = mapper.lockOrder(orderId);
            if (order == null || number(order, "order_status") != 4) {
                return false;
            }
            Map<String, Object> reservation = mapper.lockReservationByOrder(orderId);
            if (reservation != null) {
                release(admissions.reservation(number(reservation, "id")), "ORDER_CANCELED");
            } else {
                mapper.restoreAvailableStock((int) number(order, "quantity"), number(order, "tier_id"));
                stockChanged(number(order, "tier_id"));
                outbox.enqueue("CACHE_REBUILD", "legacy-release:" + orderId,
                        Map.of("tierId", number(order, "tier_id")));
            }
            mapper.markOrderCanceled(now(), orderId);
            return true;
        }));
    }

    @Override
    public boolean applyPaid(long orderId, long paymentId) {
        requireEnabled();
        return Boolean.TRUE.equals(transactions.execute(status -> {
            lockOrderEpoch(orderId);
            Map<String, Object> order = mapper.lockOrder(orderId);
            Map<String, Object> payment = mapper.findPayment(paymentId);
            if (order == null || payment == null || number(payment, "pay_status") != 2
                    || number(payment, "order_id") != orderId
                    || number(payment, "user_id") != number(order, "user_id")
                    || ((BigDecimal) payment.get("total_amount")).compareTo((BigDecimal) order.get("total_amount")) != 0) {
                return false;
            }
            int state = (int) number(order, "order_status");
            Map<String, Object> reservation = mapper.lockReservationByOrder(orderId);
            if (state == 2 || (reservation != null && "RELEASED".equals(reservation.get("status")))) {
                return false;
            }
            if (order.get("active_payment_id") != null) {
                return number(order, "active_payment_id") == paymentId && (state == 1 || state == 3);
            }
            if (state != 0 && state != 4) {
                return false;
            }
            mapper.markOrderPaid(paymentId, now(), now(), orderId);
            if (reservation != null) {
                mapper.markReservationPaid(now(), number(reservation, "id"));
            }
            return true;
        }));
    }

    @Override
    public void releaseUnordered(long id, String reason) {
        requireEnabled();
        transactions.executeWithoutResult(status -> {
            StockReservation initial = requireReservation(id);
            stock.requireReady(initial.getTierId(), null);
            StockReservation row = admissions.lockReservation(id);
            if (row.getOrderId() == null) {
                release(row, reason);
            }
        });
    }

    @Override
    public void creationFailed(long id, String error) {
        requireEnabled();
        transactions.executeWithoutResult(status -> {
            StockReservation initial = requireReservation(id);
            stock.requireReady(initial.getTierId(), null);
            StockReservation row = admissions.lockReservation(id);
            if (!"RESERVED".equals(row.getStatus()) || row.getOrderId() != null) {
                return;
            }
            int attempts = row.getAttempts() + 1;
            mapper.updateCreationAttempts(attempts, now(), id);
            if (attempts >= 3 || !now().isBefore(row.getExpiresAt())) {
                release(row, "ORDER_CREATION_FAILED: " + error);
                return;
            }
            outbox.enqueueAt("CREATE_ORDER", "create-order:" + id + ":" + attempts,
                    Map.of("reservationId", id), now().plusSeconds(attempts == 1 ? 10 : 30));
        });
    }

    private void release(StockReservation row, String reason) {
        if ("RELEASED".equals(row.getStatus()) || "PAID".equals(row.getStatus())) {
            return;
        }
        String safe = Objects.toString(reason, "RELEASED");
        if (mapper.releaseReservation(safe.substring(0, Math.min(255, safe.length())), now(), row.getId()) != 1) {
            return;
        }
        if ("HELD".equals(row.getInventoryState())) {
            if (mapper.restoreAvailableStock(row.getQuantity(), row.getTierId()) != 1) {
                throw new IllegalStateException("数据库库存释放失败");
            }
            stockChanged(row.getTierId());
        }
        admissions.released(row.getId());
        mapper.clearPurchaseGuard(now(), row.getUserId(), row.getTierId(), row.getId());
        outbox.enqueue("RESERVATION_RELEASED", "released:" + row.getId(), Map.of("reservationId", row.getId()));
    }

    private void stockChanged(long tierId) {
        Map<String, Object> tier = mapper.findStockProjection(tierId);
        long version = number(tier, "stock_version");
        outbox.enqueue("STOCK_CHANGED", "stock:" + tierId + ":" + version,
                Map.of("tierId", tierId, "version", version, "stock", tier.get("available_stock")));
    }

    private StockReservation requireReservation(long id) {
        StockReservation row = admissions.reservation(id);
        if (row == null) {
            throw new IllegalArgumentException("申请不存在");
        }
        return row;
    }

    private Map<String, Object> requireSale(long tierId) {
        Map<String, Object> sale = admissions.sale(tierId);
        if (sale == null || number(sale, "status") != 1 || number(sale, "event_status") != 1
                || number(sale, "session_status") != 1) {
            throw new IllegalStateException("票档、场次或演出不可售");
        }
        if ((time(sale, "sale_start_time") != null && now().isBefore(time(sale, "sale_start_time")))
                || (time(sale, "sale_end_time") != null && !now().isBefore(time(sale, "sale_end_time")))
                || ((BigDecimal) sale.get("current_price")).signum() <= 0) {
            throw new IllegalStateException("不在售票时间内或价格无效");
        }
        return sale;
    }

    public Map<String, Object> view(StockReservation row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("requestId", row.getId());
        view.put("requestStatus", row.getStatus());
        view.put("requestKey", row.getRequestKey());
        view.put("orderId", row.getOrderId());
        view.put("orderNo", row.getOrderNo());
        view.put("expireTime", row.getExpiresAt());
        view.put("tierId", row.getTierId());
        view.put("quantity", row.getQuantity());
        view.put("price", row.getUnitPrice());
        view.put("totalAmount", row.getTotalAmount());
        view.put("releaseReason", row.getReleaseReason());
        return view;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("交易功能尚未启用");
        }
    }

}
