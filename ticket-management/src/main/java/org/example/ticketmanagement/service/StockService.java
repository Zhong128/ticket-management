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
     * 原子扣减库存
     * @param tierId 票档ID
     * @param quantity 扣减数量
     * @return 扣减后的库存，-1表示库存不足
     */
    Long decrStock(Long tierId, Integer quantity);

    /**
     * 原子增加库存
     * @param tierId 票档ID
     * @param quantity 增加数量
     * @return 增加后的库存
     */
    Long restoreStock(Long tierId, Integer quantity);

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

    /**
     * 设置用户抢票记录（防重）
     * @param userId 用户ID
     * @param tierId 票档ID
     * @param expireSeconds 过期时间（秒）
     */
    void setUserSeckillRecord(Long userId, Long tierId, long expireSeconds);

    /**
     * 删除用户抢票记录
     * @param userId 用户ID
     * @param tierId 票档ID
     */
    void deleteUserSeckillRecord(Long userId, Long tierId);
}