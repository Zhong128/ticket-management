// org/example/ticketmanagement/util/RedissonLockUtil.java
package org.example.ticketmanagement.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.SeckillConstant;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonLockUtil {

    private final RedissonClient redissonClient;

    /**
     * 获取锁对象
     */
    public RLock getLock(String lockKey) {
        return redissonClient.getLock(SeckillConstant.LOCK_KEY_PREFIX + lockKey);
    }

    /**
     * 尝试获取锁（非阻塞）
     */
    public boolean tryLock(String lockKey, long waitTime, long leaseTime) {
        RLock lock = getLock(lockKey);
        try {
            return lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("获取锁失败, lockKey: {}", lockKey, e);
            return false;
        }
    }

    /**
     * 释放锁
     */
    public void unlock(String lockKey) {
        RLock lock = getLock(lockKey);
        if (lock.isLocked() && lock.isHeldByCurrentThread()) {
            try {
                lock.unlock();
                log.debug("释放锁成功, lockKey: {}", lockKey);
            } catch (Exception e) {
                log.error("释放锁失败, lockKey: {}", lockKey, e);
            }
        }
    }

    /**
     * 执行带锁的操作（有返回值）
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, Supplier<T> supplier) {
        RLock lock = getLock(lockKey);
        try {
            // 尝试获取锁
            if (lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS)) {
                try {
                    return supplier.get();
                } finally {
                    if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                log.warn("获取锁超时, lockKey: {}, waitTime: {}s", lockKey, waitTime);
                throw new RuntimeException("系统繁忙，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取锁被中断, lockKey: {}", lockKey, e);
            throw new RuntimeException("系统繁忙，请稍后重试");
        } catch (Exception e) {
            log.error("执行带锁操作异常, lockKey: {}", lockKey, e);
            throw new RuntimeException("系统繁忙，请稍后重试");
        }
    }
}