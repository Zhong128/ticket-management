package org.example.ticketmanagement.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)  // 忽略未知字段
public class PayTypeDTO {

    @NotNull(message = "支付方式不能为空")
    private Integer payType;        // 支付方式：1-支付宝
    
    // 新增orderId字段（可选）
    private Long orderId;
}
