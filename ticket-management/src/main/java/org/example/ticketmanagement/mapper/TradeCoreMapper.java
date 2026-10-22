package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 库存预占和订单状态流转的数据库操作，事务规则由 Service 控制。
 */
@Mapper
public interface TradeCoreMapper {

    /**
     * 创建用户与票档的购买占位，已有记录时保持原值。
     */
    @Insert("INSERT IGNORE INTO purchase_guard(user_id,tier_id,reservation_id,create_time,update_time) "
            + "VALUES(#{userId},#{tierId},NULL,#{now},#{now})")
    int insertPurchaseGuard(@Param("userId") long userId, @Param("tierId") long tierId,
            @Param("now") LocalDateTime now);

    /**
     * 锁定用户与票档的购买占位，串行处理重复请求。
     */
    @Select("SELECT * FROM purchase_guard WHERE user_id=#{userId} AND tier_id=#{tierId} FOR UPDATE")
    Map<String, Object> lockPurchaseGuard(@Param("userId") long userId, @Param("tierId") long tierId);

    /**
     * 统计该用户在票档下尚未结束的订单。
     */
    @Select("SELECT COUNT(*) FROM order_info WHERE user_id=#{userId} AND tier_id=#{tierId} AND "
            + "order_status IN (0,1,3,4)")
    long countActiveOrders(@Param("userId") long userId, @Param("tierId") long tierId);

    /**
     * 把本次预占关联到用户购买占位。
     */
    @Update("UPDATE purchase_guard SET reservation_id=#{id},update_time=#{now} WHERE user_id=#{userId} "
            + "AND tier_id=#{tierId}")
    int attachPurchaseGuard(@Param("id") long id, @Param("now") LocalDateTime now, @Param("userId") long userId,
            @Param("tierId") long tierId);

    /**
     * 根据预占数据创建待支付订单并回填主键。
     */
    @Insert("INSERT INTO order_info(order_no,user_id,event_id,session_id,tier_id,quantity,total_amount,"
            + "order_status,expire_time,create_time,update_time) VALUES(#{order_no},#{user_id},#{event_id},"
            + "#{session_id},#{tier_id},#{quantity},#{total_amount},0,#{expires_at},#{create_time},"
            + "#{update_time})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertOrder(Map<String, Object> order);

    /**
     * 记录预占对应的订单及创建状态。
     */
    @Update("UPDATE stock_reservation SET order_id=#{orderId},status='ORDER_CREATED',update_time=#{now} "
            + "WHERE id=#{id}")
    int markOrderCreated(@Param("orderId") long orderId, @Param("now") LocalDateTime now, @Param("id") long id);

    /**
     * 锁定订单，串行处理取消和支付状态变更。
     */
    @Select("SELECT * FROM order_info WHERE id=#{orderId} FOR UPDATE")
    Map<String, Object> lockOrder(@Param("orderId") long orderId);

    /**
     * 将订单置为取消中，等待支付渠道确认。
     */
    @Update("UPDATE order_info SET order_status=4,cancel_reason=COALESCE(cancel_reason,"
            + "'CANCEL_REQUESTED'),update_time=#{now} WHERE id=#{orderId}")
    int markCancelRequested(@Param("now") LocalDateTime now, @Param("orderId") long orderId);

    /**
     * 写入幂等的订单关单补偿任务。
     */
    @Insert("INSERT IGNORE INTO payment_compensation(payment_no,order_id,order_no,user_id,task_type,"
            + "business_key,payload,task_status,retry_count,max_retry,next_execute_time,create_time,"
            + "update_time) VALUES(NULL,#{orderId},#{orderNo},#{userId},'ORDER_CLOSE',#{businessKey},"
            + "#{payload},0,0,20,#{nextExecuteTime},#{createTime},#{updateTime})")
    int insertOrderCloseTask(@Param("orderId") long orderId, @Param("orderNo") String orderNo,
            @Param("userId") long userId, @Param("businessKey") String businessKey, @Param("payload") String payload,
            @Param("nextExecuteTime") LocalDateTime nextExecuteTime, @Param("createTime") LocalDateTime createTime,
            @Param("updateTime") LocalDateTime updateTime);

    /**
     * 按订单锁定其库存预占。
     */
    @Select("SELECT * FROM stock_reservation WHERE order_id=#{orderId} FOR UPDATE")
    Map<String, Object> lockReservationByOrder(@Param("orderId") long orderId);

    /**
     * 仅将取消中的订单置为已取消。
     */
    @Update("UPDATE order_info SET order_status=2,update_time=#{now} WHERE id=#{orderId} AND "
            + "order_status=4")
    int markOrderCanceled(@Param("now") LocalDateTime now, @Param("orderId") long orderId);

    /**
     * 读取支付记录以核对订单、用户及金额。
     */
    @Select("SELECT * FROM payment_record WHERE id=#{paymentId}")
    Map<String, Object> findPayment(@Param("paymentId") long paymentId);

    /**
     * 记录成功支付流水并将订单置为已支付。
     */
    @Update("UPDATE order_info SET order_status=1,active_payment_id=#{paymentId},pay_time=#{payTime},"
            + "update_time=#{updateTime} WHERE id=#{orderId}")
    int markOrderPaid(@Param("paymentId") long paymentId, @Param("payTime") LocalDateTime payTime,
            @Param("updateTime") LocalDateTime updateTime, @Param("orderId") long orderId);

    /**
     * 将对应库存预占标记为已支付。
     */
    @Update("UPDATE stock_reservation SET status='PAID',update_time=#{now} WHERE id=#{id}")
    int markReservationPaid(@Param("now") LocalDateTime now, @Param("id") long id);

    /**
     * 记录异步创建订单的失败次数。
     */
    @Update("UPDATE stock_reservation SET attempts=#{attempts},update_time=#{now} WHERE id=#{id}")
    int updateCreationAttempts(@Param("attempts") int attempts, @Param("now") LocalDateTime now, @Param("id") long id);

    /**
     * 仅释放未支付、尚未释放的库存预占。
     */
    @Update("UPDATE stock_reservation SET status='RELEASED',release_reason=#{reason},update_time=#{now} "
            + "WHERE id=#{id} AND status IN ('RESERVED','ORDER_CREATED')")
    int releaseReservation(@Param("reason") String reason, @Param("now") LocalDateTime now, @Param("id") long id);

    /**
     * 预占释放后清除对应的购买占位。
     */
    @Update("UPDATE purchase_guard SET reservation_id=NULL,update_time=#{now} WHERE user_id=#{userId} "
            + "AND tier_id=#{tierId} AND reservation_id=#{id}")
    int clearPurchaseGuard(@Param("now") LocalDateTime now, @Param("userId") long userId, @Param("tierId") long tierId,
            @Param("id") long id);

    /**
     * 归还可售库存并递增版本号。
     */
    @Update("UPDATE ticket_tier SET available_stock=available_stock+#{quantity},"
            + "stock_version=stock_version+1 WHERE id=#{tierId}")
    int restoreAvailableStock(@Param("quantity") int quantity, @Param("tierId") long tierId);

    /**
     * 读取当前库存与版本号，用于刷新 Redis 投影。
     */
    @Select("SELECT available_stock,stock_version FROM ticket_tier WHERE id=#{tierId}")
    Map<String, Object> findStockProjection(@Param("tierId") long tierId);
}
