// org/example/ticketmanagement/dto/EventQueryDTO.java
package org.example.ticketmanagement.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Size;
import org.example.ticketmanagement.pojo.PageQuery;

/**
 * 演出分页查询DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EventQueryDTO extends PageQuery {
    @Size(max = 100, message = "搜索关键词最多100字")
    private String keyword;        // 同时检索演出名称或艺人名称
    private String name;           // 演出名称（模糊查询）
    private String artistName;     // 艺人名称（模糊查询）
    private Long categoryId;       // 分类ID
    private Long cityId;           // 城市ID
    private Integer status;        // 状态：0-草稿，1-已发布，2-已结束
}
