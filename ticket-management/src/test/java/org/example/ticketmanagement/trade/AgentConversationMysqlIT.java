package org.example.ticketmanagement.trade;

import org.apache.ibatis.session.Configuration;
import org.example.ticketmanagement.exception.AgentRequestException;
import org.example.ticketmanagement.dto.AgentCitation;
import org.example.ticketmanagement.mapper.AgentConversationMapper;
import org.example.ticketmanagement.serviceimpl.AgentConversationStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.containers.MySQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 所有DDL、并发和真实Mapper测试均在无宿主数据卷的一次性MySQL中执行。 */
class AgentConversationMysqlIT {

    @Test
    void migrationOwnershipIdempotencyDeadlinesAndRestartRecoveryUseRealMysql() throws Exception {
        try (var mysql = new MySQLContainer<>("mysql:8.0")) {
            mysql.start();
            var source = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            Flyway.configure().dataSource(source).locations("classpath:db/migration").target("6").load().migrate();
            JdbcTemplate db = new JdbcTemplate(source);
            long oldEvents = db.queryForObject("SELECT COUNT(*) FROM event", Long.class);
            Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
            assertEquals(oldEvents, db.queryForObject("SELECT COUNT(*) FROM event", Long.class));
            Configuration configuration = new Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(AgentConversationMapper.class);
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setConfiguration(configuration);
            var template = new SqlSessionTemplate(factory.getObject());
            AgentConversationMapper mapper = template.getMapper(AgentConversationMapper.class);
            MutableClock clock = new MutableClock();
            AgentConversationStore store = transactional(mapper, source, clock);

            var first = store.create(91L, "北京演出");
            var other = store.create(92L, "其他用户");
            assertEquals(1L, store.list(91L, 1, 20).getTotal());
            assertEquals(first.getId(), store.get(91L, first.getId()).getId());
            assertThrows(AgentRequestException.class, () -> store.get(92L, first.getId()));
            assertThrows(AgentRequestException.class, () -> store.messages(92L, first.getId(), 1, 50));
            var start = store.start(91L, first.getId(), "client-1", "查询北京演出", 60);
            assertTrue(start.created());
            assertEquals(clock.instant().plusSeconds(60).toEpochMilli(), start.run().getDeadlineEpochMillis());
            assertFalse(store.start(91L, first.getId(), "client-1", "查询北京演出", 60).created());
            assertThrows(AgentRequestException.class,
                    () -> store.start(91L, first.getId(), "client-1", "不同问题", 60));
            assertThrows(AgentRequestException.class,
                    () -> store.start(91L, first.getId(), "client-2", "第二个运行", 60));
            assertThrows(AgentRequestException.class, () -> store.clear(91L, first.getId()));
            assertThrows(AgentRequestException.class, () -> store.delete(91L, first.getId()));
            assertThrows(AgentRequestException.class, () -> store.getRun(92L, start.run().getId()));
            var citations = List.of(new AgentCitation("KB-ORD-001", "KB-ORDERS", "受理与订单", 2,
                    "2026-10-27", "/support/knowledge/KB-ORD-001?version=2"));
            assertTrue(store.recordCitations(start.run().getId(), citations));
            assertTrue(store.complete(91L, start.run().getId(), "SUCCEEDED", "查到一场演出", null, null));
            assertFalse(store.complete(91L, start.run().getId(), "SUCCEEDED", "重复回复", null, null));
            assertEquals(2, store.messages(91L, first.getId(), 1, 50).getRecords().size());
            assertEquals(citations, store.getRun(91L, start.run().getId()).getCitations());
            assertEquals(citations, store.messages(91L, first.getId(), 1, 50).getRecords().getLast().getCitations());
            assertEquals(2, store.context(91L, first.getId()).size());
            assertEquals("查询北京演出", store.context(91L, first.getId()).getFirst().getText());

            var pending = store.start(91L, first.getId(), "pending", "重启前问题", 60);
            clock.advanceSeconds(61);
            AgentConversationStore restarted = transactional(mapper, source, clock);
            assertEquals("TIMED_OUT", restarted.getRun(91L, pending.run().getId()).getStatus());
            assertFalse(store.complete(91L, pending.run().getId(), "SUCCEEDED", "迟到回复", null, null));
            assertEquals(2, restarted.context(91L, first.getId()).size());
            assertNull(restarted.list(91L, 1, 20).getRecords().getFirst().getActiveRunId());
            store.rename(91L, first.getId(), "重命名");
            assertEquals("重命名", store.list(91L, 1, 20).getRecords().getFirst().getTitle());

            // 先读旧运行建立RR快照，再等待会话锁，不能把随后提交的新运行当成不存在。
            AgentConversationMapper paused = spy(mapper);
            CountDownLatch readBeforeLock = new CountDownLatch(1);
            CountDownLatch newRunCommitted = new CountDownLatch(1);
            doAnswer(invocation -> {
                readBeforeLock.countDown();
                assertTrue(newRunCommitted.await(10, TimeUnit.SECONDS));
                return invocation.callRealMethod();
            }).when(paused).lockConversation(first.getId(), 91L);
            AgentConversationStore observer = transactional(paused, source, clock);
            try (var executor = Executors.newSingleThreadExecutor()) {
                var observed = executor.submit(() -> observer.getRun(91L, start.run().getId()));
                assertTrue(readBeforeLock.await(10, TimeUnit.SECONDS));
                var active = store.start(91L, first.getId(), "new-during-observation", "新运行", 60);
                newRunCommitted.countDown();
                observed.get(10, TimeUnit.SECONDS);
                assertEquals(active.run().getId(), mapper.conversation(first.getId(), 91L).getActiveRunId());
                assertThrows(AgentRequestException.class,
                        () -> store.start(91L, first.getId(), "must-not-start", "并发第二运行", 60));
                store.complete(91L, active.run().getId(), "SUCCEEDED", "新回复", null, null);
            } finally {
                newRunCommitted.countDown();
            }

            var second = store.create(91L, "并发会话");
            try (var executor = Executors.newFixedThreadPool(2)) {
                CountDownLatch go = new CountDownLatch(1);
                var a = executor.submit(() -> { go.await(); return store.start(91L, second.getId(), "same", "同一请求", 60); });
                var b = executor.submit(() -> { go.await(); return store.start(91L, second.getId(), "same", "同一请求", 60); });
                go.countDown();
                var ra = a.get(10, TimeUnit.SECONDS);
                var rb = b.get(10, TimeUnit.SECONDS);
                assertEquals(ra.run().getId(), rb.run().getId());
                assertNotEquals(ra.created(), rb.created());
                store.complete(91L, ra.run().getId(), "SUCCEEDED", "幂等回复", null, null);
            }
            for (int i = 0; i < 12; i++) {
                var run = store.start(91L, second.getId(), "history-" + i, "问题" + i, 60);
                store.complete(91L, run.run().getId(), "SUCCEEDED", "回答" + i, null, null);
            }
            assertEquals(20, store.context(91L, second.getId()).size());
            assertEquals(10, store.messages(91L, second.getId(), 1, 10).getRecords().size());
            assertTrue(store.messages(91L, second.getId(), 1, 10).getHasNext());
            store.clear(91L, second.getId());
            assertTrue(store.messages(91L, second.getId(), 1, 50).getRecords().isEmpty());
            assertTrue(store.context(91L, second.getId()).isEmpty());
            store.clear(91L, first.getId());
            assertTrue(store.getRun(91L, start.run().getId()).getCitations().isEmpty());
            store.delete(91L, second.getId());
            assertThrows(AgentRequestException.class, () -> store.messages(91L, second.getId(), 1, 50));
            assertEquals(1L, store.list(92L, 1, 20).getTotal());
            assertEquals(other.getId(), store.list(92L, 1, 20).getRecords().getFirst().getId());
        }
    }

    private AgentConversationStore transactional(AgentConversationMapper mapper,
            DriverManagerDataSource source, Clock clock) {
        ProxyFactory proxy = new ProxyFactory(new AgentConversationStore(mapper, clock));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource()));
        return (AgentConversationStore) proxy.getProxy();
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(Instant.parse("2026-10-27T00:00:00Z"));

        void advanceSeconds(long seconds) {
            current.updateAndGet(value -> value.plusSeconds(seconds));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
