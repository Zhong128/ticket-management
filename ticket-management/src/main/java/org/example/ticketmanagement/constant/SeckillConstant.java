// org/example/ticketmanagement/constant/SeckillConstant.java
package org.example.ticketmanagement.constant;

public class SeckillConstant {

    // Redis Key 前缀
    public static final String STOCK_KEY_PREFIX = "seckill:stock:";
    public static final String ORDER_KEY_PREFIX = "seckill:order:";
    public static final String USER_KEY_PREFIX = "seckill:user:";
    public static final String LOCK_KEY_PREFIX = "seckill:lock:";

    // 订单状态
    public static final Integer ORDER_STATUS_PENDING = 0;  // 待支付
    public static final Integer ORDER_STATUS_PAID = 1;     // 已支付
    public static final Integer ORDER_STATUS_CANCELED = 2; // 已取消

    // 抢票相关
    public static final Integer MAX_QUANTITY = 5;  // 每人最多购买数量
    public static final Integer PAY_TIMEOUT_MINUTES = 1;  // 支付超时时间（分钟）
    public static final Long QUEUE_LIMIT = 1000L;  // 队列大小限制

    // 消息队列
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ORDER_EXCHANGE = "seckill.order.exchange";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order.routing.key";

    // 库存回退延迟队列（用于订单超时自动取消）
    public static final String STOCK_RETURN_DELAY_QUEUE = "stock.return.delay.queue";
    public static final String STOCK_RETURN_DELAY_EXCHANGE = "stock.return.delay.exchange";
    public static final String STOCK_RETURN_DELAY_ROUTING_KEY = "stock.return.delay.routing.key";
    public static final String STOCK_RETURN_PROCESS_EXCHANGE = "stock.return.process.exchange";
    public static final String STOCK_RETURN_PROCESS_ROUTING_KEY = "stock.return.process.routing.key";


    // 分布式锁过期时间（秒）
    public static final Long LOCK_EXPIRE_TIME = 10L;


    // 错误信息
    public static final String ERROR_STOCK_NOT_ENOUGH = "库存不足";
    public static final String ERROR_USER_REPEAT = "请勿重复抢票";
    public static final String ERROR_SYSTEM_BUSY = "系统繁忙，请稍后重试";
    public static final String ERROR_TIER_NOT_FOUND = "票档不存在";
    public static final String ERROR_TIER_NOT_AVAILABLE = "票档不可用";
    public static final String ERROR_SESSION_NOT_ON_SALE = "场次未开售";
    public static final String ERROR_MAX_QUANTITY = "超过最大购买数量";
}