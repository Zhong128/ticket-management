package org.example.ticketmanagement.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.example.ticketmanagement.dto.AgentCitation;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AgentRun {

    private String id;
    private String conversationId;
    private String clientMessageId;
    @JsonIgnore
    private String inputHash;
    private String status;
    private String reply;
    private String errorCode;
    private String errorMessage;
    private int toolCalls;
    private String toolTrace;
    private List<AgentCitation> citations = List.of();
    private LocalDateTime createdAt;
    private LocalDateTime deadlineAt;
    private long deadlineEpochMillis;
    private LocalDateTime completedAt;
}
