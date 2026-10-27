package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.ticketmanagement.dto.AdminSessionQuery;
import org.example.ticketmanagement.dto.AdminRefundQuery;
import org.example.ticketmanagement.vo.EventSessionVO;
import org.example.ticketmanagement.vo.AdminDashboardVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AdminOverviewMapper {
    /** 管理分页包含下架场次及下架演出。 */
    @Select("""
            <script>
            SELECT s.*, e.name AS event_name, e.artist_name, e.venue
            FROM event_session s
            LEFT JOIN event e ON e.id=s.event_id
            <where>
                <if test="eventId != null">AND s.event_id=#{eventId}</if>
                <if test="status != null">AND s.status=#{status}</if>
                <if test="eventStatus != null">AND e.status=#{eventStatus}</if>
                <if test="startTime != null">AND s.session_time &gt;= #{startTime}</if>
                <if test="endTime != null">AND s.session_time &lt;= #{endTime}</if>
            </where>
            <choose>
                <when test="ascending">ORDER BY s.session_time ASC, s.id ASC</when>
                <otherwise>ORDER BY s.session_time DESC, s.id DESC</otherwise>
            </choose>
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<EventSessionVO> sessions(AdminSessionQuery query);

    /** 按相同条件统计场次数量。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM event_session s
            LEFT JOIN event e ON e.id=s.event_id
            <where>
                <if test="eventId != null">AND s.event_id=#{eventId}</if>
                <if test="status != null">AND s.status=#{status}</if>
                <if test="eventStatus != null">AND e.status=#{eventStatus}</if>
                <if test="startTime != null">AND s.session_time &gt;= #{startTime}</if>
                <if test="endTime != null">AND s.session_time &lt;= #{endTime}</if>
            </where>
            </script>
            """)
    long countSessions(AdminSessionQuery query);

    /** 按流水、订单及状态精确筛选，保留原退款管理字段。 */
    @Select("""
            <script>
            SELECT r.* FROM refund_record r
            LEFT JOIN order_info o ON o.id=r.order_id
            <where>
                <if test="refundNo != null and refundNo != ''">AND r.refund_no=#{refundNo}</if>
                <if test="orderNo != null and orderNo != ''">AND o.order_no=#{orderNo}</if>
                <if test="status != null and status != ''">AND r.status=#{status}</if>
            </where>
            ORDER BY r.id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> refunds(AdminRefundQuery query);

    /** 按退款分页条件统计总数。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM refund_record r
            LEFT JOIN order_info o ON o.id=r.order_id
            <where>
                <if test="refundNo != null and refundNo != ''">AND r.refund_no=#{refundNo}</if>
                <if test="orderNo != null and orderNo != ''">AND o.order_no=#{orderNo}</if>
                <if test="status != null and status != ''">AND r.status=#{status}</if>
            </where>
            </script>
            """)
    long countRefunds(AdminRefundQuery query);

    /** 使用同一时刻统计售卖、七天场次和待处理交易。 */
    @Select("""
            SELECT
                (SELECT COUNT(*) FROM event WHERE status=1
                    AND sale_start_time <= #{now} AND sale_end_time > #{now}
                    AND event_end_time > #{now}) AS on_sale_event_count,
                (SELECT COUNT(*) FROM event_session s JOIN event e ON e.id=s.event_id
                    WHERE s.status=1 AND e.status=1 AND s.session_time >= #{now}
                    AND s.session_time < #{until}) AS upcoming_session_count,
                (SELECT COUNT(*) FROM order_info WHERE order_status=0) AS pending_order_count,
                (SELECT COUNT(*) FROM refund_record WHERE status='MANUAL_REQUIRED') AS refund_attention_count
            """)
    AdminDashboardVO summary(@Param("now") LocalDateTime now, @Param("until") LocalDateTime until);
}
