// org/example/ticketmanagement/service/OrderService.java
package org.example.ticketmanagement.service;

import org.example.ticketmanagement.dto.SeckillMessageDTO;
import org.example.ticketmanagement.vo.OrderInfoVO;

import java.util.List;

/**
 * 订单服务接口
 */
public interface OrderService {

    /**
     * 创建订单
     * @param seckillMessage 抢票消息
     * @return 订单ID
     */
    Long createOrder(SeckillMessageDTO seckillMessage);

    /**
     * 根据订单号查询订单
     * @param orderNo 订单号
     * @return 订单详情
     */
    OrderInfoVO getOrderByOrderNo(String orderNo);

    /**
     * 根据订单ID查询订单
     * @param orderId 订单ID
     * @return 订单详情
     */
    OrderInfoVO getOrderById(Long orderId);

    /**
     * 根据用户ID查询订单列表
     * @param userId 用户ID
     * @return 订单列表
     */
    List<OrderInfoVO> getOrdersByUserId(Long userId);

    /**
     * 更新订单状态
     * @param orderId 订单ID
     * @param orderStatus 订单状态
     * @return 是否成功
     */
    boolean updateOrderStatus(Long orderId, Integer orderStatus);

    /**
     * 取消订单（超时未支付）
     * @param orderId 订单ID
     * @return 是否成功
     */
    boolean cancelOrder(Long orderId);

    /**
     * 处理超时未支付订单
     * @param timeoutMinutes 超时时间（分钟）
     * @param batchSize 批量处理数量
     */
    void processTimeoutOrders(Integer timeoutMinutes, Integer batchSize);

    /**
     * 根据订单号取消订单（供死信消费者使用）
     * @param orderNo 订单号
     * @return 是否成功
     */
    boolean cancelOrderByOrderNo(String orderNo);

    /**
     * 获取超时订单统计信息
     * @param timeoutMinutes 超时时间（分钟）
     * @return 超时订单数量
     */
    int countTimeoutOrders(Integer timeoutMinutes);

    /**
     * 批量取消订单
     * @param orderIds 订单ID列表
     * @return 成功取消的数量
     */
    int batchCancelOrders(List<Long> orderIds);
}