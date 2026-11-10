package org.example.ticketmanagement.dto;

/** 来源只由服务端检索记录创建，模型输出的链接不能变成来源卡片。 */
public record AgentCitation(String entryId, String documentId, String title, int version,
                            String reviewedAt, String url) {
}
