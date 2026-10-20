package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Outbox 事件的持久化操作；事务边界由调用方控制。
 */
@Mapper
public interface TradeOutboxMapper {

    /**
     * 按业务键幂等写入待发布事件。
     */
    @Insert("""
            INSERT INTO message_outbox(
                event_id,business_key,event_type,payload,publish_status,
                next_attempt_at,attempts,created_at,updated_at
            ) VALUES(
                #{eventId},#{businessKey},#{eventType},#{payload},'NEW',
                #{availableAt},0,#{now},#{updatedAt}
            )
            ON DUPLICATE KEY UPDATE business_key=business_key
            """)
    int insertEvent(@Param("eventId") String eventId, @Param("businessKey") String businessKey,
            @Param("eventType") String eventType, @Param("payload") String payload,
            @Param("availableAt") LocalDateTime availableAt, @Param("now") LocalDateTime now,
            @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 查找待发布或租约过期的事件。
     */
    @Select("""
            SELECT * FROM message_outbox
            WHERE (publish_status='NEW' AND next_attempt_at<=#{now})
               OR (publish_status='IN_FLIGHT' AND lease_until<=#{leaseNow})
            ORDER BY next_attempt_at
            LIMIT 100
            """)
    List<Map<String, Object>> findDueEvents(@Param("now") LocalDateTime now, @Param("leaseNow") LocalDateTime leaseNow);

    /**
     * 按状态及时间条件领取事件发布租约。
     */
    @Update("""
            UPDATE message_outbox
            SET publish_status='IN_FLIGHT',lease_token=#{token},
                lease_until=#{leaseUntil},updated_at=#{now}
            WHERE event_id=#{eventId}
              AND ((publish_status='NEW' AND next_attempt_at<=#{dueAt})
                OR (publish_status='IN_FLIGHT' AND lease_until<=#{leaseNow}))
            """)
    int claimEvent(@Param("token") String token, @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now, @Param("eventId") String eventId, @Param("dueAt") LocalDateTime dueAt,
            @Param("leaseNow") LocalDateTime leaseNow);

    /**
     * 收到发布确认后凭租约令牌标记成功。
     */
    @Update("""
            UPDATE message_outbox
            SET publish_status='PUBLISHED',lease_token=NULL,
                lease_until=NULL,last_error=NULL,updated_at=#{now}
            WHERE event_id=#{eventId} AND lease_token=#{token}
            """)
    int markPublished(@Param("now") LocalDateTime now, @Param("eventId") String eventId, @Param("token") String token);

    /**
     * 发布失败后记录错误并安排下次重试。
     */
    @Update("""
            UPDATE message_outbox
            SET publish_status='NEW',attempts=attempts+1,
                next_attempt_at=#{nextAttempt},lease_token=NULL,
                lease_until=NULL,last_error=#{error},updated_at=#{now}
            WHERE event_id=#{eventId} AND lease_token=#{token}
            """)
    int rescheduleEvent(@Param("nextAttempt") LocalDateTime nextAttempt, @Param("error") String error,
            @Param("now") LocalDateTime now, @Param("eventId") String eventId, @Param("token") String token);

    /**
     * 读取预占的绝对截止时间，用于延迟消息。
     */
    @Select("""
            SELECT expires_at
            FROM stock_reservation
            WHERE id=#{reservationId}
            """)
    LocalDateTime findReservationExpiry(@Param("reservationId") long reservationId);
}
