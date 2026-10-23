package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.serviceimpl.OrderCreationCoordinator;
import org.example.ticketmanagement.util.RedissonLockUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class OrderCreationLockIT {

    private static RedissonClient first;
    private static RedissonClient second;

    @BeforeAll
    static void connect() {
        first = IsolatedProtocolDatabase.newRedissonClient();
        second = IsolatedProtocolDatabase.newRedissonClient();
    }

    @AfterAll
    static void disconnect() {
        if (first != null) {
            first.shutdown();
        }
        if (second != null) {
            second.shutdown();
        }
    }

    @Test
    void independentNodesSerializeSameTierButOtherTierCanProgress() throws Exception {
        var firstNode = new OrderCreationCoordinator(new RedissonLockUtil(first));
        var secondNode = new OrderCreationCoordinator(new RedissonLockUtil(second));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);

        // 第一个节点持有热点票档时，另一个票档仍应独立执行。
        try (var workers = Executors.newFixedThreadPool(2)) {
            var holder = workers.submit(() -> firstNode.execute(7001, () -> {
                entered.countDown();
                await(release);
                return 1;
            }));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var waiter = workers.submit(() -> secondNode.execute(7001, () -> {
                    secondEntered.countDown();
                    return 2;
                }));
                assertFalse(secondEntered.await(200, TimeUnit.MILLISECONDS));
                assertEquals(3, secondNode.execute(7002, () -> 3));
                release.countDown();
                assertEquals(1, holder.get(5, TimeUnit.SECONDS));
                assertEquals(2, waiter.get(5, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void watchdogRetainsOwnershipBeyondOriginalLeaseThenReleases() throws Exception {
        var firstNode = new OrderCreationCoordinator(new RedissonLockUtil(first));
        var secondLocks = new RedissonLockUtil(second);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var workers = Executors.newSingleThreadExecutor()) {
            var holder = workers.submit(() -> firstNode.execute(7010, () -> {
                entered.countDown();
                await(release);
                return 1;
            }));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                Thread.sleep(2200);
                assertFalse(secondLocks.getLock("order:tier:7010").tryLock(0, TimeUnit.MILLISECONDS));
                release.countDown();
                assertEquals(1, holder.get(5, TimeUnit.SECONDS));
                assertTrue(secondLocks.getLock("order:tier:7010").tryLock(0, TimeUnit.MILLISECONDS));
                secondLocks.unlock("order:tier:7010");
            } finally {
                release.countDown();
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("测试释放信号超时");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
