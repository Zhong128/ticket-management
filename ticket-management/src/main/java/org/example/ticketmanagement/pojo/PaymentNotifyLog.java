// org/example/ticketmanagement/pojo/PaymentNotifyLog.java
package org.example.ticketmanagement.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 支付回调日志实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentNotifyLog {
    private Long id;                 // 日志ID
    private String paymentNo;        // 支付流水号
    private String tradeNo;          // 支付宝交易号
    private String notifyData;       // 已验签回调的最小摘要，不保存签名等原始参数
    private String notifyStatus;     // 回调状态
    private Integer processStatus;   // 处理状态：0-待处理，1-处理成功，2-处理失败
    private String errorMsg;         // 错误信息
    private String ipAddress;        // 回调IP
    private LocalDateTime createTime;// 创建时间
}
