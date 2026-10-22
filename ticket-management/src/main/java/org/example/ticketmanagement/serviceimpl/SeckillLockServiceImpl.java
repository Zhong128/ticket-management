// org/example/ticketmanagement/service/lock/impl/SeckillLockServiceImpl.java
package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.service.SeckillLockService;
import org.example.ticketmanagement.util.RedissonLockUtil;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillLockServiceImpl implements SeckillLockService {

    private final RedissonLockUtil redissonLockUtil;

    @Value("${seckill.lock.wait-millis:200}")
    private long waitMillis;


    /**
     * 获取用户锁的 key
     */
    private String getUserLockKey(Long userId, Long tierId) {
        return "user:" + userId + ":tier:" + tierId;
    }

    /**
     * 获取票档锁的 key
     */
    private String getTierLockKey(Long tierId) {
        return "tier:" + tierId;
    }

    /**
     * 获取分段锁的 key（关键修改：基于用户 ID 分段）
     */
    private String getSegmentLockKey(Long userId, Long tierId, int segmentCount) {
        int segment = (int) (userId % segmentCount);
        return "tier:" + tierId + ":segment:" + segment;
    }

    @Override
    public <T> T executeWithUserLock(Long userId, Long tierId, Supplier<T> supplier) {
        String lockKey = getUserLockKey(userId, tierId);
        return redissonLockUtil.executeWithLock(lockKey, 2, 3, supplier); // 等待 2 秒，持有 3 秒
    }

    @Override
    public <T> T executeWithTierLock(Long tierId, Supplier<T> supplier) {
        String lockKey = getTierLockKey(tierId);
        return redissonLockUtil.executeWithLock(lockKey, 3, 5, supplier); // 等待 3 秒，持有 5 秒
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

    /**
     * 执行带分段锁的操作（推荐方案：基于用户 ID 分段，缩短持有时间）
     */
    public <T> T executeWithSegmentLock(Long userId, Long tierId, int segmentCount, Supplier<T> supplier) {
        if (segmentCount < 1) {
            throw new IllegalArgumentException("分段数必须大于零");
        }
        String lockKey = getSegmentLockKey(userId, tierId, segmentCount);
        log.debug("使用分段锁：userId={}, tierId={}, segmentCount={}, lockKey={}", 
                 userId, tierId, segmentCount, lockKey);
        return redissonLockUtil.executeWithWatchdog(lockKey, waitMillis, supplier);
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
            log.error("tryLockWithWait 操作异常，lockKey: {}", lockKey, e);
            return null;
        }
    }
}
