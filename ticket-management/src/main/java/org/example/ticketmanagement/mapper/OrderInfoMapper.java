// org/example/ticketmanagement/mapper/OrderInfoMapper.java
package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.ticketmanagement.dto.OrderQueryDTO;
import org.example.ticketmanagement.mapper.provider.OrderSqlProvider;
import org.example.ticketmanagement.pojo.OrderInfo;
import org.example.ticketmanagement.vo.OrderInfoVO;

import java.util.List;

@Mapper
public interface OrderInfoMapper {

    // ==================== 基础CRUD（保持原有） ====================

    @Insert("INSERT INTO order_info(order_no, user_id, event_id, session_id, tier_id, " +
            "quantity, total_amount, order_status, pay_time, create_time, update_time) " +
            "VALUES(#{orderNo}, #{userId}, #{eventId}, #{sessionId}, #{tierId}, " +
            "#{quantity}, #{totalAmount}, #{orderStatus}, #{payTime}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OrderInfo orderInfo);

    @Select("SELECT * FROM order_info WHERE order_no = #{orderNo}")
    @Results(id = "orderResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "order_no", property = "orderNo"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "event_id", property = "eventId"),
            @Result(column = "session_id", property = "sessionId"),
            @Result(column = "tier_id", property = "tierId"),
            @Result(column = "quantity", property = "quantity"),
            @Result(column = "total_amount", property = "totalAmount"),
            @Result(column = "order_status", property = "orderStatus"),
            @Result(column = "pay_time", property = "payTime"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    OrderInfo selectByOrderNo(@Param("orderNo") String orderNo);


    @Select("SELECT * FROM order_info WHERE user_id = #{userId} AND order_status = #{status} ORDER BY create_time DESC LIMIT 1")
    @ResultMap("orderResultMap")
    OrderInfo selectByUserIdAndStatus(@Param("userId") Long userId, @Param("status") Integer status);



    @Select("SELECT * FROM order_info WHERE id = #{id}")
    @ResultMap("orderResultMap")
    OrderInfo selectById(@Param("id") Long id);



    @Select("SELECT * FROM order_info WHERE user_id = #{userId} ORDER BY create_time DESC")
    @ResultMap("orderResultMap")
    List<OrderInfo> selectByUserId(@Param("userId") Long userId);



    @Update("UPDATE order_info SET order_status = #{orderStatus}, " +
            "pay_time = #{payTime}, update_time = #{updateTime} " +
            "WHERE id = #{id}")
    int updateStatus(OrderInfo orderInfo);



    @UpdateProvider(type = OrderSqlProvider.class, method = "updateOrderInfo")
    int updateOrderInfo(OrderInfo orderInfo);



    @Delete("DELETE FROM order_info WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    // ==================== 动态分页查询（使用SQL Provider）====================

    /**
     * 1. 客户端分页查询订单（只查order_info表）
     * 对应OrderSqlProvider.selectByCondition
     */
    @SelectProvider(type = OrderSqlProvider.class, method = "selectByCondition")
    @ResultMap("orderResultMap")
    List<OrderInfo> selectByCondition(OrderQueryDTO queryDTO);


    /**
     * 2. 统计客户端订单总数
     * 对应OrderSqlProvider.countByCondition
     */
    @SelectProvider(type = OrderSqlProvider.class, method = "countByCondition")
    Long countByCondition(OrderQueryDTO queryDTO);


    /**
     * 3. 管理端分页查询订单（联表查询，带演出、用户信息）
     * 对应OrderSqlProvider.selectAdminByCondition
     */
    @SelectProvider(type = OrderSqlProvider.class, method = "selectAdminByCondition")
    @Results(id = "adminOrderResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "order_no", property = "orderNo"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "user_name", property = "userName"),
            @Result(column = "event_id", property = "eventId"),
            @Result(column = "event_name", property = "eventName"),
            @Result(column = "session_id", property = "sessionId"),
            @Result(column = "session_name", property = "sessionName"),
            @Result(column = "session_time", property = "sessionTime"),
            @Result(column = "venue_name", property = "venueName"),
            @Result(column = "tier_id", property = "tierId"),
            @Result(column = "tier_name", property = "tierName"),
            @Result(column = "tier_price", property = "tierPrice"),
            @Result(column = "quantity", property = "quantity"),
            @Result(column = "total_amount", property = "totalAmount"),
            @Result(column = "order_status", property = "orderStatus"),
            @Result(column = "pay_time", property = "payTime"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    List<OrderInfoVO> selectAdminByCondition(OrderQueryDTO queryDTO);

    /**
     * 4. 统计管理端订单总数
     * 对应OrderSqlProvider.countAdminByCondition
     */
    @SelectProvider(type = OrderSqlProvider.class, method = "countAdminByCondition")
    Long countAdminByCondition(OrderQueryDTO queryDTO);

    // ==================== 其他查询（保持原有）====================

    @Select("SELECT * FROM order_info WHERE order_status = 0 " +
            "AND create_time < DATE_SUB(NOW(), INTERVAL #{timeoutMinutes} MINUTE) " +
            "LIMIT #{limit}")
    @ResultMap("orderResultMap")
    List<OrderInfo> selectTimeoutOrders(@Param("timeoutMinutes") Integer timeoutMinutes,
                                        @Param("limit") Integer limit);

}