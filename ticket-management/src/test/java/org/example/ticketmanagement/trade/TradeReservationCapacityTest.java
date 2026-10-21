package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.mapper.TradeCoreMapper;
import org.example.ticketmanagement.trade.core.TradeOperationsImpl;
import org.example.ticketmanagement.trade.messaging.OutboxStore;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeReservationCapacityTest {

    @Test
    void limitsConcurrentReservationTransactionsToLeaveConnectionsForMessaging() throws Exception {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch sixEntered = new CountDownLatch(6);
        CountDownLatch release = new CountDownLatch(1);
        when(manager.getTransaction(any())).thenAnswer(call -> {
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            sixEntered.countDown();
            release.await(10, TimeUnit.SECONDS);
            return new SimpleTransactionStatus();
        });

        TradeOperationsImpl core = new TradeOperationsImpl(mock(TradeCoreMapper.class), manager,
                mock(OutboxStore.class), Clock.systemUTC(), true);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> requests = new ArrayList<>();

        try (var workers = Executors.newFixedThreadPool(20)) {
            for (int i = 0; i < 20; i++) {
                long userId = i + 1L;
                requests.add(workers.submit(() -> {
                    start.await();
                    try {
                        core.reserve(userId, 1, 1, "capacity-" + userId);
                    } catch (RuntimeException ignored) {
                        // Mapper 替身只用于观察进入事务的并发数。
                    }
                    return null;
                }));
            }

            // 持有事务入口，确认抢票请求不会占满全部数据库连接。
            start.countDown();
            try {
                assertTrue(sixEntered.await(5, TimeUnit.SECONDS));
                Thread.sleep(200);
                assertTrue(maximum.get() <= 6, "同时进入抢票事务的请求数为 " + maximum.get());
            } finally {
                release.countDown();
            }

            for (Future<?> request : requests) {
                request.get(10, TimeUnit.SECONDS);
            }
        }
    }
}
