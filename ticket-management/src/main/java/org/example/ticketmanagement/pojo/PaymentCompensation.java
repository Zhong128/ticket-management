// org/example/ticketmanagement/pojo/PaymentCompensation.java
package org.example.ticketmanagement.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 支付补偿任务实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentCompensation {
    private Long id;                 // 任务ID
    private String paymentNo;        // 支付流水号
    private Long orderId;            // 订单ID
    private String orderNo;          // 订单号
    private Long userId;             // 用户ID
    private Integer taskStatus;      // 任务状态：0-待处理，1-处理中，2-处理成功，3-处理失败
    private Integer retryCount;      // 重试次数
    private Integer maxRetry;        // 最大重试次数
    private LocalDateTime lastExecuteTime;  // 最后执行时间
    private LocalDateTime nextExecuteTime;  // 下次执行时间
    private String errorMsg;         // 错误信息
    private LocalDateTime createTime;// 创建时间
    private LocalDateTime updateTime;// 更新时间

    /**
     * 是否可以重试
     */
    public boolean canRetry() {
        return retryCount < maxRetry;
    }

    /**
     * 计算下次执行时间（指数退避）
     */
    public void calculateNextExecuteTime() {
        // 重试间隔：2^retryCount 分钟
        int minutes = (int) Math.pow(2, retryCount);
        this.nextExecuteTime = LocalDateTime.now().plusMinutes(minutes);
    }
}