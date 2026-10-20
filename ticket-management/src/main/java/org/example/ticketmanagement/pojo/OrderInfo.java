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

    private LocalDateTime expireTime;
    private Long activePaymentId;
    private String cancelReason;
    private String remark;
    // 订单ID
    private Long id;
    // 订单号（唯一）
    private String orderNo;
    // 用户ID
    private Long userId;
    // 演出ID
    private Long eventId;
    // 场次ID
    private Long sessionId;
    // 票档ID
    private Long tierId;
    // 购买数量
    private Integer quantity;
    // 订单总金额
    private BigDecimal totalAmount;
    // 订单状态：0-待支付，1-已支付，2-已取消，3-已完成
    private Integer orderStatus;
    // 支付时间
    private LocalDateTime payTime;
    // 创建时间
    private LocalDateTime createTime;
    // 更新时间
    private LocalDateTime updateTime;
}
