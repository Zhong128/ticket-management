package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.mapper.SeckillAdmissionMapper;
import org.example.ticketmanagement.mapper.TradeStockMapper;
import org.example.ticketmanagement.service.ReconciliationService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 抢票配额核对不能把数据库库存直接覆盖到Redis。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {
    private final TradeStockMapper stocks;
    private final SeckillAdmissionMapper admissions;
    private final StockServiceImpl stock;
    private final Clock clock;
    private final Map<Long, Instant> discrepancies = new ConcurrentHashMap<>();

    @Override
    public void reconcileAllStock() {
        for (Long id : stocks.selectTierIds()) {
            try {
                reconcileStockByTierId(id);
            } catch (RuntimeException failure) {
                log.warn("票档对账或恢复暂未完成 tierId={}", id, failure);
            }
        }
    }

    @Override
    public boolean reconcileStockByTierId(Long tierId) {
        // prepare缺失时受控重建；正常情况下只检查现有代次和数量。
        int actual = stock.getStock(tierId);
        int expected = stocks.selectAvailableStock(tierId) - admissions.pendingQuantity(tierId);
        stock.refresh(tierId);
        if (actual == expected) {
            discrepancies.remove(tierId);
            return true;
        }
        if (stock.hasProvisional(tierId)) {
            // 可靠受理之前的临时预扣由5秒扫描处理，不能误当作丢库存。
            return false;
        }
        Instant first = discrepancies.computeIfAbsent(tierId, ignored -> clock.instant());
        if (clock.instant().isBefore(first.plusSeconds(10))) {
            return false;
        }

        // 连续观察到差额才关闭入口，重建时会等待所有在途数据库变更。
        log.warn("库存差额持续存在，暂停并恢复 tierId={}, expected={}, actual={}", tierId, expected, actual);
        stock.pauseCache(tierId);
        stock.rebuild(tierId);
        discrepancies.remove(tierId);
        return false;
    }
}
