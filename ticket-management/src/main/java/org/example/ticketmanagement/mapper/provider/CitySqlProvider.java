package org.example.ticketmanagement.mapper.provider;

import org.apache.ibatis.jdbc.SQL;
import org.example.ticketmanagement.dto.CityQueryDTO;

/**
 * 城市动态SQL提供者
 */
public class CitySqlProvider {

    /**
     * 构建动态查询SQL
     */
    public String selectByCondition(CityQueryDTO queryDTO) {
        return new SQL() {{
            SELECT("*");
            FROM("city");

            // 动态添加WHERE条件
            buildWhereClause(this, queryDTO);

            // 排序
            if (queryDTO.getSortBy() != null && queryDTO.getSortOrder() != null) {
                ORDER_BY(queryDTO.getOrderByClause());
            } else {
                ORDER_BY("hot_level DESC, create_time DESC");
            }

            // 分页
            LIMIT("#{size}");
            OFFSET("#{offset}");
        }}.toString();
    }

    /**
     * 构建计数SQL
     */
    public String countByCondition(CityQueryDTO queryDTO) {
        return new SQL() {{
            SELECT("COUNT(*)");
            FROM("city");

            // 动态添加WHERE条件
            buildWhereClause(this, queryDTO);
        }}.toString();
    }

    /**
     * 构建WHERE条件（公共逻辑）
     */
    private void buildWhereClause(SQL sql, CityQueryDTO queryDTO) {
        if (queryDTO.getStatus() != null) {
            sql.WHERE("status = #{status}");
        }

        if (queryDTO.getHotLevel() != null) {
            sql.WHERE("hot_level = #{hotLevel}");
        }

        if (queryDTO.needNameLike()) {
            sql.WHERE("name LIKE CONCAT('%', #{name}, '%')");
        }

        if (queryDTO.needCodeExact()) {
            sql.WHERE("code = #{code}");
        }

        if (queryDTO.needProvince()) {
            sql.WHERE("province LIKE CONCAT('%', #{province}, '%')");
        }
    }
}