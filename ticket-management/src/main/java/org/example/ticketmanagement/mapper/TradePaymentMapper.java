package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.ticketmanagement.pojo.PaymentRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 交易支付流程使用的数据库操作。
 */
@Mapper
public interface TradePaymentMapper {

    /**
     * 按支付流水号读取支付记录。
     */
    @Select("SELECT * FROM payment_record WHERE payment_no=#{paymentNo}")
    PaymentRecord find(@Param("paymentNo") String paymentNo);

    /**
     * 锁定支付记录，串行处理支付回调与查询结果。
     */
    @Select("SELECT * FROM payment_record WHERE payment_no=#{paymentNo} FOR UPDATE")
    PaymentRecord lock(@Param("paymentNo") String paymentNo);

    /**
     * 先锁订单，保持支付流程统一的锁顺序。
     */
    @Select("SELECT * FROM order_info WHERE id=#{id} FOR UPDATE")
    Map<String, Object> lockOrder(@Param("id") long id);

    /**
     * 查询订单关联的全部支付记录。
     */
    @Select("SELECT * FROM payment_record WHERE order_id=#{orderId} ORDER BY id")
    List<PaymentRecord> paymentsForOrder(@Param("orderId") long orderId);

    /**
     * 查询用户仍在支付期限内的待支付订单。
     */
    @Select("SELECT id FROM order_info WHERE user_id=#{userId} AND order_status=0 AND expire_time>#{now} "
            + "ORDER BY id")
    List<Long> payableOrders(@Param("userId") long userId, @Param("now") LocalDateTime now);

    /**
     * 查询需要与支付渠道对账的流水。
     */
    @Select("SELECT * FROM payment_record WHERE pay_status IN (0,1,3,4) ORDER BY update_time,id LIMIT "
            + "100")
    List<PaymentRecord> waiting();

    /**
     * 记录本次支付状态同步时间。
     */
    @Update("UPDATE payment_record SET update_time=#{now} WHERE id=#{id}")
    int reconciled(@Param("id") long id, @Param("now") LocalDateTime now);

    /**
     * 查询已过期、仍需关单的订单。
     */
    @Select("SELECT id FROM order_info WHERE order_status IN (0,4) AND expire_time<=#{now} ORDER BY id "
            + "LIMIT 100")
    List<Long> expiredOrders(@Param("now") LocalDateTime now);

    /**
     * 插入支付记录并回填主键。
     */
    @Insert("INSERT INTO payment_record(order_id,order_no,user_id,payment_no,total_amount,pay_status,"
            + "pay_type,subject,expire_time,create_time,update_time) VALUES(#{orderId},#{orderNo},"
            + "#{userId},#{paymentNo},#{totalAmount},0,1,#{subject},#{expireTime},#{createTime},"
            + "#{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PaymentRecord payment);

    /**
     * 将支付流水标记成功并保存渠道交易号。
     */
    @Update("UPDATE payment_record SET pay_status=CASE WHEN pay_status=5 THEN 5 ELSE 2 END,"
            + "trade_no=#{tradeNo},pay_time=COALESCE(pay_time,#{now}),update_time=#{now} WHERE id=#{id}")
    int paid(@Param("id") long id, @Param("tradeNo") String tradeNo, @Param("now") LocalDateTime now);

    /**
     * 保存首次有效回调时间及最近一次回调的最小审计摘要。
     */
    @Update("UPDATE payment_record SET notify_time=COALESCE(notify_time,#{now}),"
            + "notify_data=#{summary} WHERE payment_no=#{paymentNo}")
    int recordNotification(@Param("paymentNo") String paymentNo, @Param("summary") String summary,
            @Param("now") LocalDateTime now);

    /**
     * 将未成功支付的流水标记关闭。
     */
    @Update("UPDATE payment_record SET pay_status=4,update_time=#{now} WHERE id=#{id} AND pay_status IN "
            + "(0,1)")
    int close(@Param("id") long id, @Param("now") LocalDateTime now);
}
