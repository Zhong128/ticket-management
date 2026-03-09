package org.example.ticketmanagement.pojo;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * 通用分页查询参数
 */
@Data
public class PageQuery {
    @Min(value = 1, message = "页码必须大于0")
    private Integer page = 1;          // 页码，默认第1页

    @Min(value = 1, message = "每页大小必须大于0")
    @Max(value = 200, message = "每页大小不能超过200")
    private Integer size = 10;         // 每页大小，默认10条

    private String sortBy = "create_time";  // 排序字段
    private String sortOrder = "DESC";      // 排序方向：ASC/DESC

    /**
     * 计算偏移量（用于SQL的OFFSET）
     */
    public Integer getOffset() {
        return (page - 1) * size;
    }

    /**
     * 获取排序SQL片段（防止SQL注入）
     */
    public String getOrderByClause() {
        // 安全的字段白名单
        String[] allowedSortFields = {
                "id", "create_time", "update_time",
                "name", "sort_order", "hot_level"
        };

        // 默认排序字段
        String safeSortBy = "create_time";
        for (String field : allowedSortFields) {
            if (field.equals(sortBy)) {
                safeSortBy = field;
                break;
            }
        }

        // 排序方向
        String safeOrder = "ASC".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";

        return safeSortBy + " " + safeOrder;
    }

    /**
     * 验证分页参数
     */
    public boolean validate() {
        return page != null && page > 0 && size != null && size > 0;
    }
}