// org/example/ticketmanagement/vo/OrderInfoVO.java
package org.example.ticketmanagement.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderInfoVO {
    private Long id;                    // 订单ID
    private String orderNo;            // 订单号
    private Long userId;               // 用户ID
    private Long eventId;              // 演出ID
    private String eventName;          // 演出名称
    private Long sessionId;            // 场次ID
    private String sessionName;        // 场次名称
    private LocalDateTime sessionTime; // 场次时间
    private Long tierId;               // 票档ID
    private String tierName;           // 票档名称
    private Integer quantity;          // 购买数量
    private BigDecimal totalAmount;    // 订单总金额
    private Integer orderStatus;       // 订单状态
    private String orderStatusDesc;    // 订单状态描述
    private LocalDateTime payTime;     // 支付时间
    private LocalDateTime createTime;  // 创建时间
    private LocalDateTime updateTime;  // 更新时间

    // 根据状态码返回描述
    public String getOrderStatusDesc() {
        if (orderStatus == null) return "未知";
        return switch (orderStatus) {
            case 0 -> "待支付";
            case 1 -> "已支付";
            case 2 -> "已取消";
            case 3 -> "已完成";
            default -> "未知";
        };
    }
}