// org/example/ticketmanagement/dto/PaymentResultDTO.java
package org.example.ticketmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 支付结果DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentResultDTO {
    private String paymentNo;       // 支付流水号
    private String orderNo;         // 订单号
    private String qrCode;          // 支付二维码（Base64或URL）
    private String paymentPageHtml; // 支付宝SDK返回的支付表单；旧qrCode字段保留兼容
    private BigDecimal totalAmount; // 支付金额
    private Integer payStatus;      // 支付状态
    private String payStatusDesc;   // 支付状态描述
    private String expireTime;      // 过期时间
}
