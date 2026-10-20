// org/example/ticketmanagement/vo/OrderInfoVO.java
package org.example.ticketmanagement.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.ticketmanagement.constant.OrderConstant;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderInfoVO {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    private String cancelReason;
    private String remark;
    // 订单ID
    private Long id;
    // 订单号
    private String orderNo;
    // 用户ID
    private Long userId;
    // 用户姓名（管理端用）
    private String userName;
    // 演出ID
    private Long eventId;
    // 演出名称
    private String eventName;
    // 演出海报
    private String eventImage;
    // 场次ID
    private Long sessionId;
    // 场次名称
    private String sessionName;
    // 场次时间
    private LocalDateTime sessionTime;
    // 场馆名称
    private String venueName;
    // 票档ID
    private Long tierId;
    // 票档名称
    private String tierName;
    // 票档单价
    private BigDecimal tierPrice;
    // 购买数量
    private Integer quantity;
    // 订单总金额
    private BigDecimal totalAmount;
    // 订单状态
    private Integer orderStatus;
    // 订单状态描述
    private String orderStatusDesc;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    // 支付时间
    private LocalDateTime payTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    // 创建时间
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    // 更新时间
    private LocalDateTime updateTime;

    public String getOrderStatusDesc() {
        return OrderConstant.getStatusDesc(this.orderStatus);
    }
}
