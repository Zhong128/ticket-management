package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.mapper.TradeStockMapper;
import org.example.ticketmanagement.service.StockService;
import org.example.ticketmanagement.trade.messaging.StockProjection;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    private final TradeStockMapper stockMapper;
    private final StockProjection projection;

    @Override
    public void initStock(Long tierId, Integer ignored) {
        refresh(tierId);
    }

    public void refresh(Long tierId) {
        Map<String, Object> row = stockMapper.selectStock(tierId);
        if (row == null) {
            throw new IllegalArgumentException("票档不存在");
        }
        try {
            projection.apply(tierId, ((Number) row.get("stock_version")).longValue(),
                    ((Number) row.get("available_stock")).intValue());
        } catch (Exception e) {
            log.warn("Redis stock projection unavailable tier={}, MySQL remains authoritative", tierId);
        }
    }

    /**
     * Legacy mutations are intentionally rejected; reservations own all inventory changes.
     */
    @Override
    public Long decrStock(Long tierId, Integer quantity) {
        throw new UnsupportedOperationException("Use TradeOperations.reserve");
    }

    @Override
    public Long restoreStock(Long tierId, Integer quantity) {
        throw new UnsupportedOperationException("Use transactional reservation release");
    }

    @Override
    public Integer getStock(Long tierId) {
        return stockMapper.selectAvailableStock(tierId);
    }

    @Override
    public boolean hasEnoughStock(Long tierId, Integer quantity) {
        Integer stock = getStock(tierId);
        return stock != null && stock >= quantity;
    }

    @Override
    public boolean hasUserSeckilled(Long userId, Long tierId) {
        return stockMapper.countUserActiveOrders(userId, tierId) > 0;
    }

    @Override
    public void setUserSeckillRecord(Long userId, Long tierId, long expireSeconds) {
        /* purchase_guard owns uniqueness */
    }

    @Override
    public void deleteUserSeckillRecord(Long userId, Long tierId) {
        /* purchase_guard is released in the business transaction */
    }
}
