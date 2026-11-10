package org.example.ticketmanagement.dto;

/** SSE协议仅传业务状态与公开回复，不传模型内部思维过程。 */
public record AgentStreamEvent(String name, Object data) {
}
