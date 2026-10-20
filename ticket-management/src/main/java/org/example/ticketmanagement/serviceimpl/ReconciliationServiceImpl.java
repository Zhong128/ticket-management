package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.SeckillConstant;
import org.example.ticketmanagement.mapper.TradeStockMapper;
import org.example.ticketmanagement.service.ReconciliationService;
import org.example.ticketmanagement.trade.messaging.StockProjection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private final TradeStockMapper stockMapper;
    private final StringRedisTemplate redis;
    private final StockProjection projection;

    @Override
    public void reconcileAllStock() {
        for (Long id : stockMapper.selectTierIds()) {
            try {
                reconcileStockByTierId(id);
            } catch (Exception e) {
                log.warn("Stock projection repair failed tier={}", id, e);
            }
        }
    }

    @Override
    public boolean reconcileStockByTierId(Long tierId) {
        Map<String, Object> row = stockMapper.selectStock(tierId);
        if (row == null) {
            throw new IllegalArgumentException("票档不存在");
        }
        int stock = ((Number) row.get("available_stock")).intValue();
        long version = ((Number) row.get("stock_version")).longValue();
        String key = SeckillConstant.STOCK_KEY_PREFIX + tierId;
        boolean same = Integer.toString(stock).equals(redis.opsForValue().get(key))
                && Long.toString(version).equals(redis.opsForValue().get(key + ":version"));
        projection.apply(tierId, version, stock);
        return same;
    }
}
