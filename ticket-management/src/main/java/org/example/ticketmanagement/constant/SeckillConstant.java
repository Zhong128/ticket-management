// org/example/ticketmanagement/constant/SeckillConstant.java
package org.example.ticketmanagement.constant;

public class SeckillConstant {

    // Redis Key 前缀
    public static final String STOCK_KEY_PREFIX = "seckill:stock:";
    public static final String USER_KEY_PREFIX = "seckill:user:";
    public static final String LOCK_KEY_PREFIX = "seckill:lock:";


    // 抢票相关
    public static final Integer MAX_QUANTITY = 5;  // 每人最多购买数量
    public static final Integer PAY_TIMEOUT_MINUTES = 30;  // 支付超时时间（分钟）

    // 消息队列
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ORDER_EXCHANGE = "seckill.order.exchange";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order.routing.key";

    // 错误信息
    public static final String ERROR_STOCK_NOT_ENOUGH = "库存不足";
    public static final String ERROR_USER_REPEAT = "请勿重复抢票";
    public static final String ERROR_SYSTEM_BUSY = "系统繁忙，请稍后重试";
    public static final String ERROR_TIER_NOT_FOUND = "票档不存在";
    public static final String ERROR_TIER_NOT_AVAILABLE = "票档不可用";
    public static final String ERROR_SESSION_NOT_ON_SALE = "场次未开售";
    public static final String ERROR_MAX_QUANTITY = "超过最大购买数量";
}