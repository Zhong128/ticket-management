// org/example/ticketmanagement/dto/OrderQueryDTO.java
package org.example.ticketmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderQueryDTO {
    private String orderNo;           // 订单号
    private Long userId;              // 用户ID
    private Long eventId;             // 演出ID
    private Long sessionId;           // 场次ID
    private Long tierId;              // 票档ID
    private Integer orderStatus;      // 订单状态
    private LocalDateTime startTime;  // 创建开始时间
    private LocalDateTime endTime;    // 创建结束时间
    private Integer pageNum = 1;      // 页码
    private Integer pageSize = 10;    // 每页大小
}