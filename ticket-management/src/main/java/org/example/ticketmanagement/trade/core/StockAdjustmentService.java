package org.example.ticketmanagement.trade.core;

import org.example.ticketmanagement.mapper.TradeStockMapper;
import org.example.ticketmanagement.mapper.SeckillAdmissionMapper;
import org.example.ticketmanagement.exception.StockCacheNotReadyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.example.ticketmanagement.trade.messaging.OutboxStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Capacity changes preserve sold quantity and serialize against reservation stock mutations.
 */
@Service
public class StockAdjustmentService {

    private final TradeStockMapper stockMapper;
    private final TransactionTemplate tx;
    private final OutboxStore outbox;
    private final boolean enabled;

    @Autowired
    private SeckillAdmissionMapper admissions;
    private final Clock clock;

    public StockAdjustmentService(TradeStockMapper stockMapper, PlatformTransactionManager manager, OutboxStore outbox,
            Clock clock, @Value("${trade.enabled:false}") boolean enabled) {
        this.stockMapper = stockMapper;
        this.tx = new TransactionTemplate(manager);
        this.outbox = outbox;
        this.enabled = enabled;
        this.clock = clock;
    }

    public Map<String, Object> adjust(long tierId, int delta) {
        // 1. 仅允许对已下架票档调整非零库存。
        if (!enabled) {
            throw new IllegalStateException("交易功能尚未启用");
        }
        if (delta == 0) {
            throw new IllegalArgumentException("库存调整量不能为0");
        }

        return tx.execute(s -> {
            // 实际运行时与缓存重建共享屏障，避免调整容量穿过重建快照。
            if (admissions != null) {
                var epoch = admissions.shareEpoch(tierId);
                if (epoch == null || !"READY".equals(epoch.getCacheStatus())) {
                    throw new StockCacheNotReadyException();
                }
            }
            // 2. 锁定票档，并确认没有未完成的预占或关单。
            var tier = stockMapper.lockTier(tierId);
            if (tier == null) {
                throw new IllegalArgumentException("票档不存在");
            }
            if (((Number) tier.get("status")).intValue() != 0) {
                throw new IllegalStateException("调整库存前必须下架票档");
            }
            if (stockMapper.countUnsettledReservations(tierId) > 0 || stockMapper.countUnsettledOrders(tierId) > 0) {
                throw new IllegalStateException("票档仍存在未完成的预占或待关单订单");
            }

            // 3. 保持已售出数量不变，校验调整后的总库存与可售库存。
            long total = ((Number) tier.get("total_stock")).longValue() + delta;
            long available = ((Number) tier.get("available_stock")).longValue() + delta;
            if (total < 0 || available < 0 || available > total || total > Integer.MAX_VALUE
                    || available > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("调整后库存无效，不能减少已售出或已占用的票");
            }

            // 4. 更新 MySQL 后写出库存事件，供 Redis 投影同步。
            stockMapper.adjustCapacity(tierId, delta, LocalDateTime.now(clock));
            long version = ((Number) tier.get("stock_version")).longValue() + 1;
            outbox.enqueue("STOCK_CHANGED", "stock:" + tierId + ":" + version,
                    Map.of("tierId", tierId, "version", version, "stock", (int) available));
            outbox.enqueue("CACHE_REBUILD", "capacity:" + tierId + ":" + version, Map.of("tierId", tierId));
            return Map.of("tierId", tierId, "totalStock", (int) total, "stock", (int) available, "version", version);
        });
    }
}
