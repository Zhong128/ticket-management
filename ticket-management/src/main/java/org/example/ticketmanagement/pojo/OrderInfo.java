// org/example/ticketmanagement/pojo/OrderInfo.java
package org.example.ticketmanagement.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderInfo {
    private Long id;                    // 订单ID
    private String orderNo;            // 订单号（唯一）
    private Long userId;               // 用户ID
    private Long eventId;              // 演出ID
    private Long sessionId;            // 场次ID
    private Long tierId;               // 票档ID
    private Integer quantity;          // 购买数量
    private BigDecimal totalAmount;    // 订单总金额
    private Integer orderStatus;       // 订单状态：0-待支付，1-已支付，2-已取消，3-已完成
    private LocalDateTime payTime;     // 支付时间
    private LocalDateTime createTime;  // 创建时间
    private LocalDateTime updateTime;  // 更新时间
}