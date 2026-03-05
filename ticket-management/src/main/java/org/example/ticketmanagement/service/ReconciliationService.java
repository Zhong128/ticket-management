package org.example.ticketmanagement.service;

public interface ReconciliationService {
    /**
     * 核对所有票档的Redis库存与数据库库存
     */
    void reconcileAllStock();

    /**
     * 核对指定票档的库存
     */
    boolean reconcileStockByTierId(Long tierId);
}