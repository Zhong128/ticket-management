// org/example/ticketmanagement/mapper/OrderInfoMapper.java
package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.ticketmanagement.pojo.OrderInfo;

import java.util.List;

@Mapper
public interface OrderInfoMapper {

    /**
     * 插入订单
     */
    @Insert("INSERT INTO order_info(order_no, user_id, event_id, session_id, tier_id, " +
            "quantity, total_amount, order_status, pay_time, create_time, update_time) " +
            "VALUES(#{orderNo}, #{userId}, #{eventId}, #{sessionId}, #{tierId}, " +
            "#{quantity}, #{totalAmount}, #{orderStatus}, #{payTime}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OrderInfo orderInfo);

    /**
     * 根据订单号查询订单
     */
    @Select("SELECT id, order_no, user_id, event_id, session_id, tier_id, " +
            "quantity, total_amount, order_status, pay_time, create_time, update_time " +
            "FROM order_info WHERE order_no = #{orderNo}")
    OrderInfo selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 根据订单ID查询订单
     */
    @Select("SELECT id, order_no, user_id, event_id, session_id, tier_id, " +
            "quantity, total_amount, order_status, pay_time, create_time, update_time " +
            "FROM order_info WHERE id = #{id}")
    OrderInfo selectById(@Param("id") Long id);

    /**
     * 根据用户ID查询订单列表
     */
    @Select("SELECT id, order_no, user_id, event_id, session_id, tier_id, " +
            "quantity, total_amount, order_status, pay_time, create_time, update_time " +
            "FROM order_info WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<OrderInfo> selectByUserId(@Param("userId") Long userId);

    /**
     * 更新订单状态
     */
    @Update("UPDATE order_info SET order_status = #{orderStatus}, " +
            "pay_time = #{payTime}, update_time = #{updateTime} " +
            "WHERE id = #{id}")
    int updateStatus(OrderInfo orderInfo);

    /**
     * 根据订单号更新订单状态
     */
    @Update("UPDATE order_info SET order_status = #{orderStatus}, " +
            "update_time = #{updateTime} WHERE order_no = #{orderNo}")
    int updateStatusByOrderNo(@Param("orderNo") String orderNo,
                              @Param("orderStatus") Integer orderStatus,
                              @Param("updateTime") java.time.LocalDateTime updateTime);

    /**
     * 删除订单（测试用）
     */
    @Delete("DELETE FROM order_info WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /**
     * 查询超时未支付订单
     */
    @Select("SELECT id, order_no, user_id, event_id, session_id, tier_id, " +
            "quantity, total_amount, order_status, pay_time, create_time, update_time " +
            "FROM order_info WHERE order_status = 0 " +
            "AND create_time < DATE_SUB(NOW(), INTERVAL #{timeoutMinutes} MINUTE) " +
            "LIMIT #{limit}")
    List<OrderInfo> selectTimeoutOrders(@Param("timeoutMinutes") Integer timeoutMinutes,
                                        @Param("limit") Integer limit);
}