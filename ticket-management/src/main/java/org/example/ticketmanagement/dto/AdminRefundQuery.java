package org.example.ticketmanagement.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.example.ticketmanagement.pojo.PageQuery;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminRefundQuery extends PageQuery {
    private String refundNo;
    private String orderNo;
    private String status;
}
