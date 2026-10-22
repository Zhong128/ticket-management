package org.example.ticketmanagement.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StockEpoch {
    private Long tierId;
    private Long epoch;
    private String cacheStatus;
    private String buildToken;
    private LocalDateTime updatedAt;
}
