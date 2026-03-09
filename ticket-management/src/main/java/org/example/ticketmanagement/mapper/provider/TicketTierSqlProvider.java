// org/example/ticketmanagement/mapper/provider/TicketTierSqlProvider.java
package org.example.ticketmanagement.mapper.provider;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.jdbc.SQL;
import org.example.ticketmanagement.dto.TicketTierQueryDTO;

import java.math.BigDecimal;

public class TicketTierSqlProvider {


    /**
     * 分页查询票档的动态 SQL
     */
    public String selectByCondition(TicketTierQueryDTO queryDTO) {
        return new SQL() {{
            SELECT("id, event_id, session_id, tier_name, original_price, current_price, " +
                    "total_stock, available_stock, status, create_time, update_time");
            FROM("ticket_tier");

            // 构建动态 WHERE 条件
            buildWhereClause(this, queryDTO);

            ORDER_BY("current_price ASC, create_time DESC");
        }}.toString();
    }

    /**
     * 统计符合条件的票档数量
     */
    public String countByCondition(org.example.ticketmanagement.dto.TicketTierQueryDTO queryDTO) {
        return new SQL() {{
            SELECT("COUNT(*)");
            FROM("ticket_tier");

            // 构建动态 WHERE 条件
            buildWhereClause(this, queryDTO);
        }}.toString();
    }

    /**
     * 构建动态 WHERE 条件（公共逻辑）
     */
    private void buildWhereClause(SQL sql,TicketTierQueryDTO queryDTO) {
        // 1. 根据票档 ID 精确查询（优先级最高）
        if (queryDTO.getTierId() != null) {
            sql.WHERE("id = #{tierId}");
        } else {
            // 2. 如果没有指定 tierId，才应用其他条件
            
            // 默认查询启用状态，除非明确指定其他状态
            if (queryDTO.getStatus() != null) {
                sql.WHERE("status = #{status}");
            } else {
                sql.WHERE("status = 1");
            }

            // 根据演出 ID 查询
            if (queryDTO.needEventId()) {
                sql.WHERE("event_id = #{eventId}");
            }

            // 根据场次 ID 查询
            if (queryDTO.needSessionId()) {
                sql.WHERE("session_id = #{sessionId}");
            }

            // 根据价格范围查询
            if (queryDTO.needPriceRange()) {
                sql.WHERE("current_price BETWEEN #{minPrice} AND #{maxPrice}");
            } else if (queryDTO.getMinPrice() != null) {
                sql.WHERE("current_price >= #{minPrice}");
            } else if (queryDTO.getMaxPrice() != null) {
                sql.WHERE("current_price <= #{maxPrice}");
            }
        }
    }

    /**
     * 根据价格范围和状态查询票档
     */

}