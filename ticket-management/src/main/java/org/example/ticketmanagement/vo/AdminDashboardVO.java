package org.example.ticketmanagement.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AdminDashboardVO {
    private long onSaleEventCount;
    private long upcomingSessionCount;
    private long pendingOrderCount;
    private long refundAttentionCount;
    private LocalDateTime asOf;
}
