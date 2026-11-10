package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.SeckillOrderDTO;
import org.example.ticketmanagement.dto.RedisReservation;
import org.example.ticketmanagement.pojo.SeckillAdmission;
import org.example.ticketmanagement.pojo.StockReservation;
import org.example.ticketmanagement.service.SeckillLockService;
import org.springframework.beans.factory.annotation.Value;
import org.example.ticketmanagement.mapper.EventMapper;
import org.example.ticketmanagement.mapper.EventSessionMapper;
import org.example.ticketmanagement.mapper.TicketTierMapper;
import org.example.ticketmanagement.service.SeckillService;
import org.example.ticketmanagement.dto.SeckillQualificationDTO;
import org.example.ticketmanagement.vo.SeckillResultVO;
import org.example.ticketmanagement.exception.ReservationRejectedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final ReservationServiceImpl reservations;
    private final StockServiceImpl stock;
    private final SeckillLockService locks;
    private final TicketTierMapper tiers;
    private final EventSessionMapper sessions;
    private final EventMapper events;
    private final Clock clock;

    @Value("${trade.enabled:false}")
    private boolean enabled;

    @Value("${seckill.lock.segments:20}")
    private int segments;

    @Value("${seckill.lock.enabled:true}")
    private boolean lockingEnabled = true;

    @Override
    public SeckillResultVO seckill(Long userId, SeckillOrderDTO dto) {
        if (dto.getQuantity() == null) {
            return SeckillResultVO.fail("购买数量不能为空");
        }
        String key = dto.getRequestKey();
        if (key == null || key.isBlank()) {
            key = UUID.randomUUID().toString();
        }
        try {
            var accepted = reserveApplication(userId, dto.getTierId(), dto.getQuantity(), key);
            SeckillResultVO result = SeckillResultVO.success((String) accepted.get("orderNo"),
                    (Long) accepted.get("orderId"), (BigDecimal) accepted.get("totalAmount"), null,
                    (BigDecimal) accepted.get("price"), ((Number) accepted.get("quantity")).intValue());
            result.setRequestId(((Number) accepted.get("requestId")).longValue());
            result.setRequestStatus((String) accepted.get("requestStatus"));
            result.setExpireTime((LocalDateTime) accepted.get("expireTime"));
            result.setRequestKey(key);
            result.setMessage("申请已受理，请查询落单结果");
            return result;
        } catch (IllegalArgumentException | IllegalStateException e) {
            SeckillResultVO failure = SeckillResultVO.fail(e.getMessage());
            failure.setRequestKey(key);
            failure.setRequestStatus(e instanceof AdmissionUncertainException ? "UNKNOWN" : "REJECTED");
            return failure;
        } catch (RuntimeException unavailable) {
            // 不向客户端暴露SQL、连接地址或底层配置；预扣结果交由同键查询确认。
            log.warn("抢票暂不可用 userId={}, tierId={}", userId, dto.getTierId(), unavailable);
            SeckillResultVO failure = SeckillResultVO.fail("服务暂不可用，请使用原requestKey查询或重试");
            failure.setRequestKey(key);
            failure.setRequestStatus("UNKNOWN");
            return failure;
        }
    }

    /** 分段锁只覆盖短受理流程，真实库存由Lua和持久化约束共同保护。 */
    public Map<String, Object> reserveApplication(long userId, long tierId, int quantity, String key) {
        return reserveApplication(userId, tierId, quantity, key, null);
    }

    /** 仅供已确认卡片调用，单价随申请传入可靠受理事务。 */
    public Map<String, Object> reserveConfirmedApplication(long userId, long tierId, int quantity,
            String key, BigDecimal expectedPrice) {
        if (expectedPrice == null || expectedPrice.signum() <= 0) {
            throw new IllegalArgumentException("确认价格不合法");
        }
        return reserveApplication(userId, tierId, quantity, key, expectedPrice);
    }

    private Map<String, Object> reserveApplication(long userId, long tierId, int quantity, String key,
            BigDecimal expectedPrice) {
        if (!enabled) {
            throw new IllegalStateException("交易功能尚未启用");
        }
        if (userId <= 0 || tierId <= 0 || quantity < 1 || quantity > 5
                || key == null || key.isBlank() || key.length() > 64) {
            throw new IllegalArgumentException("抢票参数不合法");
        }
        // 对照测试可关闭分段锁，Lua及数据库约束始终保留。
        if (!lockingEnabled) {
            return acceptUnderLock(userId, tierId, quantity, key, expectedPrice);
        }
        // 只有确认入口可将尚未进入受理的锁拒绝判为确定未提交；执行开始后保留原未知语义。
        AtomicBoolean admissionStarted = new AtomicBoolean();
        try {
            return locks.executeWithSegmentLock(userId, tierId, segments, () -> {
                admissionStarted.set(true);
                return acceptUnderLock(userId, tierId, quantity, key, expectedPrice);
            });
        } catch (RuntimeException failure) {
            if (expectedPrice != null && !admissionStarted.get()) {
                throw new ReservationRejectedException("受理入口暂不可用，尚未提交申请，请稍后重新确认");
            }
            throw failure;
        }
    }

    private Map<String, Object> acceptUnderLock(long userId, long tierId, int quantity, String key,
            BigDecimal expectedPrice) {
        RedisReservation pending;
        try {
            pending = stock.reserve(userId, tierId, quantity, key);
        } catch (IllegalStateException unavailable) {
            // 缓存重建后可能只恢复了有效占位，历史同键结果仍从可靠日志返回。
            SeckillAdmission known = reservations.findAdmission(userId, key);
            reservations.verifyParameters(known, tierId, quantity);
            if (known != null && "ACCEPTED".equals(known.getAdmissionStatus())) {
                return reservations.request(known.getReservationId(), userId);
            }
            throw unavailable;
        }

        StockReservation accepted;
        try {
            accepted = expectedPrice == null ? reservations.accept(pending) : reservations.accept(pending, expectedPrice);
        } catch (RuntimeException failure) {
            // 即使提交响应丢失，也必须通过同一受理日志封存结果后才能回补。
            SeckillAdmission resolved;
            try {
                resolved = reservations.sealRejected(pending);
            } catch (RuntimeException unresolved) {
                throw new AdmissionUncertainException();
            }
            if ("ACCEPTED".equals(resolved.getAdmissionStatus())) {
                try {
                    reservations.verifyParameters(resolved, tierId, quantity);
                } catch (IllegalArgumentException mismatch) {
                    // 同键指向另一票档时只释放本次新token，不能等孤立扫描。
                    stock.release(pending);
                    throw mismatch;
                }
                accepted = reservations.find(resolved.getReservationId());
            } else {
                stock.release(pending);
                throw failure;
            }
        }

        // 同键历史申请可能对应其他代次或token，仅释放这次多余的预扣。
        try {
            if (!accepted.getRedisToken().equals(pending.token())) {
                stock.release(pending);
            }
            stock.confirm(stock.from(accepted));
        } catch (RuntimeException delayed) {
            // 受理事务已经提交，Outbox和扫描会补齐Redis状态，不反向撤销申请。
            log.warn("Redis受理确认待恢复 requestId={}", accepted.getId(), delayed);
        }
        return reservations.view(accepted);
    }

    /** 扫描只清理超过60秒的未确认预扣，先确定数据库终态再处理Redis。 */
    public int recoverProvisional(long tierId, int limit) {
        var pendingBatch = stock.provisional(tierId, clock.millis() - 60_000, limit);
        for (RedisReservation pending : pendingBatch) {
            SeckillAdmission decision = reservations.sealRejected(pending);
            if ("ACCEPTED".equals(decision.getAdmissionStatus())
                    && pending.token().equals(decision.getRedisToken())) {
                StockReservation row = reservations.find(decision.getReservationId());
                if ("RELEASED".equals(row.getStatus())) {
                    stock.release(stock.from(row));
                } else {
                    stock.confirm(stock.from(row));
                }
            } else {
                stock.release(pending);
            }
        }
        return pendingBatch.size();
    }

    private static final class AdmissionUncertainException extends IllegalStateException {
        private AdmissionUncertainException() {
            super("受理结果暂未确认，请使用原requestKey查询或重试");
        }
    }

    @Override
    public boolean checkSeckillQualification(Long userId, Long tierId, Integer quantity) {
        return getSeckillQualification(userId, tierId, quantity).qualified();
    }

    @Override
    public SeckillQualificationDTO getSeckillQualification(Long userId, Long tierId, Integer quantity) {
        // 沿用原有校验顺序和库存来源，仅为否定结果补充原因。
        if (quantity == null || quantity < 1 || quantity > 5) {
            return SeckillQualificationDTO.denied("INVALID_QUANTITY", "购买数量须为1至5张");
        }
        var tier = tiers.selectById(tierId);
        if (tier == null || !Integer.valueOf(1).equals(tier.getStatus())) {
            return SeckillQualificationDTO.denied("TIER_UNAVAILABLE", "票档不存在或未启用");
        }
        if (tier.getAvailableStock() < quantity) {
            return SeckillQualificationDTO.denied("INSUFFICIENT_STOCK", "当前票档库存不足");
        }
        var session = sessions.selectById(tier.getSessionId());
        var event = events.selectById(tier.getEventId());
        if (session == null || !Integer.valueOf(1).equals(session.getStatus()) || event == null
                || !Integer.valueOf(1).equals(event.getStatus())) {
            return SeckillQualificationDTO.denied("EVENT_UNAVAILABLE", "演出或场次不存在或未启用");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (event.getSaleStartTime() != null && now.isBefore(event.getSaleStartTime())) {
            return SeckillQualificationDTO.denied("SALE_NOT_STARTED", "演出尚未开始售票");
        }
        if (event.getSaleEndTime() != null && !now.isBefore(event.getSaleEndTime())) {
            return SeckillQualificationDTO.denied("SALE_ENDED", "演出已结束售票");
        }
        if (stock.hasUserSeckilled(userId, tierId)) {
            return SeckillQualificationDTO.denied("ALREADY_PURCHASED", "当前用户已有该票档的购买占用，请查看订单或申请状态");
        }
        if (!stock.hasEnoughStock(tierId, quantity)) {
            return SeckillQualificationDTO.denied("INSUFFICIENT_STOCK", "实时可用库存不足");
        }
        return SeckillQualificationDTO.allowed();
    }

    @Override
    public Integer getRealTimeStock(Long tierId) {
        return stock.getStock(tierId);
    }
}
