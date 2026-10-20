// org/example/ticketmanagement/dto/SeckillOrderDTO.java
package org.example.ticketmanagement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeckillOrderDTO {

    @NotNull(message = "票档ID不能为空")
    // 要购买的票档ID
    private Long tierId;

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为1")
    @Max(value = 5, message = "每单最多购买5张")
    // 购买数量（默认为1，可扩展）
    private Integer quantity;

    @Size(max = 64, message = "requestKey最多64字符")
    private String requestKey;
    // 注意：用户ID从登录信息中获取，不从前端传递
}
