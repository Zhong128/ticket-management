// org/example/ticketmanagement/dto/TicketTierQueryDTO.java
package org.example.ticketmanagement.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.example.ticketmanagement.pojo.PageQuery;

import java.math.BigDecimal;

/**
 * 票档查询参数 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TicketTierQueryDTO extends PageQuery {
    private Long eventId;           // 演出 ID
    private Long sessionId;         // 场次 ID
    private Long tierId;// 票档 ID
    private Integer status;         // 状态：0-禁用，1-启用
    private BigDecimal minPrice;    // 最低价格
    private BigDecimal maxPrice;    // 最高价格

    /**
     * 是否需要根据演出 ID 查询
     */
    public boolean needEventId() {
        return eventId != null;
    }

    /**
     * 是否需要根据场次 ID 查询
     */
    public boolean needSessionId() {
        return sessionId != null;
    }

    /**
     * 是否需要根据票档 ID 精确查询
     */
    public boolean needTierId() {return tierId != null;}

    /**
     * 是否需要根据价格范围查询
     */
    public boolean needPriceRange() {
        return minPrice != null && maxPrice != null;
    }
}
