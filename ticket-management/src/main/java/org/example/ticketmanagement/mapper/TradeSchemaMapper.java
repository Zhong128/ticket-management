package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 交易表结构的只读检查，不负责建表或迁移。
 */
@Mapper
public interface TradeSchemaMapper {

    /**
     * 检查库存预占表是否存在。
     */
    @Select("SELECT * FROM stock_reservation WHERE 1=0")
    List<Map<String, Object>> checkReservations();

    /**
     * 检查购买占位表是否存在。
     */
    @Select("SELECT * FROM purchase_guard WHERE 1=0")
    List<Map<String, Object>> checkPurchaseGuards();

    /**
     * 检查消息 Outbox 表是否存在。
     */
    @Select("SELECT * FROM message_outbox WHERE 1=0")
    List<Map<String, Object>> checkOutbox();

    /**
     * 检查退款任务表是否存在。
     */
    @Select("SELECT * FROM refund_record WHERE 1=0")
    List<Map<String, Object>> checkRefunds();

    /**
     * 检查订单交易字段是否齐全。
     */
    @Select("SELECT expire_time,active_payment_id,cancel_reason FROM order_info WHERE 1=0")
    List<Map<String, Object>> checkOrderColumns();

    /**
     * 检查票档库存版本字段是否存在。
     */
    @Select("SELECT stock_version FROM ticket_tier WHERE 1=0")
    List<Map<String, Object>> checkStockColumns();

    /**
     * 检查补偿任务新增字段是否齐全。
     */
    @Select("SELECT task_type,business_key,payload,lease_token,lease_until FROM payment_compensation "
            + "WHERE 1=0")
    List<Map<String, Object>> checkCompensationColumns();

    /**
     * 确认包含 Outbox 租约索引的迁移版本已通过校验。
     */
    @Select("SELECT COUNT(*) FROM flyway_schema_history WHERE version='4' AND success=1")
    int countVerifiedSchemaVersion();
}
