// org/example/ticketmanagement/dto/EventVO.java
package org.example.ticketmanagement.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventVO {
    private BigDecimal minPrice;
    private Long id;
    private String name;
    private String artistName;
    private Long categoryId;
    private Long cityId;
    private String venue;
    private String coverImage;
    private String description;
    private Integer status;
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
    private LocalDateTime eventStartTime;
    private LocalDateTime eventEndTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 可以添加关联信息（可选）
    private String categoryName;    // 分类名称（需要关联查询）
    private String cityName;        // 城市名称（需要关联查询）
}
