// org/example/ticketmanagement/dto/SeckillOrderDTO.java
package org.example.ticketmanagement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeckillOrderDTO {
    @NotNull(message = "票档ID不能为空")
    private Long tierId;           // 要购买的票档ID

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为1")
    private Integer quantity;      // 购买数量（默认为1，可扩展）

    // 注意：用户ID从登录信息中获取，不从前端传递
}