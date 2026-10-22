package org.example.ticketmanagement.pojo;

import lombok.Data;

@Data
public class SeckillAdmission {
    private Long userId;
    private String requestKey;
    private Long tierId;
    private Integer quantity;
    private String redisToken;
    private Long stockEpoch;
    private String admissionStatus;
    private Long reservationId;
}
