package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 邮件通知任务的持久化操作。
 */
@Mapper
public interface TradeTaskMapper {

    /**
     * 根据库存预占查询新建订单。
     */
    @Select("""
            SELECT o.*
            FROM order_info o
            JOIN stock_reservation r ON r.order_id=o.id
            WHERE r.id=#{reservationId}
            """)
    List<Map<String, Object>> findOrderByReservation(@Param("reservationId") long reservationId);

    /**
     * 根据成功支付流水查询可通知的订单。
     */
    @Select("""
            SELECT o.*
            FROM order_info o
            JOIN payment_record p ON p.order_id=o.id AND o.active_payment_id=p.id
            WHERE p.payment_no=#{paymentNo}
              AND p.pay_status=2
              AND o.order_status IN(1,3)
            """)
    List<Map<String, Object>> findPaidOrderByPayment(@Param("paymentNo") String paymentNo);

    /**
     * 按业务键幂等创建邮件通知任务。
     */
    @Insert("""
            INSERT INTO payment_compensation(
                payment_no,order_id,order_no,user_id,task_type,business_key,payload,
                task_status,retry_count,max_retry,next_execute_time,create_time,update_time
            ) VALUES(
                NULL,#{orderId},#{orderNo},#{userId},#{taskType},#{businessKey},#{payload},
                0,0,3,#{nextExecuteTime},#{createTime},#{updateTime}
            )
            ON DUPLICATE KEY UPDATE business_key=business_key
            """)
    int insertEmailTask(@Param("orderId") Object orderId, @Param("orderNo") Object orderNo,
            @Param("userId") Object userId, @Param("taskType") String taskType,
            @Param("businessKey") String businessKey, @Param("payload") String payload,
            @Param("nextExecuteTime") LocalDateTime nextExecuteTime, @Param("createTime") LocalDateTime createTime,
            @Param("updateTime") LocalDateTime updateTime);

    /**
     * 查询到期或租约过期的邮件任务。
     */
    @Select("""
            SELECT *
            FROM payment_compensation
            WHERE task_type IN('EMAIL_ORDER','EMAIL_PAYMENT')
              AND ((task_status=0 AND next_execute_time<=#{now})
                OR (task_status=1 AND lease_until<=#{leaseNow}))
            ORDER BY id
            LIMIT 50
            """)
    List<Map<String, Object>> findDueEmailTasks(@Param("now") LocalDateTime now,
            @Param("leaseNow") LocalDateTime leaseNow);

    /**
     * 领取邮件任务租约并增加尝试次数。
     */
    @Update("""
            UPDATE payment_compensation
            SET task_status=1,lease_token=#{token},lease_until=#{leaseUntil},
                retry_count=retry_count+1,last_execute_time=#{lastExecuteTime},update_time=#{now}
            WHERE id=#{id}
              AND retry_count<3
              AND ((task_status=0 AND next_execute_time<=#{dueAt})
                OR (task_status=1 AND lease_until<=#{leaseNow}))
            """)
    int claimEmailTask(@Param("token") String token, @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("lastExecuteTime") LocalDateTime lastExecuteTime, @Param("now") LocalDateTime now,
            @Param("id") long id, @Param("dueAt") LocalDateTime dueAt, @Param("leaseNow") LocalDateTime leaseNow);

    /**
     * 将超过重试上限的邮件任务标记失败。
     */
    @Update("""
            UPDATE payment_compensation
            SET task_status=3,lease_token=NULL,lease_until=NULL,
                error_msg='Email retry limit exhausted'
            WHERE id=#{id}
              AND retry_count>=3
              AND (task_status=0 OR (task_status=1 AND lease_until<=#{now}))
            """)
    int exhaustEmailTask(@Param("id") long id, @Param("now") LocalDateTime now);

    /**
     * 查询订单和用户邮箱，用于发送通知。
     */
    @Select("""
            SELECT o.*,u.email
            FROM order_info o
            JOIN user u ON u.id=o.user_id
            WHERE o.id=#{orderId}
            """)
    Map<String, Object> findOrderWithEmail(@Param("orderId") Object orderId);

    /**
     * 凭租约令牌将邮件任务标记完成。
     */
    @Update("""
            UPDATE payment_compensation
            SET task_status=2,lease_token=NULL,lease_until=NULL,update_time=#{now}
            WHERE id=#{id} AND lease_token=#{token}
            """)
    int completeEmailTask(@Param("now") LocalDateTime now, @Param("id") long id, @Param("token") String token);

    /**
     * 记录邮件发送失败和下次执行时间。
     */
    @Update("""
            UPDATE payment_compensation
            SET task_status=#{status},next_execute_time=#{nextExecuteTime},
                error_msg=#{error},lease_token=NULL,lease_until=NULL,update_time=#{now}
            WHERE id=#{id} AND lease_token=#{token}
            """)
    int failEmailTask(@Param("status") int status, @Param("nextExecuteTime") LocalDateTime nextExecuteTime,
            @Param("error") String error, @Param("now") LocalDateTime now, @Param("id") long id,
            @Param("token") String token);
}
