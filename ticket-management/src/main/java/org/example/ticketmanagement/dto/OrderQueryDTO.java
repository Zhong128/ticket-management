// org/example/ticketmanagement/dto/OrderQueryDTO.java
package org.example.ticketmanagement.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.example.ticketmanagement.pojo.PageQuery;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderQueryDTO extends PageQuery {
    private String orderNo;           // 订单号（模糊查询）
    private Long userId;              // 用户ID
    private String userName;         // 用户名称（管理端模糊查询）
    private String userPhone;        // 用户手机号（管理端查询）
    private Long eventId;            // 演出ID
    private String eventName;        // 演出名称（管理端模糊查询）
    private Long sessionId;          // 场次ID
    private Long tierId;             // 票档ID
    private Integer orderStatus;     // 订单状态
    private LocalDateTime startTime; // 创建开始时间
    private LocalDateTime endTime;   // 创建结束时间
    private BigDecimal minAmount;    // 最小金额
    private BigDecimal maxAmount;    // 最大金额

    // 参照CityQueryDTO的needXxx方法风格
    public boolean needOrderNoLike() {
        return orderNo != null && !orderNo.trim().isEmpty();
    }

    public boolean needEventNameLike() {
        return eventName != null && !eventName.trim().isEmpty();
    }
}