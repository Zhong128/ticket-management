package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TradeOrderQueryMapper {

    /**
     * 按订单号和用户查询关联的预占编号。
     */
    @Select("SELECT id FROM stock_reservation WHERE order_no=#{orderNo} AND user_id=#{userId}")
    List<Long> selectReservationIds(@Param("orderNo") String orderNo, @Param("userId") long userId);

    /**
     * 更新订单备注及修改时间。
     */
    @Update("UPDATE order_info SET remark=#{remark},update_time=#{now} WHERE id=#{orderId}")
    int updateRemark(@Param("orderId") long orderId, @Param("remark") String remark, @Param("now") LocalDateTime now);
}
