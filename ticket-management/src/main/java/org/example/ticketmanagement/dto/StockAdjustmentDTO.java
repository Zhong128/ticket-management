package org.example.ticketmanagement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StockAdjustmentDTO {

    @NotNull(message = "库存调整量不能为空")
    private Integer delta;
}
