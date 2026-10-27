package org.example.ticketmanagement.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.example.ticketmanagement.pojo.PageQuery;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminSessionQuery extends PageQuery {
    private Long eventId;
    private Integer status;
    private Integer eventStatus;
    private boolean ascending;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}
