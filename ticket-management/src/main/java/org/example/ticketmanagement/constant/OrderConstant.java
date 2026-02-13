// org/example/ticketmanagement/constant/OrderConstant.java
package org.example.ticketmanagement.constant;

/**
 * 订单模块常量类
 */
public class OrderConstant {

    /**
     * 订单状态：0-待支付，1-已支付，2-已取消，3-已完成
     */
    public static final int ORDER_STATUS_PENDING = 0;
    public static final int ORDER_STATUS_PAID = 1;
    public static final int ORDER_STATUS_CANCELED = 2;
    public static final int ORDER_STATUS_COMPLETED = 3;

    /**
     * 订单状态描述
     */
    public static final String ORDER_STATUS_PENDING_DESC = "待支付";
    public static final String ORDER_STATUS_PAID_DESC = "已支付";
    public static final String ORDER_STATUS_CANCELED_DESC = "已取消";
    public static final String ORDER_STATUS_COMPLETED_DESC = "已完成";

    /**
     * 默认分页大小
     */
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 订单超时时间（分钟）
     */
    public static final int ORDER_TIMEOUT_MINUTES = 30;

    /**
     * 根据状态码获取描述
     */
    public static String getStatusDesc(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case ORDER_STATUS_PENDING:
                return ORDER_STATUS_PENDING_DESC;
            case ORDER_STATUS_PAID:
                return ORDER_STATUS_PAID_DESC;
            case ORDER_STATUS_CANCELED:
                return ORDER_STATUS_CANCELED_DESC;
            case ORDER_STATUS_COMPLETED:
                return ORDER_STATUS_COMPLETED_DESC;
            default:
                return "未知";
        }
    }
}