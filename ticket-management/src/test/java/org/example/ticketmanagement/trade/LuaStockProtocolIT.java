package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.util.LuaScriptLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.redisson.Redisson;
import org.redisson.config.Config;
import org.example.ticketmanagement.util.RedissonLockUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 Redis 验证配额、限购和预扣状态，不能用 Java 替身代替 Lua 原子行为。
 */
@Testcontainers
class LuaStockProtocolIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static LettuceConnectionFactory connection;
    static StringRedisTemplate redis;
    final LuaScriptLoader scripts = new LuaScriptLoader();
    final List<String> keys = List.of("test:{1}:meta", "test:{1}:quota:1", "test:{1}:users:1",
            "test:{1}:requests:1", "test:{1}:tokens:1", "test:{1}:pending:1");

    @BeforeAll
    static void connect() {
        connection = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connection.afterPropertiesSet();
        connection.start();
        redis = new StringRedisTemplate(connection);
    }

    @AfterAll
    static void disconnect() {
        if (connection != null) {
            connection.destroy();
        }
    }

    @BeforeEach
    void prepareQuota() {
        redis.delete(keys);
        redis.opsForHash().putAll(keys.getFirst(), Map.of("epoch", "1", "ready", "1"));
        redis.opsForValue().set(keys.get(1), "5");
        scripts.init();
    }

    @SuppressWarnings("unchecked")
    List<String> reserve(String user, String key, String quantity, String token) {
        return (List<String>) redis.execute(scripts.getScript("reserveStock.lua"), keys,
                "1", user, key, quantity, token, "1000", "1801000");
    }

    @Test
    void repeatedRequestAndAnotherKeyNeverDeductTwice() {
        assertEquals("OK", reserve("1", "same", "2", "token-1").getFirst());
        assertEquals("EXISTING", reserve("1", "same", "2", "token-2").getFirst());
        assertEquals("DUPLICATE", reserve("1", "another", "2", "token-3").getFirst());
        assertEquals("MISMATCH", reserve("1", "same", "3", "token-4").getFirst());
        assertEquals("3", redis.opsForValue().get(keys.get(1)));
    }

    @Test
    void releaseIsIdempotentAndOldTokenCannotDeleteNewOwner() {
        reserve("1", "old", "2", "old-token");
        assertEquals(1L, redis.execute(scripts.<Long>getScript("releaseReservation.lua"), keys, "1", "old-token"));
        assertEquals(0L, redis.execute(scripts.<Long>getScript("releaseReservation.lua"), keys, "1", "old-token"));
        assertEquals("OK", reserve("1", "new", "1", "new-token").getFirst());
        redis.execute(scripts.getScript("releaseReservation.lua"), keys, "1", "old-token");
        assertEquals("new-token", redis.opsForHash().get(keys.get(2), "1"));
        assertEquals("4", redis.opsForValue().get(keys.get(1)));
    }

    @Test
    void invalidQuantityAndOldEpochCannotChangeQuota() {
        assertEquals("INVALID", reserve("1", "invalid", "-1", "bad").getFirst());
        redis.opsForHash().put(keys.getFirst(), "epoch", "2");
        assertEquals("NOT_READY", reserve("1", "valid", "2", "old").getFirst());
        assertEquals("5", redis.opsForValue().get(keys.get(1)));
        assertEquals(0L, redis.opsForHash().size(keys.get(4)));
    }

    @Test
    void confirmAfterReleaseCannotResurrectAReservation() {
        reserve("1", "one", "2", "token");
        assertEquals(1L, redis.execute(scripts.<Long>getScript("confirmReservation.lua"), keys, "1", "token"));
        assertEquals(0L, redis.opsForZSet().size(keys.get(5)));
        redis.execute(scripts.getScript("releaseReservation.lua"), keys, "1", "token");
        assertEquals(0L, redis.execute(scripts.<Long>getScript("confirmReservation.lua"), keys, "1", "token"));
        assertEquals("5", redis.opsForValue().get(keys.get(1)));
    }

    @Test
    void watchdogKeepsTheSegmentLockedPastTheOriginalTwoSecondLease() throws Exception {
        Config config = new Config();
        config.setThreads(1);
        config.setNettyThreads(2);
        config.setLockWatchdogTimeout(1500);
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379))
                .setConnectionMinimumIdleSize(1).setConnectionPoolSize(2)
                .setSubscriptionConnectionMinimumIdleSize(1).setSubscriptionConnectionPoolSize(2);
        var first = Redisson.create(config);
        var second = Redisson.create(config);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var holder = pool.submit(() -> new RedissonLockUtil(first).executeWithWatchdog("watchdog-it", 100, () -> {
                entered.countDown();
                try {
                    return release.await(8, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }));
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                Thread.sleep(2300);
                assertThrows(IllegalStateException.class,
                        () -> new RedissonLockUtil(second).executeWithWatchdog("watchdog-it", 50, () -> true));
            } finally {
                release.countDown();
            }
            assertTrue(holder.get(3, TimeUnit.SECONDS));
            assertTrue(new RedissonLockUtil(second).executeWithWatchdog("watchdog-it", 100, () -> true));
        } finally {
            release.countDown();
            first.shutdown();
            second.shutdown();
        }
    }

    @Test
    void missingResourceIsRejectedByTheCentralLoader() {
        assertThrows(IllegalArgumentException.class, () -> scripts.getScript("missing.lua"));
    }
}
