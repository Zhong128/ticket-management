package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import org.example.ticketmanagement.exception.OrderCreationDeferredException;
import org.example.ticketmanagement.util.RedissonLockUtil;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 按票档协调异步建单，锁等待必须位于数据库事务之外。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderCreationCoordinator {

    private final RedissonLockUtil locks;

    @Value("${seckill.order.coordination-enabled:true}")
    private boolean enabled = true;

    @Value("${seckill.order.lock-wait-millis:2000}")
    private long waitMillis = 2000;

    @Autowired(required = false)
    private MeterRegistry metrics;

    public <T> T execute(long tierId, Supplier<T> business) {
        // 禁止外层事务携带连接等待Redis；消费者和恢复入口均应在事务外调用。
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new OrderCreationDeferredException("建单协调必须在数据库事务外执行");
        }
        // 关闭协调仅用于对照；数据库条件更新仍是最终库存防线。
        if (!enabled) {
            return business.get();
        }
        if (waitMillis < 0) {
            throw new IllegalStateException("建单锁等待时间不能为负数");
        }

        // 独立票档键支持多实例协调；不设置固定租期，由watchdog续期。
        RLock lock;
        boolean acquired;
        long started = System.nanoTime();
        try {
            lock = locks.getLock("order:tier:" + tierId);
            acquired = lock.tryLock(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new OrderCreationDeferredException("等待建单协调锁被中断", interrupted);
        } catch (RuntimeException unavailable) {
            throw new OrderCreationDeferredException("建单协调暂不可用", unavailable);
        } finally {
            if (metrics != null) {
                metrics.timer("seckill.order.lock.wait")
                        .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            }
        }
        if (!acquired) {
            if (metrics != null) {
                metrics.counter("seckill.order.lock.busy").increment();
            }
            throw new OrderCreationDeferredException("该票档正在建单，等待持久化重试");
        }

        // 调用方在取得锁后才开启事务，提交或回滚完成后才能释放锁。
        try {
            return business.get();
        } finally {
            try {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            } catch (RuntimeException releaseFailure) {
                // 不把已提交的建单改报为业务失败；释放异常需告警核查，禁止强解其他持有者。
                if (metrics != null) {
                    metrics.counter("seckill.order.lock.release.failures").increment();
                }
                log.error("建单协调锁释放异常，请核查Redis连接及租约 tierId={}", tierId, releaseFailure);
            }
        }
    }
}
