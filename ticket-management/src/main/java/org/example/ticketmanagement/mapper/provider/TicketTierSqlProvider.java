// org/example/ticketmanagement/mapper/provider/TicketTierSqlProvider.java
package org.example.ticketmanagement.mapper.provider;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.jdbc.SQL;
import java.math.BigDecimal;

public class TicketTierSqlProvider {

    /**
     * 根据条件查询票档的动态SQL
     */
    public String selectByCondition(@Param("eventId") Long eventId,
                                    @Param("sessionId") Long sessionId,
                                    @Param("tierId") Long tierId) {
        return new SQL() {{
            SELECT("id, event_id, session_id, tier_name, original_price, current_price, " +
                    "total_stock, available_stock, status, create_time, update_time");
            FROM("ticket_tier");

            if (tierId != null) {
                WHERE("id = #{tierId}");
            }
            if (sessionId != null) {
                WHERE("session_id = #{sessionId}");
            }
            if (eventId != null) {
                WHERE("event_id = #{eventId}");
            }

            ORDER_BY("event_id, session_id, current_price ASC");
        }}.toString();
    }

    /**
     * 根据价格范围和状态查询票档
     */
    public String selectByPriceRangeAndStatus(@Param("minPrice") BigDecimal minPrice,
                                              @Param("maxPrice") BigDecimal maxPrice,
                                              @Param("status") Integer status) {
        return new SQL() {{
            SELECT("id, event_id, session_id, tier_name, original_price, current_price, " +
                    "total_stock, available_stock, status, create_time, update_time");
            FROM("ticket_tier");
            WHERE("status = #{status}");

            if (minPrice != null && maxPrice != null) {
                WHERE("current_price BETWEEN #{minPrice} AND #{maxPrice}");
            } else if (minPrice != null) {
                WHERE("current_price >= #{minPrice}");
            } else if (maxPrice != null) {
                WHERE("current_price <= #{maxPrice}");
            }

            ORDER_BY("current_price ASC");
        }}.toString();
    }
}