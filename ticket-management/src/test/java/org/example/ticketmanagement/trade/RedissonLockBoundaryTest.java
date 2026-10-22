package org.example.ticketmanagement.trade;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.ticketmanagement.util.RedissonLockUtil;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedissonLockBoundaryTest {

    @Test
    void timeoutRecordsContentionAndNeverUnlocksAnotherOwner() throws Exception {
        RLock lock = mock(RLock.class);
        RedissonClient client = mock(RedissonClient.class);
        when(client.getLock(anyString())).thenReturn(lock);
        var util = new RedissonLockUtil(client);
        var metrics = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(util, "metrics", metrics);
        assertThrows(IllegalStateException.class, () -> util.executeWithWatchdog("segment", 200, () -> true));
        assertEquals(1, metrics.get("seckill.lock.wait").timer().count());
        assertEquals(1, metrics.get("seckill.lock.busy").counter().count());
        verify(lock, never()).unlock();
        verify(lock).tryLock(200, TimeUnit.MILLISECONDS);
    }

    @Test
    void interruptedWaitPreservesFlagAndDoesNotRunBusiness() throws Exception {
        RLock lock = mock(RLock.class);
        RedissonClient client = mock(RedissonClient.class);
        when(client.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());
        try {
            assertThrows(IllegalStateException.class,
                    () -> new RedissonLockUtil(client).executeWithWatchdog("segment", 200, () -> fail("业务不应运行")));
            assertTrue(Thread.currentThread().isInterrupted());
            verify(lock, never()).unlock();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void businessFailurePropagatesAndReleasesOnlyCurrentOwner() throws Exception {
        RLock lock = mock(RLock.class);
        RedissonClient client = mock(RedissonClient.class);
        when(client.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        var failure = new IllegalArgumentException("参数冲突");
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> new RedissonLockUtil(client).executeWithWatchdog("segment", 200, () -> {
                    throw failure;
                })));
        verify(lock).unlock();
    }
}
