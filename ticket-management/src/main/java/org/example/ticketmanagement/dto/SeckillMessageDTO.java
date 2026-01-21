// org/example/ticketmanagement/dto/SeckillMessageDTO.java
package org.example.ticketmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeckillMessageDTO {
    private Long userId;           // 用户ID
    private Long eventId;          // 演出ID
    private Long sessionId;        // 场次ID
    private Long tierId;           // 票档ID
    private String tierName;       // 票档名称
    private BigDecimal price;      // 单价
    private Integer quantity;      // 购买数量
    private BigDecimal totalAmount; // 总金额
    private String orderNo;        // 订单号
    private Long requestId;        // 请求ID（用于幂等性）
    private Long timestamp;        // 时间戳
}