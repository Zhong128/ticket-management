package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.ticketmanagement.pojo.AgentAction;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentActionMapper {

    /** 内部核对同用户同目标未解决卡片，包含已擦除会话的最小恢复记录。 */
    @Select("""
            SELECT a.* FROM agent_action_guard g
            JOIN agent_action a ON a.id=g.action_id AND a.user_id=g.user_id
            WHERE g.user_id=#{userId} AND g.type=#{type} AND g.target_id=#{targetId}
                AND a.status IN ('EXECUTING', 'UNKNOWN')
            """)
    AgentAction unresolved(@Param("userId") long userId, @Param("type") String type, @Param("targetId") long targetId);

    /** 创建目标互斥行；重复目标不改变原卡片指针。 */
    @Insert("""
            INSERT INTO agent_action_guard(user_id, type, target_id)
            VALUES(#{userId}, #{type}, #{targetId})
            ON DUPLICATE KEY UPDATE user_id=user_id
            """)
    int ensureGuard(@Param("userId") long userId, @Param("type") String type, @Param("targetId") long targetId);

    /** 取得同用户同目标锁，跨会话阻止未解决申请换新幂等键。 */
    @Select("""
            SELECT action_id FROM agent_action_guard
            WHERE user_id=#{userId} AND type=#{type} AND target_id=#{targetId}
            FOR UPDATE
            """)
    String lockGuard(@Param("userId") long userId, @Param("type") String type, @Param("targetId") long targetId);

    /** 持有目标锁后绑定唯一最新草稿。 */
    @Update("""
            UPDATE agent_action_guard SET action_id=#{id}
            WHERE user_id=#{userId} AND type=#{type} AND target_id=#{targetId}
            """)
    int bindGuard(AgentAction action);

    /** 插入服务端快照和固定幂等键，不执行交易。 */
    @Insert("""
            INSERT INTO agent_action(id, user_id, conversation_id, run_id, type, target_id,
                quantity, unit_price, total_amount, title, event_name, session_name, session_time,
                tier_name, request_key, status, message, created_at, updated_at, expires_at)
            VALUES(#{id}, #{userId}, #{conversationId}, #{runId}, #{type}, #{targetId},
                #{quantity}, #{unitPrice}, #{totalAmount}, #{title}, #{eventName}, #{sessionName},
                #{sessionTime}, #{tierName}, #{requestKey}, #{status}, #{message},
                #{createdAt}, #{updatedAt}, #{expiresAt})
            """)
    int insert(AgentAction action);

    /** 内部读取最小恢复记录，服务层必须另查本人会话。 */
    @Select("SELECT * FROM agent_action WHERE id=#{id}")
    AgentAction find(String id);

    /** 在会话、运行、目标锁之后锁定卡片。 */
    @Select("SELECT * FROM agent_action WHERE id=#{id} FOR UPDATE")
    AgentAction lock(String id);

    /** 返回本人会话未擦除卡片，按创建顺序供历史恢复。 */
    @Select("""
            SELECT * FROM agent_action
            WHERE conversation_id=#{id} AND user_id=#{userId} AND content_erased=0
            ORDER BY created_at DESC, id DESC LIMIT 100
            """)
    List<AgentAction> list(@Param("id") String id, @Param("userId") long userId);

    /** 同一运行创建新卡片时让原待确认草稿失效。 */
    @Update("""
            UPDATE agent_action SET status='EXPIRED', message='已由新卡片替代',
                version=version+1, updated_at=#{now}
            WHERE run_id=#{runId} AND status='PENDING'
            """)
    int supersede(@Param("runId") String runId, @Param("now") LocalDateTime now);

    /** 仅未执行草稿允许状态变更，重复确认不能再次获得执行权。 */
    @Update("""
            UPDATE agent_action SET status=#{status}, message=#{message}, version=version+1, updated_at=#{now}
            WHERE id=#{id} AND status='PENDING' AND content_erased=0
            """)
    int pendingState(@Param("id") String id, @Param("status") String status,
                     @Param("message") String message, @Param("now") LocalDateTime now);

    /** 执行与只读恢复按版本条件写回，不重新恢复已擦除的展示字段。 */
    @Update("""
            UPDATE agent_action SET status=#{status}, request_status=#{requestStatus}, order_id=#{orderId},
                message=CASE WHEN content_erased=1 THEN NULL ELSE #{message} END,
                version=version+1, updated_at=#{now}
            WHERE id=#{id} AND version=#{version} AND status IN ('EXECUTING', 'UNKNOWN')
            """)
    int result(@Param("id") String id, @Param("version") long version, @Param("status") String status,
               @Param("requestStatus") String requestStatus, @Param("orderId") Long orderId,
               @Param("message") String message, @Param("now") LocalDateTime now);
}
