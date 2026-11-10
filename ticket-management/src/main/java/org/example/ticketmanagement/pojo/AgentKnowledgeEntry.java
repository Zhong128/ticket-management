package org.example.ticketmanagement.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AgentKnowledgeEntry {

    private String entryId;
    private String documentId;
    private String documentTitle;
    private int version;
    private String question;
    private String answer;
    private String liveCheck;
    private LocalDate reviewedAt;
    private boolean current;
    @JsonIgnore
    private int relevance;
}
