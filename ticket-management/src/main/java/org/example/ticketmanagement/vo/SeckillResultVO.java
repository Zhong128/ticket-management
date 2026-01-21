// org/example/ticketmanagement/vo/SeckillResultVO.java
package org.example.ticketmanagement.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.ticketmanagement.constant.SeckillConstant;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeckillResultVO {
    private Boolean success;           // 是否抢票成功
    private String orderNo;            // 订单号（成功时返回）
    private String message;            // 结果消息
    private Long orderId;              // 订单ID
    private BigDecimal totalAmount;    // 订单总金额
    private Integer orderStatus;       // 订单状态

    // 票档信息（方便前端展示）
    private String tierName;
    private BigDecimal price;
    private Integer quantity;

    // 静态工厂方法
    public static SeckillResultVO success(String orderNo, Long orderId, BigDecimal totalAmount,
                                          String tierName, BigDecimal price, Integer quantity) {
        SeckillResultVO vo = new SeckillResultVO();
        vo.setSuccess(true);
        vo.setOrderNo(orderNo);
        vo.setMessage("抢票成功，请在" + SeckillConstant.PAY_TIMEOUT_MINUTES + "分钟内支付");
        vo.setOrderId(orderId);
        vo.setTotalAmount(totalAmount);
        vo.setOrderStatus(0); // 待支付
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