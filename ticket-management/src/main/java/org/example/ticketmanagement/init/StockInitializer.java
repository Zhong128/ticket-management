package org.example.ticketmanagement.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.service.ReconciliationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trade.enabled", havingValue = "true")
public class StockInitializer implements CommandLineRunner {

    private final ReconciliationService reconciliation;

    @Override
    public void run(String... args) {
        try {
            reconciliation.reconcileAllStock();
        } catch (Exception e) {
            log.warn("库存初始化未完成，对应票档保持暂停，等待受控恢复", e);
        }
    }
}
