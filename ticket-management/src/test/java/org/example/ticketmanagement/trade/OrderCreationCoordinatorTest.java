package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.serviceimpl.OrderCreationCoordinator;
import org.example.ticketmanagement.exception.OrderCreationDeferredException;
import org.example.ticketmanagement.util.RedissonLockUtil;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderCreationCoordinatorTest {

    private final RedissonLockUtil locks = mock(RedissonLockUtil.class);
    private final RLock lock = mock(RLock.class);
    private final OrderCreationCoordinator coordinator = new OrderCreationCoordinator(locks);

    private void ready() throws InterruptedException {
        ReflectionTestUtils.setField(coordinator, "enabled", true);
        ReflectionTestUtils.setField(coordinator, "waitMillis", 2000L);
        when(locks.getLock("order:tier:7")).thenReturn(lock);
        when(lock.tryLock(2000, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void acquiresWatchdogLockBeforeBusinessAndReleasesAfterCommitCallback() throws Exception {
        ready();
        assertEquals(42L, coordinator.execute(7, () -> {
            assertDoesNotThrow(() -> verify(lock).tryLock(2000, TimeUnit.MILLISECONDS));
            verify(lock, never()).unlock();
            return 42L;
        }));
        verify(lock).unlock();
        verify(lock, never()).tryLock(anyLong(), anyLong(), any());
    }

    @Test
    void contentionNeverRunsBusinessOrUnlocksAnotherOwner() throws Exception {
        ready();
        when(lock.tryLock(2000, TimeUnit.MILLISECONDS)).thenReturn(false);
        AtomicBoolean called = new AtomicBoolean();
        assertThrows(OrderCreationDeferredException.class, () -> coordinator.execute(7, () -> {
            called.set(true);
            return 1;
        }));
        assertFalse(called.get());
        verify(lock, never()).unlock();
    }

    @Test
    void interruptionIsPreservedAndNeverRunsBusiness() throws Exception {
        ready();
        when(lock.tryLock(2000, TimeUnit.MILLISECONDS)).thenThrow(new InterruptedException());
        try {
            assertThrows(OrderCreationDeferredException.class, () -> coordinator.execute(7, () -> fail("不得执行建单")));
            assertTrue(Thread.currentThread().isInterrupted());
            verify(lock, never()).unlock();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void businessFailureIsPropagatedAndLostOwnershipNeverUnlocksNewOwner() throws Exception {
        ready();
        RuntimeException failure = new IllegalArgumentException("rollback");
        when(lock.isHeldByCurrentThread()).thenReturn(false);
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> coordinator.execute(7, () -> {
                    throw failure;
                })));
        verify(lock, never()).unlock();
    }

    @Test
    void redisUnavailableDefersWithoutStartingBusiness() throws Exception {
        ready();
        when(lock.tryLock(2000, TimeUnit.MILLISECONDS)).thenThrow(new IllegalStateException("redis unavailable"));
        assertThrows(OrderCreationDeferredException.class, () -> coordinator.execute(7, () -> fail("不得执行建单")));
        verify(lock, never()).unlock();
    }

    @Test
    void ambientTransactionIsRejectedBeforeAnyRedisWait() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(OrderCreationDeferredException.class,
                    () -> coordinator.execute(7, () -> fail("不得执行建单")));
            verifyNoInteractions(locks);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
