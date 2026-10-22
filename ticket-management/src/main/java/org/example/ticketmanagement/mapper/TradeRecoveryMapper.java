package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 交易恢复任务使用的查询和状态更新。
 */
@Mapper
public interface TradeRecoveryMapper {

    /** 游标扫描待建单及已到期申请，活跃未到期订单不占用扫描批次。 */
    @Select("""
            SELECT id,order_id,status,expires_at FROM stock_reservation
            WHERE id>#{after} AND (status='RESERVED' OR (status='ORDER_CREATED' AND expires_at<=#{now}))
            ORDER BY id LIMIT 100
            """)
    List<Map<String, Object>> findDueReservations(@Param("after") long after, @Param("now") LocalDateTime now);

    /**
     * 查询预占的截止时间及当前状态。
     */
    @Select("""
            SELECT order_id,status,expires_at
            FROM stock_reservation
            WHERE id=#{reservationId}
            """)
    List<Map<String, Object>> findReservationDeadline(@Param("reservationId") long reservationId);

    /**
     * 查询已到期且已创建订单的预占。
     */
    @Select("""
            SELECT order_id
            FROM stock_reservation
            WHERE id=#{reservationId}
              AND expires_at<=#{now}
              AND status='ORDER_CREATED'
            """)
    List<Map<String, Object>> findExpiredOrderedReservation(@Param("reservationId") long reservationId,
            @Param("now") LocalDateTime now);

    /**
     * 查找需要重新创建订单或处理超时的预占。
     */
    @Select("""
            SELECT id,order_id,status,expires_at
            FROM stock_reservation
            WHERE status IN('RESERVED','ORDER_CREATED')
            ORDER BY id
            """)
    List<Map<String, Object>> findRecoverableReservations();

    /**
     * 查找过期或取消中、仍需关单的订单。
     */
    @Select("""
            SELECT id
            FROM order_info
            WHERE order_status=4
               OR (order_status=0
                   AND COALESCE(expire_time,DATE_ADD(create_time,INTERVAL 30 MINUTE))<=#{now})
            """)
    List<Long> findOrdersRequiringCancellation(@Param("now") LocalDateTime now);

    /**
     * 结束已由订单状态解决的关单任务。
     */
    @Update("""
            UPDATE payment_compensation c
            JOIN order_info o ON o.id=c.order_id
            SET c.task_status=2,c.update_time=#{now}
            WHERE c.task_type='ORDER_CLOSE'
              AND c.task_status IN(0,1)
              AND o.order_status IN(1,2,3)
            """)
    int completeResolvedCloseTasks(@Param("now") LocalDateTime now);

    /**
     * 按订单号查找关联的库存预占。
     */
    @Select("""
            SELECT id
            FROM stock_reservation
            WHERE order_no=#{orderNo}
            """)
    List<Long> findReservationIdsByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 检查旧消息对应的订单是否已存在。
     */
    @Select("""
            SELECT COUNT(*)
            FROM order_info
            WHERE order_no=#{orderNo}
            """)
    int countOrdersByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 查询旧消息对应的过期待支付订单。
     */
    @Select("""
            SELECT id
            FROM order_info
            WHERE order_no=#{orderNo}
              AND order_status IN(0,4)
              AND COALESCE(expire_time,DATE_ADD(create_time,INTERVAL 30 MINUTE))<=#{now}
            """)
    List<Long> findExpiredLegacyOrders(@Param("orderNo") String orderNo, @Param("now") LocalDateTime now);
}
