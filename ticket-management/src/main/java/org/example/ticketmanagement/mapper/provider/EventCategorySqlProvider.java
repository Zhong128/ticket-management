package org.example.ticketmanagement.mapper.provider;

import org.apache.ibatis.jdbc.SQL;
import org.example.ticketmanagement.dto.EventCategoryQueryDTO;

/**
 * 分类动态SQL提供者
 */
public class EventCategorySqlProvider {

    /**
     * 构建动态查询SQL
     */
    public String selectByCondition(EventCategoryQueryDTO queryDTO) {
        return new SQL() {{
            SELECT("*");
            FROM("event_category");

            // 动态添加WHERE条件
            buildWhereClause(this, queryDTO);

            // 排序
            if (queryDTO.getSortBy() != null && queryDTO.getSortOrder() != null) {
                ORDER_BY(queryDTO.getOrderByClause());
            } else {
                ORDER_BY("sort_order ASC, create_time DESC");
            }

            // 分页
            LIMIT("#{size}");
            OFFSET("#{offset}");
        }}.toString();
    }

    /**
     * 构建计数SQL
     */
    public String countByCondition(EventCategoryQueryDTO queryDTO) {
        return new SQL() {{
            SELECT("COUNT(*)");
            FROM("event_category");

            // 动态添加WHERE条件
            buildWhereClause(this, queryDTO);
        }}.toString();
    }

    /**
     * 构建 WHERE 条件（公共逻辑）
     */
    private void buildWhereClause(SQL sql, EventCategoryQueryDTO queryDTO) {
        // 默认查询启用状态，除非明确指定其他状态
        if (queryDTO.getStatus() != null) {
            sql.WHERE("status = #{status}");
        } else {
            // 如果没有传 status 参数，默认查询 status=1
            sql.WHERE("status = 1");
        }

        // 名称模糊匹配
        if (queryDTO.needNameLike()) {
            sql.WHERE("name LIKE CONCAT('%', #{name}, '%')");
        }

        // 排序范围
        if (queryDTO.needSortRange()) {
            sql.WHERE("sort_order BETWEEN #{minSort} AND #{maxSort}");
        }
    }
}