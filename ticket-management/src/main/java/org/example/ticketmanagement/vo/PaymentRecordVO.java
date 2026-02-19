// org/example/ticketmanagement/vo/PaymentRecordVO.java
package org.example.ticketmanagement.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.ticketmanagement.constant.PaymentConstant;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRecordVO {

    private Long id;                    // 支付记录ID
    private String paymentNo;           // 支付流水号
    private String tradeNo;             // 支付宝交易号
    private String orderNo;             // 订单号
    private Long orderId;               // 订单ID
    private BigDecimal totalAmount;      // 支付金额
    private Integer payStatus;           // 支付状态
    private String payStatusDesc;        // 支付状态描述
    private Integer payType;             // 支付方式
    private String payTypeDesc;          // 支付方式描述
    private String subject;              // 支付标题

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;       // 支付完成时间

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;    // 过期时间

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;    // 创建时间

    public String getPayStatusDesc() {
        return PaymentConstant.getPayStatusDesc(this.payStatus);
    }

    public String getPayTypeDesc() {
        if (payType == null) return "未知";
        return switch (payType) {
            case 1 -> "支付宝";
            default -> "未知";
        };
    }
}