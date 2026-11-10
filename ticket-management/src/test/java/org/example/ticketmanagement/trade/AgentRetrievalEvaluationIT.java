package org.example.ticketmanagement.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ticketmanagement.mapper.AgentKnowledgeMapper;
import org.example.ticketmanagement.serviceimpl.AgentKnowledgeService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/** 固定标注集测量真实关键词检索，质量门槛单独报告，不伪装成模型回答评测。 */
class AgentRetrievalEvaluationIT {

    @Test
    void measuresFrozenQueriesAndVerifiesEveryReturnedCitationAgainstIsolatedMysql() throws Exception {
        ObjectMapper json = new ObjectMapper();
        byte[] input;
        String datasetName = System.getProperty("agent.evaluation.dataset", "retrieval-cases.json");
        assertTrue(Set.of("retrieval-cases.json", "retrieval-cases-v2.json").contains(datasetName));
        try (var resource = getClass().getResourceAsStream("/agent-evaluation/" + datasetName)) {
            assertNotNull(resource);
            input = resource.readAllBytes();
        }
        JsonNode dataset = json.readTree(input);
        assertEquals(90, dataset.path("cases").size());
        List<Map<String, Object>> rows = new ArrayList<>();
        int sourceChecks = 0;
        try (var mysql = new MySQLContainer<>("mysql:8.0")) {
            mysql.start();
            var source = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            Flyway.configure().dataSource(source).locations("classpath:db/migration").target("11").load().migrate();
            var db = new JdbcTemplate(source);
            var service = new AgentKnowledgeService(MapperTestSupport.sessions(source).getMapper(AgentKnowledgeMapper.class));
            var currentRows = db.queryForList("""
                    SELECT c.entry_id, c.document_version FROM agent_knowledge_chunk c
                    JOIN agent_knowledge_document d ON d.document_id=c.document_id AND d.version=c.document_version
                    WHERE d.status='ACTIVE' AND c.enabled=1
                        AND d.version=(SELECT MAX(v.version) FROM agent_knowledge_document v WHERE v.document_id=d.document_id)
                    ORDER BY c.entry_id
                    """);
            Map<String, Integer> currentVersions = new LinkedHashMap<>();
            for (var row : currentRows) {
                currentVersions.put(row.get("entry_id").toString(), ((Number) row.get("document_version")).intValue());
            }
            List<String> currentIds = new ArrayList<>(currentVersions.keySet());
            assertEquals(39, currentIds.size());
            for (String group : List.of("title", "paraphrase")) {
                Set<String> annotated = new HashSet<>();
                for (JsonNode sample : dataset.path("cases")) {
                    if (group.equals(sample.path("group").asText())) {
                        assertTrue(annotated.add(sample.path("expectedEntryId").asText()), "重复标注");
                    }
                }
                assertEquals(Set.copyOf(currentIds), annotated, "每个主题都必须保留，不能只选容易命中的题");
            }

            // 预热不计分，后续逐题记录；仅隔离本机样本耗时，不是压测。
            service.search("付款期限");
            Set<String> caseIds = new HashSet<>();
            for (JsonNode sample : dataset.path("cases")) {
                assertTrue(caseIds.add(sample.path("id").asText()), "重复题号");
                String expected = sample.path("expectedEntryId").isNull() ? null : sample.path("expectedEntryId").asText();
                if (expected != null) {
                    assertTrue(currentIds.contains(expected), "标注必须指向现行规则");
                }
                long started = System.nanoTime();
                var matches = service.search(sample.path("query").asText());
                double millis = (System.nanoTime() - started) / 1_000_000.0;
                assertTrue(matches.size() <= 5);
                var ids = matches.stream().map(entry -> entry.getEntryId()).toList();
                assertEquals(ids.size(), Set.copyOf(ids).size(), "不可同时返回重复历史版本");
                var citations = service.citations(matches);
                for (int index = 0; index < matches.size(); index++) {
                    var entry = matches.get(index);
                    var citation = citations.get(index);
                    assertTrue(currentIds.contains(entry.getEntryId()));
                    assertEquals(currentVersions.get(entry.getEntryId()).intValue(), entry.getVersion());
                    assertTrue(entry.isCurrent());
                    assertEquals(entry.getEntryId(), citation.entryId());
                    assertEquals(entry.getVersion(), citation.version());
                    assertEquals("/support/knowledge/" + entry.getEntryId() + "?version=" + entry.getVersion(), citation.url());
                    assertEquals(entry.getAnswer(), service.get(citation.entryId(), citation.version()).getAnswer());
                    sourceChecks++;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", sample.path("id").asText());
                row.put("group", sample.path("group").asText());
                row.put("subgroup", sample.path("subgroup").asText(""));
                row.put("query", sample.path("query").asText());
                row.put("expectedEntryId", expected);
                row.put("returnedEntryIds", ids);
                row.put("rank", expected == null || !ids.contains(expected) ? 0 : ids.indexOf(expected) + 1);
                row.put("empty", matches.isEmpty());
                row.put("elapsedMillis", millis);
                rows.add(row);
            }

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("generatedAt", Instant.now().toString());
            report.put("datasetVersion", dataset.path("version").asInt());
            report.put("datasetSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)));
            report.put("modelMode", "NO_MODEL_RETRIEVAL_ONLY");
            report.put("javaVersion", System.getProperty("java.version"));
            report.put("mysqlVersion", db.queryForObject("SELECT VERSION()", String.class));
            report.put("migrationVersion", 11);
            report.put("currentEntryCount", currentIds.size());
            report.put("documentVersionCount", db.queryForObject("SELECT COUNT(*) FROM agent_knowledge_document", Integer.class));
            report.put("historicalChunkCount", db.queryForObject("SELECT COUNT(*) FROM agent_knowledge_chunk", Integer.class));
            report.put("corpus", db.queryForList("""
                    SELECT document_id, version, content_hash FROM agent_knowledge_document
                    WHERE status='ACTIVE' ORDER BY document_id, version
                    """));
            Map<String, Map<String, Object>> metrics = new LinkedHashMap<>();
            metrics.put("title", metrics(rows, "title", null));
            metrics.put("paraphrase", metrics(rows, "paraphrase", null));
            metrics.put("negative", metrics(rows, "negative", null));
            metrics.put("negativeOrdinary", metrics(rows, "negative", "ordinary"));
            metrics.put("negativeBusinessWord", metrics(rows, "negative", "business_word"));
            report.put("metrics", metrics);
            JsonNode thresholds = dataset.path("thresholds");
            boolean passed = number(metrics.get("paraphrase"), "hit1") >= thresholds.path("paraphraseHit1").asDouble()
                    && number(metrics.get("paraphrase"), "hit5") >= thresholds.path("paraphraseHit5").asDouble()
                    && number(metrics.get("negative"), "emptyRate") >= thresholds.path("negativeEmptyRate").asDouble();
            report.put("thresholds", thresholds);
            report.put("qualityGatePassed", passed);
            report.put("hardConstraintsPassed", true);
            report.put("verifiedCitations", sourceChecks);
            report.put("cases", rows);
            Path output = Path.of(System.getProperty("agent.evaluation.output", "target/agent-evaluation/retrieval.json"))
                    .toAbsolutePath().normalize();
            assertTrue(output.startsWith(Path.of("target").toAbsolutePath().normalize()));
            Files.createDirectories(output.getParent());
            json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
            System.out.println("Agent retrieval measurement: " + output + "; qualityGatePassed=" + passed);
        }
    }

    private Map<String, Object> metrics(List<Map<String, Object>> rows, String group, String subgroup) {
        var selected = rows.stream().filter(row -> group.equals(row.get("group")))
                .filter(row -> subgroup == null || subgroup.equals(row.get("subgroup"))).toList();
        long hit1 = selected.stream().filter(row -> ((Number) row.get("rank")).intValue() == 1).count();
        long hit5 = selected.stream().filter(row -> ((Number) row.get("rank")).intValue() > 0).count();
        long empty = selected.stream().filter(row -> Boolean.TRUE.equals(row.get("empty"))).count();
        double mrr = selected.stream().mapToDouble(row -> {
            int rank = ((Number) row.get("rank")).intValue();
            return rank == 0 ? 0 : 1.0 / rank;
        }).sum() / selected.size();
        return Map.of("cases", selected.size(), "hit1Count", hit1, "hit5Count", hit5,
                "emptyCount", empty, "hit1", hit1 / (double) selected.size(),
                "hit5", hit5 / (double) selected.size(), "mrr5", mrr, "emptyRate", empty / (double) selected.size());
    }

    private double number(Map<String, Object> metric, String key) {
        return ((Number) metric.get(key)).doubleValue();
    }
}
