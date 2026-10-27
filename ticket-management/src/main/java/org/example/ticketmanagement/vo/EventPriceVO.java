package org.example.ticketmanagement.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EventPriceVO {
    private Long eventId;
    private BigDecimal minPrice;
}
