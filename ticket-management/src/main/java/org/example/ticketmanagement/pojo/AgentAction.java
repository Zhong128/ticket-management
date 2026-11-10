package org.example.ticketmanagement.pojo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 仅供服务端持久化，公开接口必须转换为AgentActionView。 */
@Data
public class AgentAction {

    private String id;
    private long userId;
    private String conversationId;
    private String runId;
    private String type;
    private long targetId;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String title;
    private String eventName;
    private String sessionName;
    private LocalDateTime sessionTime;
    private String tierName;
    private String requestKey;
    private String status;
    private String requestStatus;
    private Long orderId;
    private String message;
    private boolean contentErased;
    private long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;
}
