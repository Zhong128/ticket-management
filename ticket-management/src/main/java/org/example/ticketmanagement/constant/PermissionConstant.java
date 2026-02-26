package org.example.ticketmanagement.constant;

/**
 * 权限常量类，可用于后续对细粒度的权限控制
 */
public class PermissionConstant {
    // 用户管理权限
    public static final String USER_VIEW = "user:view";
    public static final String USER_ADD = "user:add";
    public static final String USER_EDIT = "user:edit";
    public static final String USER_DELETE = "user:delete";
    public static final String USER_ENABLE = "user:enable";

    // 城市管理权限
    public static final String CITY_VIEW = "city:view";
    public static final String CITY_ADD = "city:add";
    public static final String CITY_EDIT = "city:edit";
    public static final String CITY_DELETE = "city:delete";

    // 演出管理权限
    public static final String SHOW_VIEW = "show:view";
    public static final String SHOW_ADD = "show:add";
    public static final String SHOW_EDIT = "show:edit";
    public static final String SHOW_DELETE = "show:delete";

    // 订单管理权限
    public static final String ORDER_VIEW = "order:view";
    public static final String ORDER_CREATE = "order:create";
    public static final String ORDER_CANCEL = "order:cancel";
    public static final String ORDER_REFUND = "order:refund";

    // 抢票权限
    public static final String TICKET_GRAB = "ticket:grab";
    public static final String TICKET_VIP_GRAB = "ticket:vip_grab";
    public static final String TICKET_EARLY_ACCESS = "ticket:early_access";

    // 支付权限
    public static final String PAYMENT_CREATE = "payment:create";
    public static final String PAYMENT_REFUND = "payment:refund";

    // 公共权限
    public static final String PUBLIC_ACCESS = "public:access";

    // ============ 新增常量（根据StpInterfaceImpl中使用的补充） ============

    // 城市管理 - 通配权限（用于管理员）
    public static final String CITY_ALL = "city:*";

    // 演出管理 - 通配权限（用于管理员）
    public static final String SHOW_ALL = "show:*";

    // 票务查看权限（用于管理员）
    public static final String TICKET_VIEW = "ticket:view";

    // 所有权限通配符（用于超级管理员）
    public static final String ALL_PERMISSIONS = "*";
}