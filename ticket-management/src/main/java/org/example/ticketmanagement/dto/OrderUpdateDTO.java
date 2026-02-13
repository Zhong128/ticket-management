// org/example/ticketmanagement/dto/OrderUpdateDTO.java
package org.example.ticketmanagement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderUpdateDTO {

    @NotNull(message = "订单状态不能为空")
    @Min(value = 0, message = "订单状态必须为0-3")
    @Max(value = 3, message = "订单状态必须为0-3")
    private Integer orderStatus;     // 订单状态

    private BigDecimal totalAmount;  // 订单金额（可选修改）

    private Integer quantity;        // 购买数量（可选修改）

    private String remark;           // 修改备注（用于日志记录）
}