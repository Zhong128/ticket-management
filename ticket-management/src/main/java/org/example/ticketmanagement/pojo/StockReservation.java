package org.example.ticketmanagement.pojo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 可靠受理的库存申请，区分Redis预扣与数据库库存占用阶段。
 */
@Data
public class StockReservation {
    private Long id;
    private Long userId;
    private Long tierId;
    private Long eventId;
    private Long sessionId;
    private String requestKey;
    private String orderNo;
    private String status;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long orderId;
    private String releaseReason;
    private Integer attempts;
    private String inventoryState;
    private String redisToken;
    private Long stockEpoch;
}
