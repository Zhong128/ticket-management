// org/example/ticketmanagement/dto/OrderUpdateDTO.java
package org.example.ticketmanagement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderUpdateDTO {

    @Min(value = 0, message = "订单状态必须为0-3")
    @Max(value = 4, message = "订单状态必须为0-4")
    // 订单状态
    private Integer orderStatus;

    // 订单金额（可选修改）
    private BigDecimal totalAmount;
    // 购买数量（可选修改）
    private Integer quantity;
    // 修改备注（用于日志记录）
    private String remark;
}
