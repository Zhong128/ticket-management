package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.ticketmanagement.pojo.PaymentRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 退款请求及任务租约的数据库操作。
 */
@Mapper
public interface TradeRefundMapper {

    /**
     * 按退款流水号幂等创建退款请求。
     */
    @Insert("INSERT INTO refund_record(refund_no,payment_id,payment_no,order_id,user_id,amount,reason,"
            + "status,attempts,next_attempt_at,created_at,updated_at) VALUES(#{refundNo},#{payment.id},"
            + "#{payment.paymentNo},#{payment.orderId},#{payment.userId},#{payment.totalAmount},#{reason},"
            + "'REQUESTED',0,#{now},#{now},#{now}) ON DUPLICATE KEY UPDATE payment_id=payment_id")
    int request(@Param("refundNo") String refundNo, @Param("payment") PaymentRecord payment,
            @Param("reason") String reason, @Param("now") LocalDateTime now);

    /**
     * 查询用户最近的退款记录。
     */
    @Select("SELECT refund_no,payment_no,order_id,amount,reason,status,attempts,created_at,updated_at "
            + "FROM refund_record WHERE user_id=#{userId} ORDER BY id DESC LIMIT 100")
    List<Map<String, Object>> forUser(@Param("userId") long userId);

    /**
     * 查询后台最近的退款记录。
     */
    @Select("SELECT * FROM refund_record ORDER BY id DESC LIMIT 100")
    List<Map<String, Object>> forAdmin();

    /**
     * 将人工处理任务重新置为待重试。
     */
    @Update("UPDATE refund_record SET status='UNKNOWN',attempts=0,next_attempt_at=#{now},"
            + "updated_at=#{now} WHERE refund_no=#{refundNo} AND status='MANUAL_REQUIRED'")
    int retry(@Param("refundNo") String refundNo, @Param("now") LocalDateTime now);

    /**
     * 把超过重试上限的过期任务转为人工处理。
     */
    @Update("UPDATE refund_record SET status='MANUAL_REQUIRED',lease_token=NULL,lease_until=NULL,"
            + "updated_at=#{now} WHERE status='PROCESSING' AND attempts>=10 AND lease_until<=#{now}")
    int exhaustExpiredLeases(@Param("now") LocalDateTime now);

    /**
     * 查找已到执行时间或租约过期的退款任务。
     */
    @Select("SELECT id FROM refund_record WHERE (status IN ('REQUESTED','UNKNOWN','RETRYABLE_FAILED') "
            + "AND next_attempt_at<=#{now}) OR (status='PROCESSING' AND lease_until<=#{now}) ORDER BY id "
            + "LIMIT 50")
    List<Long> findDueIds(@Param("now") LocalDateTime now);

    /**
     * 按状态和租约条件领取退款任务。
     */
    @Update("UPDATE refund_record SET status='PROCESSING',attempts=attempts+1,lease_token=#{token},"
            + "lease_until=#{leaseUntil},updated_at=#{now} WHERE id=#{id} AND attempts<10 AND ((status IN "
            + "('REQUESTED','UNKNOWN','RETRYABLE_FAILED') AND next_attempt_at<=#{now}) OR "
            + "(status='PROCESSING' AND lease_until<=#{now}))")
    int claim(@Param("id") long id, @Param("token") String token, @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now);

    /**
     * 按主键读取退款任务。
     */
    @Select("SELECT * FROM refund_record WHERE id=#{id}")
    Map<String, Object> findById(@Param("id") long id);

    /**
     * 锁定关联支付流水，串行更新退款状态。
     */
    @Select("SELECT id FROM payment_record WHERE id=#{paymentId} FOR UPDATE")
    List<Long> lockPayment(@Param("paymentId") long paymentId);

    /**
     * 凭租约令牌写入退款结果并释放租约。
     */
    @Update("UPDATE refund_record SET status=#{status},trade_no=#{tradeNo},response_data=#{detail},"
            + "last_error=#{detail},next_attempt_at=#{nextAttemptAt},lease_token=NULL,lease_until=NULL,"
            + "updated_at=#{now} WHERE id=#{id} AND lease_token=#{token}")
    int complete(@Param("id") long id, @Param("token") String token, @Param("status") String status,
            @Param("tradeNo") String tradeNo, @Param("detail") String detail,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt, @Param("now") LocalDateTime now);

    /**
     * 将支付流水标记为已退款。
     */
    @Update("UPDATE payment_record SET pay_status=5,update_time=#{now} WHERE id=#{paymentId}")
    int markPaymentRefunded(@Param("paymentId") long paymentId, @Param("now") LocalDateTime now);
}
