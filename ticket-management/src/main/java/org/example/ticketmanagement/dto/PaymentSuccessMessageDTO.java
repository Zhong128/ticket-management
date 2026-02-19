// org/example/ticketmanagement/dto/PaymentSuccessMessageDTO.java
package org.example.ticketmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付成功消息DTO（用于RabbitMQ）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentSuccessMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String paymentNo;       // 支付流水号
    private String tradeNo;         // 支付宝交易号
    private String orderNo;         // 订单号
    private Long orderId;           // 订单ID
    private Long userId;            // 用户ID
    private String userEmail;       // 用户邮箱
    private BigDecimal totalAmount; // 支付金额
    private LocalDateTime payTime;  // 支付时间
    private String eventName;       // 演出名称
    private String sessionName;     // 场次名称
    private String tierName;        // 票档名称
    private Integer quantity;       // 购买数量
}