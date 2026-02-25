// org/example/ticketmanagement/service/OrderService.java
package org.example.ticketmanagement.service;

import org.example.ticketmanagement.dto.OrderQueryDTO;
import org.example.ticketmanagement.dto.OrderUpdateDTO;
import org.example.ticketmanagement.dto.SeckillMessageDTO;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.vo.OrderInfoVO;

import java.util.List;

public interface OrderService {

    // ==================== 基础订单操作 ====================

    /**
     * 创建订单（抢票时调用）
     */
    Long createOrder(SeckillMessageDTO seckillMessage);

    /**
     * 根据订单号查询订单
     */
    OrderInfoVO getOrderByOrderNo(String orderNo);

    /**
     * 取消订单（超时未支付/用户主动取消）
     */
    boolean cancelOrder(Long orderId);

    // ==================== 客户端订单接口 ====================

    /**
     * 客户端分页查询订单列表
     */
    PageResult<OrderInfoVO> getUserOrders(OrderQueryDTO queryDTO, Long userId);

    /**
     * 获取订单详情（客户端）
     */
    OrderInfoVO getOrderDetail(Long orderId, Long userId);

    /**
     * 用户取消订单
     */
    boolean userCancelOrder(Long orderId, Long userId);

    // ==================== 管理端订单接口 ====================

    /**
     * 管理端分页查询订单列表
     */
    PageResult<OrderInfoVO> adminQueryOrders(OrderQueryDTO queryDTO);

    /**
     * 管理端获取订单详情
     */
    OrderInfoVO adminGetOrderDetail(Long orderId);

    /**
     * 管理端修改订单信息
     */
    boolean adminUpdateOrder(Long orderId, OrderUpdateDTO updateDTO, Long adminId);

    // ==================== 定时任务/系统内部调用 ====================

    /**
     * 处理超时未支付订单
     */
    void processTimeoutOrders(Integer timeoutMinutes, Integer batchSize);

    /**
     * 根据订单号取消订单（供死信消费者使用）
     */
    boolean cancelOrderByOrderNo(String orderNo);

    /**
     * 获取超时订单统计信息
     */
    int countTimeoutOrders(Integer timeoutMinutes);

    /**
     * 批量取消订单
     */
    int batchCancelOrders(List<Long> orderIds);

    /**
     * 更新订单状态
     * @param orderId 订单ID
     * @param orderStatus 订单状态
     * @return 是否成功
     */
    boolean updateOrderStatus(Long orderId, Integer orderStatus);

    /**
     * 管理端根据用户邮箱查询订单列表
     * @param email 用户邮箱
     * @param queryDTO 查询条件
     * @return 订单分页结果
     */
    PageResult<OrderInfoVO> getOrdersByUserEmail(String email, OrderQueryDTO queryDTO);
}