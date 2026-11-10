package org.example.ticketmanagement.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentConversation {

    private String id;
    @JsonIgnore
    private Long userId;
    private String title;
    @JsonIgnore
    private Integer defaultMarker;
    private String activeRunId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @JsonIgnore
    private LocalDateTime deletedAt;

    public boolean isDefaultConversation() {
        return Integer.valueOf(1).equals(defaultMarker);
    }
}
