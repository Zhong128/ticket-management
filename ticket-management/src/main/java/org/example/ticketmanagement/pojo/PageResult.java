package org.example.ticketmanagement.pojo;

import lombok.Data;
import java.util.List;

/**
 * 通用分页结果
 */
@Data
public class PageResult<T> {
    private List<T> records;      // 当前页数据列表
    private Long total;           // 总记录数
    private Integer page;         // 当前页码
    private Integer size;         // 每页大小
    private Integer totalPages;   // 总页数
    private Boolean hasNext;      // 是否有下一页
    private Boolean hasPrevious;  // 是否有上一页

    public PageResult() {}

    public PageResult(List<T> records, Long total, PageQuery query) {
        this(records, total, query.getPage(), query.getSize());
    }

    public PageResult(List<T> records, Long total, Integer page, Integer size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;

        // 计算总页数
        this.totalPages = (int) Math.ceil((double) total / size);

        // 是否有下一页/上一页
        this.hasNext = page < totalPages;
        this.hasPrevious = page > 1;
    }

    /**
     * 创建成功分页结果
     */
    public static <T> PageResult<T> success(List<T> records, Long total, PageQuery query) {
        return new PageResult<>(records, total, query);
    }

    /**
     * 创建空分页结果
     */
    public static <T> PageResult<T> empty(PageQuery query) {
        return new PageResult<>(List.of(), 0L, query);
    }

    /**
     * 判断是否为空
     */
    public boolean isEmpty() {
        return records == null || records.isEmpty();
    }
}