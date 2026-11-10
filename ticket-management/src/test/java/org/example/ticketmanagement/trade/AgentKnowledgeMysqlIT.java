package org.example.ticketmanagement.trade;

import org.apache.ibatis.session.Configuration;
import org.example.ticketmanagement.exception.AgentRequestException;
import org.example.ticketmanagement.mapper.AgentKnowledgeMapper;
import org.example.ticketmanagement.serviceimpl.AgentKnowledgeService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 规则结构、种子、MySQL评分、版本与停用策略全部验证真实Mapper。 */
class AgentKnowledgeMysqlIT {

    @Test
    void corpusRetrievalVersionsAndDisabledRulesUseRealMysql() throws Exception {
        try (var mysql = new MySQLContainer<>("mysql:8.0")) {
            mysql.start();
            var source = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            Flyway.configure().dataSource(source).locations("classpath:db/migration").target("7").load().migrate();
            JdbcTemplate db = new JdbcTemplate(source);
            long users = db.queryForObject("SELECT COUNT(*) FROM user", Long.class);
            Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
            assertEquals(users, db.queryForObject("SELECT COUNT(*) FROM user", Long.class));
            assertEquals(6, db.queryForObject("SELECT COUNT(*) FROM agent_knowledge_document", Integer.class));
            assertEquals(47, db.queryForObject("SELECT COUNT(*) FROM agent_knowledge_chunk", Integer.class));
            Configuration config = new Configuration();
            config.setMapUnderscoreToCamelCase(true);
            config.addMapper(AgentKnowledgeMapper.class);
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setConfiguration(config);
            var service = new AgentKnowledgeService(new SqlSessionTemplate(factory.getObject()).getMapper(AgentKnowledgeMapper.class));

            assertHit(service, "付款期限多久，刷新会延长吗？", "KB-ORD-004");
            assertHit(service, "资格检查通过是不是已经抢到票", "KB-PUR-003");
            assertHit(service, "默认地址删不掉怎么办", "KB-ACC-004");
            assertHit(service, "取消订单为什么还在确认中", "KB-REF-002");
            assertHit(service, "退款几天到账收手续费吗", "KB-REF-007");
            assertHit(service, "申请编号和订单号是同一个吗", "KB-ORD-007");
            assertTrue(service.search("今天天气怎么样").isEmpty());
            assertTrue(service.search("%_'; DROP TABLE agent_knowledge_chunk; --").isEmpty());
            assertEquals(47, db.queryForObject("SELECT COUNT(*) FROM agent_knowledge_chunk", Integer.class));
            assertHit(service, "确认卡片会锁库存吗", "KB-ACC-009");
            assertHit(service, "卡片结果未知可以重新下单吗", "KB-ACC-010");
            assertHit(service, "工具上限是什么意思", "KB-ACC-008");
            assertEquals(2, service.get("KB-ACC-005", null).getVersion());
            assertTrue(service.get("KB-ACC-005", null).getAnswer().contains("点击确认"));
            assertFalse(service.get("KB-ACC-005", 1).isCurrent());
            var entry = service.get("KB-ORD-004", 2);
            assertTrue(entry.isCurrent());
            assertTrue(entry.getAnswer().contains("30分钟"));
            assertFalse(entry.getAnswer().contains("source-map"));
            assertFalse(entry.getAnswer().contains("serviceimpl"));
            assertFalse(entry.getLiveCheck().isBlank());

            db.update("UPDATE agent_knowledge_document SET status='DISABLED' WHERE document_id='KB-ORDERS'");
            assertTrue(service.search("付款期限多久").stream().noneMatch(row -> row.getDocumentId().equals("KB-ORDERS")));
            assertThrows(AgentRequestException.class, () -> service.get("KB-ORD-004", 2));
            db.update("UPDATE agent_knowledge_document SET status='ACTIVE' WHERE document_id='KB-ORDERS'");
            db.update("""
                    INSERT INTO agent_knowledge_document(document_id, version, title, reviewed_at, source_path, content_hash, status)
                    SELECT document_id, 3, title, reviewed_at, source_path, content_hash, 'DISABLED'
                    FROM agent_knowledge_document WHERE document_id='KB-ORDERS' AND version=2
                    """);
            assertThrows(AgentRequestException.class, () -> service.get("KB-ORD-004", null));
            assertFalse(service.get("KB-ORD-004", 2).isCurrent());
            assertTrue(service.search("付款期限多久").stream().noneMatch(row -> row.getDocumentId().equals("KB-ORDERS")),
                    "新版停用不得悄悄回退旧政策");
        }
    }

    private void assertHit(AgentKnowledgeService service, String query, String expectedId) {
        var results = service.search(query);
        assertFalse(results.isEmpty(), query);
        assertTrue(results.size() <= 5);
        assertTrue(results.stream().anyMatch(row -> expectedId.equals(row.getEntryId())),
                query + " -> " + results.stream().map(row -> row.getEntryId()).toList());
    }
}
