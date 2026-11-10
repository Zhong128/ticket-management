package org.example.ticketmanagement.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.example.ticketmanagement.dto.AgentCitation;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AgentMessage {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    private String conversationId;
    private String runId;
    private String role;
    private String content;
    private List<AgentCitation> citations = List.of();
    private LocalDateTime createdAt;
}
