package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.example.ticketmanagement.mapper.typehandler.AgentCitationTypeHandler;
import org.example.ticketmanagement.pojo.AgentConversation;
import org.example.ticketmanagement.pojo.AgentMessage;
import org.example.ticketmanagement.pojo.AgentRun;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentConversationMapper {

    /** 清空会话时擦除卡片展示；保留执行恢复标识，迟到结果不得恢复展示。 */
    @Update("""
            UPDATE agent_action SET content_erased=1, title=NULL, event_name=NULL,
                session_name=NULL, session_time=NULL, tier_name=NULL, message=NULL,
                version=version+CASE WHEN status='PENDING' THEN 1 ELSE 0 END,
                updated_at=CASE WHEN status IN ('EXECUTING', 'UNKNOWN') THEN updated_at ELSE #{now} END,
                status=CASE WHEN status='PENDING' THEN 'EXPIRED' ELSE status END
            WHERE conversation_id=#{id}
            """)
    int eraseActions(@Param("id") String id, @Param("now") LocalDateTime now);

    /** 新建本人会话，默认标记唯一约束防止重复默认会话。 */
    @Insert("""
            INSERT INTO agent_conversation(id, user_id, title, default_marker, created_at, updated_at)
            VALUES(#{id}, #{userId}, #{title}, #{defaultMarker}, #{createdAt}, #{updatedAt})
            """)
    int insertConversation(AgentConversation conversation);

    /** 并发创建默认会话时只忽略同一用户默认标记冲突。 */
    @Insert("""
            INSERT INTO agent_conversation(id, user_id, title, default_marker, created_at, updated_at)
            VALUES(#{id}, #{userId}, #{title}, 1, #{createdAt}, #{updatedAt})
            ON DUPLICATE KEY UPDATE id=id
            """)
    int ensureDefault(AgentConversation conversation);

    /** 获取本人当前默认会话，不包含已删除记录。 */
    @Select("""
            SELECT * FROM agent_conversation
            WHERE user_id=#{userId} AND default_marker=1 AND deleted_at IS NULL
            """)
    AgentConversation defaultConversation(long userId);

    /** 锁定本人会话；运行受理、完成、清空及删除均先取得此锁。 */
    @Select("""
            SELECT * FROM agent_conversation
            WHERE id=#{id} AND user_id=#{userId} AND deleted_at IS NULL
            FOR UPDATE
            """)
    AgentConversation lockConversation(@Param("id") String id, @Param("userId") long userId);

    /** 校验本人会话归属，不锁定读取历史。 */
    @Select("""
            SELECT * FROM agent_conversation
            WHERE id=#{id} AND user_id=#{userId} AND deleted_at IS NULL
            """)
    AgentConversation conversation(@Param("id") String id, @Param("userId") long userId);

    /** 按最近更新与ID稳定分页查询本人未删除会话。 */
    @Select("""
            SELECT * FROM agent_conversation
            WHERE user_id=#{userId} AND deleted_at IS NULL
            ORDER BY updated_at DESC, id DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<AgentConversation> conversations(@Param("userId") long userId, @Param("size") int size,
                                          @Param("offset") int offset);

    /** 统计本人未删除会话数量。 */
    @Select("SELECT COUNT(*) FROM agent_conversation WHERE user_id=#{userId} AND deleted_at IS NULL")
    long countConversations(long userId);

    /** 持有会话锁后绑定唯一运行，或完成时释放占用。 */
    @Update("UPDATE agent_conversation SET active_run_id=#{runId}, updated_at=#{now} WHERE id=#{id}")
    int active(@Param("id") String id, @Param("runId") String runId, @Param("now") LocalDateTime now);

    /** 仅重命名本人未删除会话。 */
    @Update("""
            UPDATE agent_conversation SET title=#{title}, updated_at=#{now}
            WHERE id=#{id} AND user_id=#{userId} AND deleted_at IS NULL
            """)
    int rename(@Param("id") String id, @Param("userId") long userId,
               @Param("title") String title, @Param("now") LocalDateTime now);

    /** 软删除已校验归属且没有运行占用的会话，释放默认唯一标记。 */
    @Update("""
            UPDATE agent_conversation SET deleted_at=#{now}, updated_at=#{now}, default_marker=NULL
            WHERE id=#{id} AND active_run_id IS NULL
            """)
    int deleteConversation(@Param("id") String id, @Param("now") LocalDateTime now);

    /** 以会话及客户端消息标识查询幂等记录，不重复执行模型。 */
    @ResultMap("agentRunMap")
    @Select("SELECT * FROM agent_run WHERE conversation_id=#{conversationId} AND client_message_id=#{clientId}")
    AgentRun byClientId(@Param("conversationId") String conversationId, @Param("clientId") String clientId);

    /** 查询运行；服务层必须另外验证会话归属。 */
    @Results(id = "agentRunMap", value = @Result(column = "citation_data", property = "citations",
            typeHandler = AgentCitationTypeHandler.class))
    @Select("SELECT * FROM agent_run WHERE id=#{id}")
    AgentRun run(String id);

    /** 取得会话锁后当前读运行，避免RR旧快照看不到刚提交的运行而误清占用。 */
    @ResultMap("agentRunMap")
    @Select("SELECT * FROM agent_run WHERE id=#{id} FOR UPDATE")
    AgentRun lockRun(String id);

    /** 创建运行及固定截止时间，与会话占用和用户消息同事务提交。 */
    @Insert("""
            INSERT INTO agent_run(id, conversation_id, client_message_id, input_hash, status,
                tool_calls, tool_trace, created_at, deadline_at)
            VALUES(#{id}, #{conversationId}, #{clientMessageId}, #{inputHash}, 'RUNNING',
                0, '[]', #{createdAt}, #{deadlineAt})
            """)
    int insertRun(AgentRun run);

    /** 只允许运行从RUNNING进入一次终态，拒绝迟到或重复完成。 */
    @Update("""
            UPDATE agent_run SET status=#{status}, reply=#{reply}, error_code=#{errorCode},
                error_message=#{errorMessage}, completed_at=#{completedAt}
            WHERE id=#{id} AND status='RUNNING'
            """)
    int finish(AgentRun run);

    /** 只记录截止前仍运行中的工具摘要，不覆盖终态，不保存工具输入或完整输出。 */
    @Update("""
            UPDATE agent_run SET tool_calls=#{count}, tool_trace=#{trace}
            WHERE id=#{id} AND status='RUNNING' AND deadline_at > #{now}
            """)
    int toolTrace(@Param("id") String id, @Param("count") int count,
                  @Param("trace") String trace, @Param("now") LocalDateTime now);

    /** 回填消息主键；每个运行最多一条用户消息和一条成功回复。 */
    @Insert("""
            INSERT INTO agent_message(conversation_id, run_id, `role`, content, created_at)
            VALUES(#{conversationId}, #{runId}, #{role}, #{content}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMessage(AgentMessage message);

    /** 从最新消息向前分页，服务层反转为时间正序展示。 */
    @Results(id = "agentMessageMap", value = @Result(column = "citation_data", property = "citations",
            typeHandler = AgentCitationTypeHandler.class))
    @Select("""
            SELECT * FROM agent_message WHERE conversation_id=#{id}
            ORDER BY id DESC LIMIT #{size} OFFSET #{offset}
            """)
    List<AgentMessage> messages(@Param("id") String id, @Param("size") int size, @Param("offset") int offset);

    /** 统计该会话保留的消息数量。 */
    @Select("SELECT COUNT(*) FROM agent_message WHERE conversation_id=#{id}")
    long countMessages(String id);

    /** 重建最近20条已成功完成的上下文，不将失败问题或本次问题重复发送给模型。 */
    @ResultMap("agentMessageMap")
    @Select("""
            SELECT m.* FROM agent_message m
            JOIN agent_run r ON r.id=m.run_id AND r.status='SUCCEEDED'
            WHERE m.conversation_id=#{id}
            ORDER BY m.id DESC LIMIT 20
            """)
    List<AgentMessage> context(String id);

    /** 清空已锁定会话的消息；只作用于该会话。 */
    @Delete("DELETE FROM agent_message WHERE conversation_id=#{id}")
    int clearMessages(String id);

    /** 清除终态回复及工具摘要，保留幂等墓碑，旧客户端消息不会再次执行。 */
    @Update("""
            UPDATE agent_run SET reply=NULL, error_message=NULL, tool_trace='[]', citation_data=NULL
            WHERE conversation_id=#{id} AND status <> 'RUNNING'
            """)
    int eraseRunContent(String id);

    /** 仅为截止前活跃运行保存检索来源，不让迟到线程覆盖终态依据。 */
    @Update("""
            UPDATE agent_run SET citation_data=#{data}
            WHERE id=#{id} AND status='RUNNING' AND deadline_at > #{now}
            """)
    int citations(@Param("id") String id, @Param("data") String data, @Param("now") LocalDateTime now);

    /** 与成功回复同事务保存来源快照，仅作用于该运行的助手消息。 */
    @Update("""
            UPDATE agent_message SET citation_data=#{data}
            WHERE run_id=#{runId} AND `role`='assistant'
            """)
    int messageCitations(@Param("runId") String runId, @Param("data") String data);
}
