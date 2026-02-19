// org/example/ticketmanagement/dto/CreatePaymentDTO.java
package org.example.ticketmanagement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建支付订单DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreatePaymentDTO {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;           // 订单ID

    @NotNull(message = "支付方式不能为空")
    private Integer payType;        // 支付方式：1-支付宝
}