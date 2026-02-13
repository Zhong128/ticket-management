// org/example/ticketmanagement/vo/OrderInfoVO.java
package org.example.ticketmanagement.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.ticketmanagement.constant.OrderConstant;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderInfoVO {
    private Long id;                    // 订单ID
    private String orderNo;            // 订单号
    private Long userId;               // 用户ID
    private String userName;           // 用户姓名（管理端用）
    private Long eventId;              // 演出ID
    private String eventName;          // 演出名称
    private String eventImage;         // 演出海报
    private Long sessionId;            // 场次ID
    private String sessionName;        // 场次名称
    private LocalDateTime sessionTime; // 场次时间
    private String venueName;          // 场馆名称
    private Long tierId;              // 票档ID
    private String tierName;          // 票档名称
    private BigDecimal tierPrice;     // 票档单价
    private Integer quantity;         // 购买数量
    private BigDecimal totalAmount;   // 订单总金额
    private Integer orderStatus;      // 订单状态
    private String orderStatusDesc;   // 订单状态描述

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;    // 支付时间

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime; // 创建时间

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime; // 更新时间

    public String getOrderStatusDesc() {
        return OrderConstant.getStatusDesc(this.orderStatus);
    }
}