package org.example.ticketmanagement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付方式DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PayTypeDTO {

    @NotNull(message = "支付方式不能为空")
    private Integer payType;        // 支付方式：1-支付宝
}
