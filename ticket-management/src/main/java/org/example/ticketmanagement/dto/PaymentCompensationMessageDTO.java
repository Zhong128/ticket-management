// org/example/ticketmanagement/dto/PaymentCompensationMessageDTO.java
package org.example.ticketmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 支付补偿消息DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentCompensationMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String paymentNo;       // 支付流水号
    private String orderNo;         // 订单号
    private Long orderId;           // 订单ID
    private Integer retryCount;     // 重试次数
    private Long timestamp;         // 时间戳
}