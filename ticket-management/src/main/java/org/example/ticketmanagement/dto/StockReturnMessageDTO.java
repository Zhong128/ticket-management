// org/example/ticketmanagement/dto/StockReturnMessageDTO.java
package org.example.ticketmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockReturnMessageDTO {
    private Long tierId;           // 票档ID
    private Integer quantity;      // 回退数量
    private Long orderId;          // 订单ID
    private String reason;         // 回退原因：timeout（超时）、cancel（取消）、error（错误）
    private Long timestamp;        // 时间戳
}