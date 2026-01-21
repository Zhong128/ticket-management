// org/example/ticketmanagement/service/lock/SeckillLockService.java
package org.example.ticketmanagement.service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 抢票专用锁服务接口
 */
public interface SeckillLockService {

    /**
     * 执行用户级别的锁操作
     * @param userId 用户ID
     * @param tierId 票档ID
     * @param supplier 要执行的操作
     * @param <T> 返回值类型
     * @return 操作结果
     */
    <T> T executeWithUserLock(Long userId, Long tierId, Supplier<T> supplier);

    /**
     * 执行票档级别的锁操作
     * @param tierId 票档ID
     * @param supplier 要执行的操作
     * @param <T> 返回值类型
     * @return 操作结果
     */
    <T> T executeWithTierLock(Long tierId, Supplier<T> supplier);

    /**
     * 尝试获取票档锁（非阻塞）
     * @param tierId 票档ID
     * @param leaseTime 锁持有时间（秒）
     * @return 是否获取成功
     */
    boolean tryTierLock(Long tierId, long leaseTime);

    /**
     * 释放票档锁
     * @param tierId 票档ID
     */
    void unlockTier(Long tierId);

    /**
     * 尝试获取用户锁（非阻塞）
     * @param userId 用户ID
     * @param tierId 票档ID
     * @param leaseTime 锁持有时间（秒）
     * @return 是否获取成功
     */
    boolean tryUserLock(Long userId, Long tierId, long leaseTime);

    /**
     * 释放用户锁
     * @param userId 用户ID
     * @param tierId 票档ID
     */
    void unlockUser(Long userId, Long tierId);

    /**
     * 执行分段锁操作（减少锁竞争）
     * @param tierId 票档ID
     * @param segmentCount 分段数量
     * @param supplier 要执行的操作
     * @param <T> 返回值类型
     * @return 操作结果
     */
    <T> T executeWithSegmentLock(Long tierId, int segmentCount, Supplier<T> supplier);

    /**
     * 等待锁并执行操作（可设置超时）
     * @param lockKey 锁的key
     * @param waitTime 等待时间（秒）
     * @param leaseTime 持有时间（秒）
     * @param supplier 要执行的操作
     * @param <T> 返回值类型
     * @return 操作结果，如果获取锁失败返回null
     */
    <T> T tryLockWithWait(String lockKey, long waitTime, long leaseTime, Supplier<T> supplier);
}