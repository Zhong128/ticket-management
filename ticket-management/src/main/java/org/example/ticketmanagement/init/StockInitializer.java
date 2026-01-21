// org/example/ticketmanagement/init/StockInitializer.java
package org.example.ticketmanagement.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.mapper.TicketTierMapper;
import org.example.ticketmanagement.pojo.TicketTier;
import org.example.ticketmanagement.service.StockService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockInitializer implements CommandLineRunner {

    private final TicketTierMapper ticketTierMapper;
    private final StockService stockService;

    @Override
    public void run(String... args) throws Exception {
        log.info("开始初始化票档库存到Redis");

        try {
            // 查询所有启用状态的票档
            List<TicketTier> ticketTiers = ticketTierMapper.selectByStatus(1);

            int successCount = 0;
            int failCount = 0;

            for (TicketTier tier : ticketTiers) {
                try {
                    // 初始化库存到Redis（如果Redis中不存在）
                    stockService.initStock(tier.getId(), tier.getAvailableStock());
                    successCount++;

                    log.debug("初始化票档库存成功: tierId={}, tierName={}, stock={}",
                            tier.getId(), tier.getTierName(), tier.getAvailableStock());
                } catch (Exception e) {
                    failCount++;
                    log.error("初始化票档库存失败: tierId={}, tierName={}",
                            tier.getId(), tier.getTierName(), e);
                }
            }

            log.info("票档库存初始化完成: 成功={}, 失败={}, 总数={}",
                    successCount, failCount, ticketTiers.size());

        } catch (Exception e) {
            log.error("初始化票档库存异常", e);
        }
    }
}