// org/example/ticketmanagement/service/lock/impl/SeckillLockServiceImpl.java
package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.service.SeckillLockService;
import org.example.ticketmanagement.util.RedissonLockUtil;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillLockServiceImpl implements SeckillLockService {

    private final RedissonLockUtil redissonLockUtil;

    /**
     * 获取用户锁的key
     */
    private String getUserLockKey(Long userId, Long tierId) {
        return "user:" + userId + ":tier:" + tierId;
    }

    /**
     * 获取票档锁的key
     */
    private String getTierLockKey(Long tierId) {
        return "tier:" + tierId;
    }

    /**
     * 获取分段锁的key
     */
    private String getSegmentLockKey(Long tierId, int segmentCount) {
        int segment = (int) (tierId % segmentCount);
        return "tier:segment:" + segment;
    }

    @Override
    public <T> T executeWithUserLock(Long userId, Long tierId, Supplier<T> supplier) {
        String lockKey = getUserLockKey(userId, tierId);
        return redissonLockUtil.executeWithLock(lockKey, 3, 5, supplier); // 等待3秒，持有5秒
    }

    @Override
    public <T> T executeWithTierLock(Long tierId, Supplier<T> supplier) {
        String lockKey = getTierLockKey(tierId);
        return redissonLockUtil.executeWithLock(lockKey, 5, 10, supplier); // 等待5秒，持有10秒
    }

    @Override
    public boolean tryTierLock(Long tierId, long leaseTime) {
        String lockKey = getTierLockKey(tierId);
        return redissonLockUtil.tryLock(lockKey, 0, leaseTime); // 不等待，立即返回
    }

    @Override
    public void unlockTier(Long tierId) {
        String lockKey = getTierLockKey(tierId);
        redissonLockUtil.unlock(lockKey);
    }

    @Override
    public boolean tryUserLock(Long userId, Long tierId, long leaseTime) {
        String lockKey = getUserLockKey(userId, tierId);
        return redissonLockUtil.tryLock(lockKey, 0, leaseTime); // 不等待，立即返回
    }

    @Override
    public void unlockUser(Long userId, Long tierId) {
        String lockKey = getUserLockKey(userId, tierId);
        redissonLockUtil.unlock(lockKey);
    }

    @Override
    public <T> T executeWithSegmentLock(Long tierId, int segmentCount, Supplier<T> supplier) {
        String lockKey = getSegmentLockKey(tierId, segmentCount);
        return redissonLockUtil.executeWithLock(lockKey, 3, 8, supplier); // 等待3秒，持有8秒
    }

    @Override
    public <T> T tryLockWithWait(String lockKey, long waitTime, long leaseTime, Supplier<T> supplier) {
        try {
            if (redissonLockUtil.tryLock(lockKey, waitTime, leaseTime)) {
                try {
                    return supplier.get();
                } finally {
                    redissonLockUtil.unlock(lockKey);
                }
            }
            return null;
        } catch (Exception e) {
            log.error("tryLockWithWait操作异常, lockKey: {}", lockKey, e);
            return null;
        }
    }
}