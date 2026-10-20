package org.example.ticketmanagement.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.service.ReconciliationService;
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

    @Scheduled(fixedDelay = 60000)
    public void reconcileStockDaily() {
        try {
            reconciliationService.reconcileAllStock();
        } catch (Exception e) {
            log.error("Stock projection reconciliation failed", e);
        }
    }
}
