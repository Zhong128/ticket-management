package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.ticketmanagement.pojo.PaymentRecord;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PaymentRecordMapper {

    /**
     * 插入支付记录
     */
    @Insert("INSERT INTO payment_record(order_id, order_no, user_id, payment_no, trade_no, " +
            "total_amount, pay_status, pay_type, subject, body, expire_time, create_time, update_time) " +
            "VALUES(#{orderId}, #{orderNo}, #{userId}, #{paymentNo}, #{tradeNo}, " +
            "#{totalAmount}, #{payStatus}, #{payType}, #{subject}, #{body}, #{expireTime}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PaymentRecord paymentRecord);

    /**
     * 根据支付流水号查询
     */
    @Select("SELECT * FROM payment_record WHERE payment_no = #{paymentNo}")
    @Results(id = "paymentResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "order_id", property = "orderId"),
            @Result(column = "order_no", property = "orderNo"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "payment_no", property = "paymentNo"),
            @Result(column = "trade_no", property = "tradeNo"),
            @Result(column = "total_amount", property = "totalAmount"),
            @Result(column = "pay_status", property = "payStatus"),
            @Result(column = "pay_type", property = "payType"),
            @Result(column = "subject", property = "subject"),
            @Result(column = "body", property = "body"),
            @Result(column = "notify_data", property = "notifyData"),
            @Result(column = "notify_time", property = "notifyTime"),
            @Result(column = "pay_time", property = "payTime"),
            @Result(column = "expire_time", property = "expireTime"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    PaymentRecord selectByPaymentNo(@Param("paymentNo") String paymentNo);

    /**
     * 根据订单ID查询
     */
    @Select("SELECT * FROM payment_record WHERE order_id = #{orderId} ORDER BY id DESC LIMIT 1")
    @ResultMap("paymentResultMap")
    PaymentRecord selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 根据订单号查询
     */
    @Select("SELECT * FROM payment_record WHERE order_no = #{orderNo} ORDER BY id DESC LIMIT 1")
    @ResultMap("paymentResultMap")
    PaymentRecord selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 根据支付宝交易号查询
     */
    @Select("SELECT * FROM payment_record WHERE trade_no = #{tradeNo}")
    @ResultMap("paymentResultMap")
    PaymentRecord selectByTradeNo(@Param("tradeNo") String tradeNo);

    /**
     * 更新支付状态
     */
    @Update("UPDATE payment_record SET pay_status = #{payStatus}, " +
            "trade_no = #{tradeNo}, notify_data = #{notifyData}, " +
            "notify_time = #{notifyTime}, pay_time = #{payTime}, " +
            "update_time = #{updateTime} WHERE id = #{id}")
    int updateStatus(PaymentRecord paymentRecord);

    /**
     * 查询待支付的过期订单
     */
    @Select("SELECT * FROM payment_record WHERE pay_status = 0 " +
            "AND expire_time < NOW() LIMIT #{limit}")
    @ResultMap("paymentResultMap")
    List<PaymentRecord> selectExpiredPendingPayments(@Param("limit") Integer limit);

    /**
     * 查询待补偿的支付记录（状态为待支付但已过期，且没有补偿任务）
     */
    @Select("SELECT p.* FROM payment_record p " +
            "LEFT JOIN payment_compensation c ON p.payment_no = c.payment_no " +
            "WHERE p.pay_status = 0 AND p.expire_time < NOW() " +
            "AND c.id IS NULL LIMIT #{limit}")
    @ResultMap("paymentResultMap")
    List<PaymentRecord> selectNeedCompensation(@Param("limit") Integer limit);

    /**
     * 【新增】查询待支付超过指定时间的订单（用于补偿）
     */
    @Select("SELECT * FROM payment_record " +
            "WHERE pay_status = 0 " +
            "AND create_time < DATE_SUB(NOW(), INTERVAL #{waitingMinutes} MINUTE) " +
            "ORDER BY create_time ASC " +
            "LIMIT #{limit}")
    @ResultMap("paymentResultMap")
    List<PaymentRecord> selectWaitingPayments(
        @Param("waitingMinutes") int waitingMinutes, 
        @Param("limit") int limit
    );

    /**
     * 【新增】清理过期的支付记录
     */
    @Delete("DELETE FROM payment_record " +
            "WHERE pay_status IN (2, 3) " +  // 已关闭或已取消
            "AND update_time < DATE_SUB(NOW(), INTERVAL #{daysToKeep} DAY)")
    int cleanExpiredPayments(@Param("daysToKeep") int daysToKeep);
}