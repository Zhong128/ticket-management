// org/example/ticketmanagement/service/StockService.java
package org.example.ticketmanagement.service;

/**
 * 库存服务接口
 */
public interface StockService {

    /**
     * 初始化库存到Redis
     * @param tierId 票档ID
     * @param totalStock 总库存
     */
    void initStock(Long tierId, Integer totalStock);

    /**
     * 获取库存
     * @param tierId 票档ID
     * @return 当前库存
     */
    Integer getStock(Long tierId);

    /**
     * 检查库存是否充足
     * @param tierId 票档ID
     * @param quantity 需要数量
     * @return 是否充足
     */
    boolean hasEnoughStock(Long tierId, Integer quantity);

    /**
     * 检查用户是否已抢过该票档
     * @param userId 用户ID
     * @param tierId 票档ID
     * @return 是否已抢过
     */
    boolean hasUserSeckilled(Long userId, Long tierId);

}
