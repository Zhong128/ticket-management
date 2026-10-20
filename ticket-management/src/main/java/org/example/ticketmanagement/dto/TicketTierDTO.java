// org/example/ticketmanagement/dto/TicketTierDTO.java
package org.example.ticketmanagement.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketTierDTO {

    public interface Create {
    }

    @NotNull(message = "演出ID不能为空")
    private Long eventId;

    @NotNull(message = "场次ID不能为空")
    private Long sessionId;

    @NotBlank(message = "票档名称不能为空")
    @Size(max = 100, message = "票档名称不能超过100个字符")
    private String tierName;

    @NotNull(message = "原价不能为空")
    @DecimalMin(value = "0.00", message = "原价必须大于等于0")
    @Digits(integer = 10, fraction = 2, message = "原价格式不正确")
    private BigDecimal originalPrice;

    @NotNull(message = "现价不能为空")
    @DecimalMin(value = "0.00", message = "现价必须大于等于0")
    @Digits(integer = 10, fraction = 2, message = "现价格式不正确")
    private BigDecimal currentPrice;

    @NotNull(message = "总库存不能为空", groups = Create.class)
    @Min(value = 0, message = "总库存必须大于等于0")
    private Integer totalStock;

    @NotNull(message = "可用库存不能为空", groups = Create.class)
    @Min(value = 0, message = "可用库存必须大于等于0")
    @Max(value = 100000, message = "可用库存不能超过100000")
    private Integer availableStock;

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态必须为0或1")
    @Max(value = 1, message = "状态必须为0或1")
    private Integer status;
}
