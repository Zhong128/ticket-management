package org.example.ticketmanagement.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 确认卡片公开白名单，不包含身份、幂等键和内部异常。 */
public record AgentActionView(String id, String conversationId, String runId, String type,
                              String status, String targetId, String title, String eventName,
                              String sessionName, @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime sessionTime,
                              String tierName,
                              int quantity, BigDecimal unitPrice, BigDecimal totalAmount,
                              String orderId, String requestStatus, String message,
                              @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime expiresAt, long expiresEpochMillis) {
}
