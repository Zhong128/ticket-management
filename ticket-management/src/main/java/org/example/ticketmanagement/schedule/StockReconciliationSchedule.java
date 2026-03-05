// org/example/ticketmanagement/schedule/StockReconciliationSchedule.java
package org.example.ticketmanagement.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.service.ReconciliationService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class StockReconciliationSchedule {

    private final ReconciliationService reconciliationService;

    /**
     * 库存对账定时任务：每天凌晨3点执行一次
     * 核对Redis库存与数据库库存是否一致，不一致则以数据库为准修复
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void reconcileStockDaily() {
        log.info("启动每日库存对账任务");
        long startTime = System.currentTimeMillis();

        try {
            reconciliationService.reconcileAllStock();

            long cost = System.currentTimeMillis() - startTime;
            log.info("每日库存对账任务完成，耗时：{}ms", cost);

        } catch (Exception e) {
            log.error("每日库存对账任务异常", e);
        }
    }

    /**
     * 高峰期后对账：每天中午12:30执行一次
     * 用于处理上午抢票高峰后的数据一致性
     */
    @Scheduled(cron = "0 30 12 * * ?")
    public void reconcileStockNoon() {
        log.info("启动午间库存对账任务");
        long startTime = System.currentTimeMillis();

        try {
            reconciliationService.reconcileAllStock();

            long cost = System.currentTimeMillis() - startTime;
            log.info("午间库存对账任务完成，耗时：{}ms", cost);

        } catch (Exception e) {
            log.error("午间库存对账任务异常", e);
        }
    }


}