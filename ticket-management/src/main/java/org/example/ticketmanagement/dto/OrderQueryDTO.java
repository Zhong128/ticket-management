// org/example/ticketmanagement/dto/OrderQueryDTO.java
package org.example.ticketmanagement.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.example.ticketmanagement.pojo.PageQuery;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderQueryDTO extends PageQuery {
    private String orderNo;           // 订单号（模糊查询）
    private Long userId;              // 用户ID
    private String userName;         // 用户名称（管理端模糊查询）
    private String userPhone;        // 用户手机号（管理端查询）
    private Long eventId;            // 演出ID
    private String eventName;        // 演出名称（管理端模糊查询）
    private Long sessionId;          // 场次ID
    private Long tierId;             // 票档ID
    private Integer orderStatus;     // 订单状态
    private LocalDateTime startTime; // 创建开始时间
    private LocalDateTime endTime;   // 创建结束时间
    private BigDecimal minAmount;    // 最小金额
    private BigDecimal maxAmount;    // 最大金额
    private String userEmail;        // 用户邮箱（管理端查询）

    public boolean needOrderNoLike() {
        return orderNo != null && !orderNo.trim().isEmpty();
    }

    public boolean needEventNameLike() {
        return eventName != null && !eventName.trim().isEmpty();
    }
    
    /**
     * 检查是否需要邮箱精确匹配
     */
    public boolean needUserEmailExact() {
        return userEmail != null && !userEmail.trim().isEmpty();
    }
    
    /**
     * 检查价格范围参数是否有效
     * @return true 表示参数有效，false 表示无效
     */
    public boolean isValidAmountRange() {
        // 如果两个参数都为null，认为有效（不限制价格范围）
        if (minAmount == null && maxAmount == null) {
            return true;
        }
        
        // 如果只有一个参数为null，认为有效（只限制最小值或最大值）
        if (minAmount == null || maxAmount == null) {
            return true;
        }
        
        // 两个参数都不为null时，最小值必须小于等于最大值
        return minAmount.compareTo(maxAmount) <= 0;
    }
    
    /**
     * 获取价格范围校验错误信息
     * @return 错误信息，如果参数有效则返回null
     */
    public String getAmountRangeErrorMessage() {
        if (!isValidAmountRange()) {
            return "最小金额不能大于最大金额";
        }
        return null;
    }
}