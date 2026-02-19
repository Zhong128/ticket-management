// org/example/ticketmanagement/constant/PaymentConstant.java
package org.example.ticketmanagement.constant;

/**
 * 支付模块常量类
 */
public class PaymentConstant {

    /**
     * 支付状态：0-待支付，1-支付中，2-支付成功，3-支付失败，4-已关闭，5-已退款
     */
    public static final int PAY_STATUS_WAITING = 0;        // 待支付
    public static final int PAY_STATUS_PROCESSING = 1;     // 支付中
    public static final int PAY_STATUS_SUCCESS = 2;        // 支付成功
    public static final int PAY_STATUS_FAILED = 3;         // 支付失败
    public static final int PAY_STATUS_CLOSED = 4;         // 已关闭
    public static final int PAY_STATUS_REFUNDED = 5;       // 已退款

    /**
     * 支付状态描述
     */
    public static final String PAY_STATUS_WAITING_DESC = "待支付";
    public static final String PAY_STATUS_PROCESSING_DESC = "支付中";
    public static final String PAY_STATUS_SUCCESS_DESC = "支付成功";
    public static final String PAY_STATUS_FAILED_DESC = "支付失败";
    public static final String PAY_STATUS_CLOSED_DESC = "已关闭";
    public static final String PAY_STATUS_REFUNDED_DESC = "已退款";

    /**
     * 支付方式
     */
    public static final int PAY_TYPE_ALIPAY = 1;           // 支付宝
    public static final int PAY_TYPE_WECHAT = 2;           // 微信（预留）

    /**
     * 支付宝交易状态（从支付宝回调中获取）
     */
    public static final String ALIPAY_TRADE_WAIT_BUYER_PAY = "WAIT_BUYER_PAY";    // 交易创建，等待买家付款
    public static final String ALIPAY_TRADE_CLOSED = "TRADE_CLOSED";              // 未付款交易超时关闭/支付完成后全额退款
    public static final String ALIPAY_TRADE_SUCCESS = "TRADE_SUCCESS";            // 交易支付成功
    public static final String ALIPAY_TRADE_FINISHED = "TRADE_FINISHED";          // 交易结束，不可退款

    /**
     * 补偿任务状态
     */
    public static final int COMPENSATION_STATUS_PENDING = 0;   // 待处理
    public static final int COMPENSATION_STATUS_PROCESSING = 1;// 处理中
    public static final int COMPENSATION_STATUS_SUCCESS = 2;   // 处理成功
    public static final int COMPENSATION_STATUS_FAILED = 3;    // 处理失败

    // ==================== RabbitMQ相关常量 ====================

    // 支付成功通知
    public static final String PAYMENT_SUCCESS_EXCHANGE = "payment.success.exchange";
    public static final String PAYMENT_SUCCESS_QUEUE = "payment.success.queue";
    public static final String PAYMENT_SUCCESS_ROUTING_KEY = "payment.success";

    // 支付超时延迟队列（用于30分钟超时检查）
    public static final String PAYMENT_TIMEOUT_EXCHANGE = "payment.timeout.exchange";
    public static final String PAYMENT_TIMEOUT_QUEUE = "payment.timeout.queue";
    public static final String PAYMENT_TIMEOUT_ROUTING_KEY = "payment.timeout";
    public static final int PAYMENT_TIMEOUT_DELAY = 30 * 60 * 1000; // 30分钟，单位毫秒

    // 支付补偿队列
    public static final String PAYMENT_COMPENSATION_EXCHANGE = "payment.compensation.exchange";
    public static final String PAYMENT_COMPENSATION_QUEUE = "payment.compensation.queue";
    public static final String PAYMENT_COMPENSATION_ROUTING_KEY = "payment.compensation";

    // ==================== 其他常量 ====================
    public static final int MAX_COMPENSATION_RETRY = 3;     // 最大补偿重试次数
    public static final int COMPENSATION_BATCH_SIZE = 100;  // 补偿批量处理数量

    /**
     * 根据状态码获取描述
     */
    public static String getPayStatusDesc(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case PAY_STATUS_WAITING: return PAY_STATUS_WAITING_DESC;
            case PAY_STATUS_PROCESSING: return PAY_STATUS_PROCESSING_DESC;
            case PAY_STATUS_SUCCESS: return PAY_STATUS_SUCCESS_DESC;
            case PAY_STATUS_FAILED: return PAY_STATUS_FAILED_DESC;
            case PAY_STATUS_CLOSED: return PAY_STATUS_CLOSED_DESC;
            case PAY_STATUS_REFUNDED: return PAY_STATUS_REFUNDED_DESC;
            default: return "未知";
        }
    }
}