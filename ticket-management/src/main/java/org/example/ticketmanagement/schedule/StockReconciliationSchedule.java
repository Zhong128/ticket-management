package org.example.ticketmanagement.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.service.ReconciliationService;
import org.example.ticketmanagement.serviceimpl.SeckillServiceImpl;
import org.example.ticketmanagement.mapper.TradeStockMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trade.enabled", havingValue = "true")
public class StockReconciliationSchedule {

    private final ReconciliationService reconciliationService;
    private final TradeStockMapper stocks;
    private final ObjectProvider<SeckillServiceImpl> seckill;
    private int tierCursor;

    /** 每轮全局最多100条孤立预扣，轮转票档避免固定前缀饥饿。 */
    @Scheduled(fixedDelayString = "${seckill.recovery.provisional-delay-millis:5000}")
    public void recoverProvisionalReservations() {
        var tiers = stocks.selectTierIds();
        if (tiers.isEmpty()) {
            return;
        }
        int remaining = 100;
        int start = Math.floorMod(tierCursor++, tiers.size());
        for (int offset = 0; offset < tiers.size() && remaining > 0; offset++) {
            Long id = tiers.get((start + offset) % tiers.size());
            try {
                remaining -= seckill.getObject().recoverProvisional(id, remaining);
            } catch (RuntimeException unavailable) {
                log.warn("孤立预扣待下轮恢复 tierId={}", id, unavailable);
            }
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void reconcileStockDaily() {
        try {
            reconciliationService.reconcileAllStock();
        } catch (Exception e) {
            log.error("Stock projection reconciliation failed", e);
        }
    }
}
