package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.ticketmanagement.pojo.AgentKnowledgeEntry;

import java.util.List;

@Mapper
public interface AgentKnowledgeMapper {

    /** 只检索每份文档最新版的启用条目；新版停用不回退旧政策，词项全部参数绑定。 */
    @Select("""
            <script>
            SELECT c.entry_id, c.document_id, d.title AS document_title, c.document_version AS version,
                c.question, c.answer, c.live_check, d.reviewed_at, TRUE AS current,
                (
                <foreach collection="terms" item="term" separator=" + ">
                    CASE WHEN LOCATE(#{term}, LOWER(c.question)) > 0 THEN 20 ELSE 0 END
                    + CASE WHEN LOCATE(#{term}, LOWER(c.aliases)) > 0 THEN 10 ELSE 0 END
                    + CASE WHEN LOCATE(#{term}, LOWER(c.answer)) > 0 THEN 2 ELSE 0 END
                    + CASE WHEN LOCATE(#{term}, LOWER(c.live_check)) > 0 THEN 1 ELSE 0 END
                </foreach>
                ) AS relevance
            FROM agent_knowledge_chunk c
            JOIN agent_knowledge_document d ON d.document_id=c.document_id AND d.version=c.document_version
            WHERE d.status='ACTIVE' AND c.enabled=1
                AND d.version=(SELECT MAX(v.version) FROM agent_knowledge_document v WHERE v.document_id=d.document_id)
            HAVING relevance &gt;= 10
            ORDER BY relevance DESC, c.entry_id ASC
            LIMIT #{limit}
            </script>
            """)
    List<AgentKnowledgeEntry> search(@Param("terms") List<String> terms, @Param("limit") int limit);

    /** 精确版本可读但须仍启用；未指定版本只返回当前最新版，不将旧引用自动换新。 */
    @Select("""
            <script>
            SELECT c.entry_id, c.document_id, d.title AS document_title, c.document_version AS version,
                c.question, c.answer, c.live_check, d.reviewed_at,
                (d.version=(SELECT MAX(v.version) FROM agent_knowledge_document v WHERE v.document_id=d.document_id)) AS current
            FROM agent_knowledge_chunk c
            JOIN agent_knowledge_document d ON d.document_id=c.document_id AND d.version=c.document_version
            WHERE c.entry_id=#{entryId} AND c.enabled=1 AND d.status='ACTIVE'
            <choose>
                <when test="version != null">AND c.document_version=#{version}</when>
                <otherwise>
                    AND d.version=(SELECT MAX(v.version) FROM agent_knowledge_document v WHERE v.document_id=d.document_id)
                </otherwise>
            </choose>
            </script>
            """)
    AgentKnowledgeEntry entry(@Param("entryId") String entryId, @Param("version") Integer version);
}
