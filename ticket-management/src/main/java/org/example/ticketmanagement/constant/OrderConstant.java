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
     * 订单超时时间（分钟）
     */
    public static final int ORDER_TIMEOUT_MINUTES = 30;

    /**
     * 根据状态码获取描述
     */
    public static String getStatusDesc(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case ORDER_STATUS_PENDING -> ORDER_STATUS_PENDING_DESC;
            case ORDER_STATUS_PAID -> ORDER_STATUS_PAID_DESC;
            case ORDER_STATUS_CANCELED -> ORDER_STATUS_CANCELED_DESC;
            case ORDER_STATUS_COMPLETED -> ORDER_STATUS_COMPLETED_DESC;
            default -> "未知";
        };
    }
}