package org.example.ticketmanagement.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.ticketmanagement.constant.SeckillConstant;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeckillResultVO {

    private Long requestId;
    private String requestKey;
    private String requestStatus;
    private LocalDateTime expireTime;
    // 是否抢票成功
    private Boolean success;
    // 订单号（成功时返回）
    private String orderNo;
    // 结果消息
    private String message;

    // 只在不为null时序列化
    @JsonInclude(JsonInclude.Include.NON_NULL)
    // 订单ID
    private Long orderId;

    // 订单总金额
    private BigDecimal totalAmount;
    // 订单状态
    private Integer orderStatus;
    // 票档信息（方便前端展示）
    private String tierName;
    private BigDecimal price;
    private Integer quantity;

    // 静态工厂方法
    public static SeckillResultVO success(String orderNo, Long orderId, BigDecimal totalAmount, String tierName,
            BigDecimal price, Integer quantity) {
        SeckillResultVO vo = new SeckillResultVO();
        vo.setSuccess(true);
        vo.setOrderNo(orderNo);
        vo.setMessage("抢票成功，请在" + SeckillConstant.PAY_TIMEOUT_MINUTES + "分钟内支付");
        // 可以为null
        vo.setOrderId(orderId);
        vo.setTotalAmount(totalAmount);
        // 待支付
        vo.setOrderStatus(0);
        vo.setTierName(tierName);
        vo.setPrice(price);
        vo.setQuantity(quantity);
        return vo;
    }

    public static SeckillResultVO fail(String message) {
        SeckillResultVO vo = new SeckillResultVO();
        vo.setSuccess(false);
        vo.setMessage(message);
        return vo;
    }
}
