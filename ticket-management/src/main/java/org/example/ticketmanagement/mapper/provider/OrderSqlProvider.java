// org/example/ticketmanagement/mapper/provider/OrderSqlProvider.java
package org.example.ticketmanagement.mapper.provider;

import org.apache.ibatis.jdbc.SQL;
import org.example.ticketmanagement.dto.OrderQueryDTO;
import org.example.ticketmanagement.pojo.OrderInfo;

/**
 * 订单动态SQL提供者
 * 参照CitySqlProvider风格
 */
public class OrderSqlProvider {

    /**
     * 构建动态查询SQL
     */
    public String selectByCondition(OrderQueryDTO queryDTO) {
        return new SQL() {{
            SELECT("o.id, o.order_no, o.user_id, o.event_id, o.session_id, o.tier_id, " +
                    "o.quantity, o.total_amount, o.order_status, o.pay_time, o.create_time, o.update_time");
            FROM("order_info o");

            // 动态添加WHERE条件
            buildWhereClause(this, queryDTO);

            // 排序
            if (queryDTO.getSortBy() != null && queryDTO.getSortOrder() != null) {
                ORDER_BY(queryDTO.getOrderByClause());
            } else {
                ORDER_BY("o.create_time DESC");
            }

            // 分页
            LIMIT("#{size}");
            OFFSET("#{offset}");
        }}.toString();
    }

    /**
     * 构建计数SQL
     */
    public String countByCondition(OrderQueryDTO queryDTO) {
        return new SQL() {{
            SELECT("COUNT(*)");
            FROM("order_info o");

            // 动态添加WHERE条件
            buildWhereClause(this, queryDTO);
        }}.toString();
    }

    public String updateOrderInfo(OrderInfo orderInfo) {
        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE order_info SET update_time = NOW()");

        if (orderInfo.getOrderStatus() != null) {
            sql.append(", order_status = #{orderStatus}");
        }
        if (orderInfo.getTotalAmount() != null) {
            sql.append(", total_amount = #{totalAmount}");
        }
        if (orderInfo.getQuantity() != null) {
            sql.append(", quantity = #{quantity}");
        }
        if (orderInfo.getPayTime() != null) {
            sql.append(", pay_time = #{payTime}");
        }

        sql.append(" WHERE id = #{id}");
        return sql.toString();
    }

    /**
     * 构建WHERE条件（公共逻辑）
     * 参照CitySqlProvider的buildWhereClause风格
     */
    private void buildWhereClause(SQL sql, OrderQueryDTO queryDTO) {
        // 1. 订单号模糊查询
        if (queryDTO.needOrderNoLike()) {
            sql.WHERE("o.order_no LIKE CONCAT('%', #{orderNo}, '%')");
        }

        // 2. 用户ID精确查询
        if (queryDTO.getUserId() != null) {
            sql.WHERE("o.user_id = #{userId}");
        }

        // 3. 演出ID精确查询
        if (queryDTO.getEventId() != null) {
            sql.WHERE("o.event_id = #{eventId}");
        }

        // 4. 场次ID精确查询
        if (queryDTO.getSessionId() != null) {
            sql.WHERE("o.session_id = #{sessionId}");
        }

        // 5. 票档ID精确查询
        if (queryDTO.getTierId() != null) {
            sql.WHERE("o.tier_id = #{tierId}");
        }

        // 6. 订单状态精确查询
        if (queryDTO.getOrderStatus() != null) {
            sql.WHERE("o.order_status = #{orderStatus}");
        }

        // 7. 创建时间范围查询
        if (queryDTO.getStartTime() != null) {
            sql.WHERE("o.create_time >= #{startTime}");
        }
        if (queryDTO.getEndTime() != null) {
            sql.WHERE("o.create_time <= #{endTime}");
        }

        // 8. 金额范围查询
        if (queryDTO.getMinAmount() != null) {
            sql.WHERE("o.total_amount >= #{minAmount}");
        }
        if (queryDTO.getMaxAmount() != null) {
            sql.WHERE("o.total_amount <= #{maxAmount}");
        }
    }

    /**
     * 构建管理端联表查询SQL（带演出名称、用户名称等）
     * 仅管理端使用，联表查询
     */
    public String selectAdminByCondition(OrderQueryDTO queryDTO) {
        return new SQL() {{
            SELECT("o.id, o.order_no, o.user_id, u.username as user_name, " +
                    "o.event_id, e.name as event_name, e.venue as venue_name, " +
                    "o.session_id, s.session_name, s.session_time, " +
                    "o.tier_id, t.tier_name, t.current_price as tier_price, " +
                    "o.quantity, o.total_amount, o.order_status, " +
                    "o.pay_time, o.create_time, o.update_time");
            FROM("order_info o");
            LEFT_OUTER_JOIN("user u ON o.user_id = u.id");
            LEFT_OUTER_JOIN("event e ON o.event_id = e.id");
            LEFT_OUTER_JOIN("event_session s ON o.session_id = s.id");
            LEFT_OUTER_JOIN("ticket_tier t ON o.tier_id = t.id");

            // 动态添加WHERE条件
            buildAdminWhereClause(this, queryDTO);

            // 排序
            if (queryDTO.getSortBy() != null && queryDTO.getSortOrder() != null) {
                ORDER_BY("o." + queryDTO.getOrderByClause());
            } else {
                ORDER_BY("o.create_time DESC");
            }

            // 分页
            LIMIT("#{size}");
            OFFSET("#{offset}");
        }}.toString();
    }

    /**
     * 构建管理端计数SQL（联表）
     */
    public String countAdminByCondition(OrderQueryDTO queryDTO) {
        return new SQL() {{
            SELECT("COUNT(*)");
            FROM("order_info o");
            LEFT_OUTER_JOIN("user u ON o.user_id = u.id");
            LEFT_OUTER_JOIN("event e ON o.event_id = e.id");

            // 动态添加WHERE条件
            buildAdminWhereClause(this, queryDTO);
        }}.toString();
    }

    /**
     * 构建管理端WHERE条件（支持演出名称、用户名称模糊查询）
     */
    private void buildAdminWhereClause(SQL sql, OrderQueryDTO queryDTO) {
        // 1. 先调用基础条件
        buildWhereClause(sql, queryDTO);

        // 2. 演出名称模糊查询（联表）
        if (queryDTO.needEventNameLike()) {
            sql.WHERE("e.name LIKE CONCAT('%', #{eventName}, '%')");
        }

        // 3. 用户名称模糊查询（联表）- 管理端特有
        if (queryDTO.getUserName() != null && !queryDTO.getUserName().trim().isEmpty()) {
            sql.WHERE("u.username LIKE CONCAT('%', #{userName}, '%')");
        }

        // 4. 用户手机号查询（管理端）
        if (queryDTO.getUserPhone() != null && !queryDTO.getUserPhone().trim().isEmpty()) {
            sql.WHERE("u.phone LIKE CONCAT('%', #{userPhone}, '%')");
        }
    }
}