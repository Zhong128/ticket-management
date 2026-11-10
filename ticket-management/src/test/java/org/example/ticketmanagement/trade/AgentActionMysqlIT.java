package org.example.ticketmanagement.trade;

import org.apache.ibatis.session.Configuration;
import org.example.ticketmanagement.exception.AgentRequestException;
import org.example.ticketmanagement.mapper.AgentActionMapper;
import org.example.ticketmanagement.mapper.AgentConversationMapper;
import org.example.ticketmanagement.pojo.AgentAction;
import org.example.ticketmanagement.serviceimpl.AgentActionStore;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 一次性MySQL覆盖真实Mapper、行锁、跨会话幂等及清空迟到写回，不使用原数据卷。 */
class AgentActionMysqlIT {

    @Test
    void migrationClaimConcurrencyOwnershipExpiryAndErasureUseRealMysql() throws Exception {
        try (var mysql = new MySQLContainer<>("mysql:8.0")) {
            mysql.start();
            var source = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            Flyway.configure().dataSource(source).locations("classpath:db/migration").target("9").load().migrate();
            JdbcTemplate db = new JdbcTemplate(source);
            long events = db.queryForObject("SELECT COUNT(*) FROM event", Long.class);
            Flyway.configure().dataSource(source).locations("classpath:db/migration").target("10").load().migrate();
            assertEquals(events, db.queryForObject("SELECT COUNT(*) FROM event", Long.class));
            Configuration config = new Configuration();
            config.setMapUnderscoreToCamelCase(true);
            config.addMapper(AgentConversationMapper.class);
            config.addMapper(AgentActionMapper.class);
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setConfiguration(config);
            factory.setDataSource(source);
            SqlSessionTemplate template = new SqlSessionTemplate(factory.getObject());
            var conversations = template.getMapper(AgentConversationMapper.class);
            var actions = template.getMapper(AgentActionMapper.class);
            MutableClock clock = new MutableClock();
            DataSourceTransactionManager tx = new DataSourceTransactionManager(source);
            AgentConversationStore chat = proxy(new AgentConversationStore(conversations, clock), tx);
            AgentActionStore store = proxy(new AgentActionStore(actions, conversations, clock), tx);

            var first = chat.create(7, "确认卡片");
            var second = chat.create(7, "第二会话");
            var run = chat.start(7, first.getId(), "purchase", "买两张", 60).run();
            assertThrows(AgentRequestException.class, () -> store.create(8, run.getId(), draft(10)));
            AgentAction card = store.create(7, run.getId(), draft(10));
            assertEquals(card.getId(), store.create(7, run.getId(), draft(10)).getId());
            assertThrows(AgentRequestException.class, () -> store.claim(7, card.getId()));
            assertEquals("PENDING", actions.find(card.getId()).getStatus());
            assertThrows(AgentRequestException.class, () -> store.get(8, card.getId()));
            assertThrows(AgentRequestException.class, () -> store.list(8, first.getId()));
            assertTrue(chat.complete(7, run.getId(), "SUCCEEDED", "请核对卡片", null, null));

            // 两个并发确认只有一个取得执行权，固定幂等键不变化。
            AgentAction executing;
            try (var executor = Executors.newFixedThreadPool(2)) {
                CountDownLatch go = new CountDownLatch(1);
                var a = executor.submit(() -> { go.await(); return store.claim(7, card.getId()); });
                var b = executor.submit(() -> { go.await(); return store.claim(7, card.getId()); });
                go.countDown();
                var ra = a.get(15, TimeUnit.SECONDS);
                var rb = b.get(15, TimeUnit.SECONDS);
                assertNotEquals(ra.execute(), rb.execute());
                executing = ra.execute() ? ra.action() : rb.action();
                assertEquals(card.getRequestKey(), executing.getRequestKey());
                assertEquals("EXECUTING", executing.getStatus());
            }
            var otherRun = chat.start(7, second.getId(), "other", "再买", 60).run();
            assertThrows(AgentRequestException.class, () -> store.create(7, otherRun.getId(), draft(10)));
            store.result(executing, "UNKNOWN", "UNKNOWN", null, "原结果未知");
            assertThrows(AgentRequestException.class, () -> store.create(7, otherRun.getId(), draft(10)));
            AgentAction unresolved = store.unresolved(7, "PURCHASE", 10);
            assertNotNull(unresolved);
            chat.clear(7, first.getId());
            assertTrue(store.list(7, first.getId()).isEmpty());
            assertThrows(AgentRequestException.class, () -> store.get(7, card.getId()));

            // 清空不破坏已确认交易的恢复，但迟到结果不能恢复展示内容。
            store.result(unresolved, "SUCCEEDED", "ORDER_CREATED", 99L, "不得重新出现的文案");
            AgentAction erased = actions.find(card.getId());
            assertTrue(erased.isContentErased());
            assertEquals("SUCCEEDED", erased.getStatus());
            assertNull(erased.getTitle());
            assertNull(erased.getEventName());
            assertNull(erased.getMessage());
            assertEquals(99L, erased.getOrderId());
            assertNull(store.unresolved(7, "PURCHASE", 10));
            assertNotNull(store.create(7, otherRun.getId(), draft(10)));
            chat.complete(7, otherRun.getId(), "FAILED", null, "MODEL_FAILED", "失败");
            AgentAction failed = store.list(7, second.getId()).getFirst();
            assertFalse(store.claim(7, failed.getId()).execute());
            assertEquals("EXPIRED", store.get(7, failed.getId()).getStatus());

            // 同一运行的新参数生成新ID；旧卡片和用户放弃卡片都不能执行。
            var replaceRun = chat.start(7, first.getId(), "replace", "换票档", 60).run();
            AgentAction old = store.create(7, replaceRun.getId(), draft(20));
            AgentAction replacement = store.create(7, replaceRun.getId(), draft(21));
            assertNotEquals(old.getId(), replacement.getId());
            assertEquals("EXPIRED", store.get(7, old.getId()).getStatus());
            chat.complete(7, replaceRun.getId(), "SUCCEEDED", "新卡片", null, null);
            assertFalse(store.claim(7, old.getId()).execute());
            assertEquals("DISMISSED", store.dismiss(7, replacement.getId()).getStatus());
            assertFalse(store.claim(7, replacement.getId()).execute());

            var expiryRun = chat.start(7, first.getId(), "expiry", "过期", 60).run();
            AgentAction expiry = store.create(7, expiryRun.getId(), draft(30));
            chat.complete(7, expiryRun.getId(), "SUCCEEDED", "待确认", null, null);
            clock.advance(301);
            assertFalse(store.claim(7, expiry.getId()).execute());
            assertEquals("EXPIRED", store.get(7, expiry.getId()).getStatus());

            // 确认与清空使用同一会话锁；最多确认一次，擦除标记不会丢失。
            var raceRun = chat.start(7, first.getId(), "race", "竞态", 60).run();
            AgentAction race = store.create(7, raceRun.getId(), draft(40));
            chat.complete(7, raceRun.getId(), "SUCCEEDED", "待确认", null, null);
            try (var executor = Executors.newFixedThreadPool(2)) {
                CountDownLatch go = new CountDownLatch(1);
                var confirm = executor.submit(() -> {
                    go.await();
                    try {
                        return store.claim(7, race.getId()).execute();
                    } catch (AgentRequestException cleared) {
                        assertEquals(404, cleared.getCode());
                        return false;
                    }
                });
                var clear = executor.submit(() -> { go.await(); chat.clear(7, first.getId()); return true; });
                go.countDown();
                boolean confirmed = confirm.get(15, TimeUnit.SECONDS);
                assertTrue(clear.get(15, TimeUnit.SECONDS));
                AgentAction row = actions.find(race.getId());
                assertTrue(row.isContentErased());
                assertEquals(confirmed ? "EXECUTING" : "EXPIRED", row.getStatus());
                assertNull(row.getEventName());
            }

            // 已删除会话的未解决最小标识仍可内部查询，不对用户返回快照。
            AgentAction remaining = store.unresolved(7, "PURCHASE", 40);
            if (remaining != null) {
                chat.delete(7, first.getId());
                assertNotNull(store.unresolved(7, "PURCHASE", 40));
                store.result(remaining, "REJECTED", "REJECTED", null, "仍不得恢复");
                assertNull(store.unresolved(7, "PURCHASE", 40));
                assertNull(actions.find(remaining.getId()).getMessage());
            }

            // 两个会话同时为同目标提草稿，目标guard使最终只有一张可确认卡片。
            var leftChat = chat.create(7, "并发草稿一");
            var rightChat = chat.create(7, "并发草稿二");
            var leftRun = chat.start(7, leftChat.getId(), "left", "选择票档", 60).run();
            var rightRun = chat.start(7, rightChat.getId(), "right", "选择同一票档", 60).run();
            try (var executor = Executors.newFixedThreadPool(2)) {
                CountDownLatch go = new CountDownLatch(1);
                var left = executor.submit(() -> { go.await(); return store.create(7, leftRun.getId(), draft(55)); });
                var right = executor.submit(() -> { go.await(); return store.create(7, rightRun.getId(), draft(55)); });
                go.countDown();
                AgentAction a = left.get(15, TimeUnit.SECONDS);
                AgentAction b = right.get(15, TimeUnit.SECONDS);
                assertNotEquals(a.getId(), b.getId());
                assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM agent_action WHERE target_id=55 AND status='PENDING'", Integer.class));
                chat.complete(7, leftRun.getId(), "SUCCEEDED", "卡片一", null, null);
                chat.complete(7, rightRun.getId(), "SUCCEEDED", "卡片二", null, null);
                assertNotEquals(store.claim(7, a.getId()).execute(), store.claim(7, b.getId()).execute());
            }

            // 受理点明确拒绝（如草稿后售罄）可以生成新草稿，区别于UNKNOWN永久保留原键。
            var rejectedChat = chat.create(7, "确定拒绝后重选");
            var rejectedRun = chat.start(7, rejectedChat.getId(), "rejected", "购买", 60).run();
            AgentAction rejected = store.create(7, rejectedRun.getId(), draft(60));
            chat.complete(7, rejectedRun.getId(), "SUCCEEDED", "请确认", null, null);
            AgentAction claimed = store.claim(7, rejected.getId()).action();
            store.result(claimed, "REJECTED", "REJECTED", null, "明确未受理");
            var retryRun = chat.start(7, rejectedChat.getId(), "retry", "重新选择", 60).run();
            AgentAction retry = store.create(7, retryRun.getId(), draft(60));
            assertNotEquals(rejected.getId(), retry.getId());
            assertNotEquals(rejected.getRequestKey(), retry.getRequestKey());
            assertEquals("PENDING", retry.getStatus());
        }
    }

    private AgentAction draft(long target) {
        AgentAction action = new AgentAction();
        action.setType("PURCHASE");
        action.setTargetId(target);
        action.setQuantity(2);
        action.setUnitPrice(new BigDecimal("100.00"));
        action.setTotalAmount(new BigDecimal("200.00"));
        action.setTitle("确认申请");
        action.setEventName("隔离演出");
        action.setSessionName("晚场");
        action.setTierName("看台");
        return action;
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target, DataSourceTransactionManager tx) {
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(tx, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }

    private static class MutableClock extends Clock {

        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-28T01:00:00Z"));

        void advance(long seconds) {
            now.updateAndGet(value -> value.plusSeconds(seconds));
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
            return now.get();
        }
    }
}
