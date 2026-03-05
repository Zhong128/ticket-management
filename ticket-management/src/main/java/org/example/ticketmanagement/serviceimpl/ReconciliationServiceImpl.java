// org/example/ticketmanagement/serviceimpl/ReconciliationServiceImpl.java
package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.SeckillConstant;
import org.example.ticketmanagement.mapper.TicketTierMapper;
import org.example.ticketmanagement.pojo.TicketTier;
import org.example.ticketmanagement.service.ReconciliationService;
import org.example.ticketmanagement.service.StockService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private final TicketTierMapper ticketTierMapper;
    private final StockService stockService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void reconcileAllStock() {
        log.info("开始核对所有票档库存");
        long startTime = System.currentTimeMillis();

        try {
            // 1. 查询所有启用状态的票档
            List<TicketTier> ticketTiers = ticketTierMapper.selectAllEnabled();
            if (ticketTiers.isEmpty()) {
                log.info("没有需要核对的票档");
                return;
            }

            int successCount = 0;
            int failCount = 0;
            int repairCount = 0;

            // 2. 逐个核对
            for (TicketTier tier : ticketTiers) {
                try {
                    boolean isConsistent = reconcileStockByTierId(tier.getId());
                    if (isConsistent) {
                        successCount++;
                    } else {
                        repairCount++;
                        log.warn("票档库存不一致已修复: tierId={}, 票档名称={}",
                                tier.getId(), tier.getTierName());
                    }
                } catch (Exception e) {
                    failCount++;
                    log.error("核对票档库存异常: tierId={}", tier.getId(), e);
                }
            }

            long cost = System.currentTimeMillis() - startTime;
            log.info("库存核对完成 - 总票档数: {}, 一致: {}, 已修复: {}, 失败: {}, 耗时: {}ms",
                    ticketTiers.size(), successCount, repairCount, failCount, cost);

        } catch (Exception e) {
            log.error("库存核对任务异常", e);
        }
    }

    @Override
    public boolean reconcileStockByTierId(Long tierId) {
        String stockKey = SeckillConstant.STOCK_KEY_PREFIX + tierId;

        try {
            // 1. 获取Redis库存
            Object redisStockObj = redisTemplate.opsForValue().get(stockKey);
            if (redisStockObj == null) {
                log.warn("Redis库存不存在，可能未初始化: tierId={}", tierId);
                return false;
            }

            Integer redisStock;
            if (redisStockObj instanceof Integer) {
                redisStock = (Integer) redisStockObj;
            } else if (redisStockObj instanceof String) {
                redisStock = Integer.parseInt((String) redisStockObj);
            } else {
                log.error("Redis库存类型异常: tierId={}, type={}",
                        tierId, redisStockObj.getClass().getName());
                return false;
            }

            // 2. 获取数据库库存
            TicketTier ticketTier = ticketTierMapper.selectById(tierId);
            if (ticketTier == null) {
                log.warn("票档不存在: tierId={}", tierId);
                return false;
            }
            Integer dbStock = ticketTier.getAvailableStock();

            // 3. 比较
            if (redisStock.equals(dbStock)) {
                log.debug("库存一致: tierId={}, stock={}", tierId, dbStock);
                return true;
            }

            // 4. 不一致，以数据库为准修复Redis
            log.warn("库存不一致 - tierId={}, Redis库存: {}, 数据库库存: {}, 以数据库为准修复",
                    tierId, redisStock, dbStock);

            redisTemplate.opsForValue().set(stockKey, dbStock);

            // 记录修复日志（可选，用于追踪）
            logRepair(tierId, redisStock, dbStock);

            return false;

        } catch (Exception e) {
            log.error("核对库存异常: tierId={}", tierId, e);
            throw e;
        }
    }

    /**
     * 记录修复日志
     */
    private void logRepair(Long tierId, Integer oldStock, Integer newStock) {
        // 可以记录到数据库表 stock_reconciliation_log
        log.info("库存修复记录 - tierId: {}, 原Redis库存: {}, 修复为: {}",
                tierId, oldStock, newStock);

        // 如果需要持久化，可以在这里插入数据库
        // reconciliationLogMapper.insert(tierId, oldStock, newStock);
    }
}