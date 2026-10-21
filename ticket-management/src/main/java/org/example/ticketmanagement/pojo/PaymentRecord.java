// org/example/ticketmanagement/pojo/PaymentRecord.java
package org.example.ticketmanagement.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.ticketmanagement.constant.PaymentConstant;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRecord {
    private Long id;                 // 支付记录ID
    private Long orderId;            // 订单ID
    private String orderNo;          // 订单号
    private Long userId;             // 用户ID
    private String paymentNo;        // 支付流水号（商户生成）
    private String tradeNo;          // 支付宝交易号
    private BigDecimal totalAmount;  // 支付金额
    private Integer payStatus;       // 支付状态：0-待支付，1-支付中，2-支付成功，3-支付失败，4-已关闭
    private Integer payType;         // 支付方式：1-支付宝
    private String subject;          // 支付标题
    private String body;             // 支付描述
    private String notifyData;       // 最近一次有效回调的最小摘要
    private LocalDateTime notifyTime;// 回调时间
    private LocalDateTime payTime;   // 支付完成时间
    private LocalDateTime expireTime;// 过期时间
    private LocalDateTime createTime;// 创建时间
    private LocalDateTime updateTime;// 更新时间

    /**
     * 获取支付状态描述
     */
    public String getPayStatusDesc() {
        return PaymentConstant.getPayStatusDesc(this.payStatus);
    }

    /**
     * 判断是否已过期
     */
    public boolean isExpired() {
        return expireTime != null && LocalDateTime.now().isAfter(expireTime);
    }

    /**
     * 判断是否可以支付（待支付且未过期）
     */
    public boolean canPay() {
        return payStatus == PaymentConstant.PAY_STATUS_WAITING && !isExpired();
    }
}
