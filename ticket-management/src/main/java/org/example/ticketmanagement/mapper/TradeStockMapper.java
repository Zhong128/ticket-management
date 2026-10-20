package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 库存维护查询，库存变更由 Service 事务统一控制。
 */
@Mapper
public interface TradeStockMapper {

    /**
     * 列出需要刷新库存投影的票档。
     */
    @Select("SELECT id FROM ticket_tier")
    List<Long> selectTierIds();

    /**
     * 读取票档的可售库存和版本号。
     */
    @Select("SELECT available_stock,stock_version FROM ticket_tier WHERE id=#{tierId}")
    Map<String, Object> selectStock(@Param("tierId") long tierId);

    /**
     * 按票档读取当前可售库存。
     */
    @Select("SELECT available_stock FROM ticket_tier WHERE id=#{tierId}")
    Integer selectAvailableStock(@Param("tierId") long tierId);

    /**
     * 锁定票档，串行处理容量调整。
     */
    @Select("SELECT * FROM ticket_tier WHERE id=#{tierId} FOR UPDATE")
    Map<String, Object> lockTier(@Param("tierId") long tierId);

    /**
     * 统计用户在票档下的有效订单。
     */
    @Select("SELECT COUNT(*) FROM order_info WHERE user_id=#{userId} AND tier_id=#{tierId} AND "
            + "order_status IN(0,1,3,4)")
    long countUserActiveOrders(@Param("userId") long userId, @Param("tierId") long tierId);

    /**
     * 统计尚未完成的库存预占。
     */
    @Select("SELECT COUNT(*) FROM stock_reservation WHERE tier_id=#{tierId} AND status IN('RESERVED',"
            + "'ORDER_CREATED')")
    long countUnsettledReservations(@Param("tierId") long tierId);

    /**
     * 统计尚未完成的关联订单。
     */
    @Select("SELECT COUNT(*) FROM order_info WHERE tier_id=#{tierId} AND order_status IN(0,4)")
    long countUnsettledOrders(@Param("tierId") long tierId);

    /**
     * 统计占用库存的预占记录。
     */
    @Select("SELECT COUNT(*) FROM stock_reservation WHERE tier_id=#{tierId} AND status IN('RESERVED',"
            + "'ORDER_CREATED','PAID')")
    long countActiveReservations(@Param("tierId") long tierId);

    /**
     * 统计占用库存的订单。
     */
    @Select("SELECT COUNT(*) FROM order_info WHERE tier_id=#{tierId} AND order_status IN(0,1,3,4)")
    long countActiveOrders(@Param("tierId") long tierId);

    /**
     * 调整票档容量及可售库存，并递增版本号。
     */
    @Update("UPDATE ticket_tier SET total_stock=total_stock+#{delta},"
            + "available_stock=available_stock+#{delta},stock_version=stock_version+1,update_time=#{now} "
            + "WHERE id=#{tierId}")
    int adjustCapacity(@Param("tierId") long tierId, @Param("delta") int delta, @Param("now") LocalDateTime now);
}
