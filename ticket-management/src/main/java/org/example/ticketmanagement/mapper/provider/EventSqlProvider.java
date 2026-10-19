package org.example.ticketmanagement.mapper.provider;

import org.apache.ibatis.jdbc.SQL;
import org.example.ticketmanagement.dto.EventQueryDTO;

public class EventSqlProvider {

    /**
     * 构建动态查询 SQL
     */
    public String selectByCondition(EventQueryDTO queryDTO) {
        return new SQL() {{
            SELECT("*");
            FROM("event");

            // 动态添加 WHERE 条件
            buildWhereClause(this, queryDTO);

            // 排序
            ORDER_BY("event_start_time DESC, create_time DESC");

            // 分页
            if (queryDTO.getSize() != null && queryDTO.getOffset() != null) {
                LIMIT("#{queryDTO.size}");
                OFFSET("#{queryDTO.offset}");
            }
        }}.toString();
    }

    /**
     * 构建计数 SQL
     */
    public String countByCondition(EventQueryDTO queryDTO) {
        return new SQL() {{
            SELECT("COUNT(*)");
            FROM("event");

            // 动态添加 WHERE 条件
            buildWhereClause(this, queryDTO);
        }}.toString();
    }

    /**
     * 构建 WHERE 条件（公共逻辑）
     */
    private void buildWhereClause(SQL sql, EventQueryDTO queryDTO) {
        if (queryDTO.getName() != null && !queryDTO.getName().isEmpty()) {
            sql.WHERE("name LIKE CONCAT('%', #{queryDTO.name}, '%')");
        }

        if (queryDTO.getArtistName() != null && !queryDTO.getArtistName().isEmpty()) {
            sql.WHERE("artist_name LIKE CONCAT('%', #{queryDTO.artistName}, '%')");
        }

        if (queryDTO.getCategoryId() != null) {
            sql.WHERE("category_id = #{queryDTO.categoryId}");
        }

        if (queryDTO.getCityId() != null) {
            sql.WHERE("city_id = #{queryDTO.cityId}");
        }

        if (queryDTO.getStatus() != null) {
            sql.WHERE("status = #{queryDTO.status}");
        }
    }
}