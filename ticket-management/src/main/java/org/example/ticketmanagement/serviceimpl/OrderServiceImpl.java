// org/example/ticketmanagement/service/impl/OrderServiceImpl.java
package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.SeckillConstant;
import org.example.ticketmanagement.dto.SeckillMessageDTO;
import org.example.ticketmanagement.dto.StockReturnMessageDTO;
import org.example.ticketmanagement.mapper.EventMapper;
import org.example.ticketmanagement.mapper.EventSessionMapper;
import org.example.ticketmanagement.mapper.OrderInfoMapper;
import org.example.ticketmanagement.mapper.TicketTierMapper;
import org.example.ticketmanagement.pojo.Event;
import org.example.ticketmanagement.pojo.EventSession;
import org.example.ticketmanagement.pojo.OrderInfo;
import org.example.ticketmanagement.pojo.TicketTier;
import org.example.ticketmanagement.service.OrderService;
import org.example.ticketmanagement.service.StockService;
import org.example.ticketmanagement.util.RabbitMQUtil;
import org.example.ticketmanagement.vo.OrderInfoVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderInfoMapper orderInfoMapper;
    private final TicketTierMapper ticketTierMapper;
    private final EventMapper eventMapper;
    private final EventSessionMapper eventSessionMapper;
    private final StockService stockService;
    private final RabbitMQUtil rabbitMQUtil;

    @Override
    @Transactional
    public Long createOrder(SeckillMessageDTO seckillMessage) {
        log.info("开始创建订单: orderNo={}, userId={}, tierId={}",
                seckillMessage.getOrderNo(), seckillMessage.getUserId(), seckillMessage.getTierId());

        try {
            // 1. 检查订单是否已存在（幂等性）
            OrderInfo existingOrder = orderInfoMapper.selectByOrderNo(seckillMessage.getOrderNo());
            if (existingOrder != null) {
                log.warn("订单已存在，跳过创建: orderNo={}", seckillMessage.getOrderNo());
                return existingOrder.getId();
            }

            // 2. 创建订单对象
            OrderInfo orderInfo = new OrderInfo();
            orderInfo.setOrderNo(seckillMessage.getOrderNo());
            orderInfo.setUserId(seckillMessage.getUserId());
            orderInfo.setEventId(seckillMessage.getEventId());
            orderInfo.setSessionId(seckillMessage.getSessionId());
            orderInfo.setTierId(seckillMessage.getTierId());
            orderInfo.setQuantity(seckillMessage.getQuantity());
            orderInfo.setTotalAmount(seckillMessage.getTotalAmount());
            orderInfo.setOrderStatus(SeckillConstant.ORDER_STATUS_PENDING); // 待支付
            orderInfo.setCreateTime(LocalDateTime.now());
            orderInfo.setUpdateTime(LocalDateTime.now());

            // 3. 插入订单到数据库
            int result = orderInfoMapper.insert(orderInfo);
            if (result <= 0) {
                throw new RuntimeException("订单创建失败");
            }

            log.info("订单创建成功: orderId={}, orderNo={}", orderInfo.getId(), orderInfo.getOrderNo());

            // 4. 发送库存回退消息到延迟队列（用于超时取消订单）
            sendStockReturnMessage(orderInfo);

            return orderInfo.getId();

        } catch (Exception e) {
            log.error("创建订单失败: orderNo={}", seckillMessage.getOrderNo(), e);

            // 5. 创建订单失败，恢复库存
            stockService.restoreStock(seckillMessage.getTierId(), seckillMessage.getQuantity());

            // 6. 删除用户抢票记录
            stockService.deleteUserSeckillRecord(seckillMessage.getUserId(), seckillMessage.getTierId());

            throw new RuntimeException("订单创建失败", e);
        }
    }


    @Override
    public OrderInfoVO getOrderByOrderNo(String orderNo) {
        OrderInfo orderInfo = orderInfoMapper.selectByOrderNo(orderNo);
        if (orderInfo == null) {
            return null;
        }
        return convertToVO(orderInfo);
    }

    @Override
    public OrderInfoVO getOrderById(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (orderInfo == null) {
            return null;
        }
        return convertToVO(orderInfo);
    }

    @Override
    public List<OrderInfoVO> getOrdersByUserId(Long userId) {
        List<OrderInfo> orders = orderInfoMapper.selectByUserId(userId);
        return orders.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean updateOrderStatus(Long orderId, Integer orderStatus) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (orderInfo == null) {
            log.warn("订单不存在: orderId={}", orderId);
            return false;
        }

        orderInfo.setOrderStatus(orderStatus);
        orderInfo.setUpdateTime(LocalDateTime.now());

        // 如果订单已支付，设置支付时间
        if (orderStatus.equals(SeckillConstant.ORDER_STATUS_PAID)) {
            orderInfo.setPayTime(LocalDateTime.now());
        }

        int result = orderInfoMapper.updateStatus(orderInfo);
        boolean success = result > 0;

        if (success) {
            log.info("更新订单状态成功: orderId={}, status={}", orderId, orderStatus);
        } else {
            log.error("更新订单状态失败: orderId={}, status={}", orderId, orderStatus);
        }

        return success;
    }

    @Override
    @Transactional
    public boolean cancelOrder(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (orderInfo == null) {
            log.warn("要取消的订单不存在: orderId={}", orderId);
            return false;
        }

        // 只能取消待支付状态的订单（幂等性）
        if (!orderInfo.getOrderStatus().equals(SeckillConstant.ORDER_STATUS_PENDING)) {
            log.warn("订单状态不是待支付，无法取消: orderId={}, status={}",
                    orderId, orderInfo.getOrderStatus());
            return false;
        }

        try {
            // 1. 恢复库存（原子操作）
            stockService.restoreStock(orderInfo.getTierId(), orderInfo.getQuantity());

            // 2. 删除用户抢票记录（防重记录）
            stockService.deleteUserSeckillRecord(orderInfo.getUserId(), orderInfo.getTierId());

            // 3. 更新订单状态为已取消
            orderInfo.setOrderStatus(SeckillConstant.ORDER_STATUS_CANCELED);
            orderInfo.setUpdateTime(LocalDateTime.now());

            int result = orderInfoMapper.updateStatus(orderInfo);
            boolean success = result > 0;

            if (success) {
                log.info("取消订单成功: orderId={}, orderNo={}, tierId={}, quantity={}",
                        orderId, orderInfo.getOrderNo(), orderInfo.getTierId(), orderInfo.getQuantity());
            } else {
                log.error("取消订单失败: orderId={}", orderId);
            }

            return success;

        } catch (Exception e) {
            log.error("取消订单异常: orderId={}", orderId, e);
            throw new RuntimeException("取消订单失败", e);
        }
    }

    @Override
    public void processTimeoutOrders(Integer timeoutMinutes, Integer batchSize) {
        log.info("开始处理超时未支付订单: timeoutMinutes={}, batchSize={}", timeoutMinutes, batchSize);

        Integer actualTimeout = SeckillConstant.PAY_TIMEOUT_MINUTES + 1; // +1分钟，让死信队列优先处理

        // 查询超时未支付订单
        List<OrderInfo> timeoutOrders = orderInfoMapper.selectTimeoutOrders(actualTimeout, batchSize);

        if (timeoutOrders.isEmpty()) {
            log.info("没有超时未支付订单");
            return;
        }

        log.info("找到{}个超时未支付订单", timeoutOrders.size());

        for (OrderInfo order : timeoutOrders) {
            try {
                boolean success = cancelOrder(order.getId());
                if (success) {
                    log.info("成功取消超时订单: orderId={}, orderNo={}", order.getId(), order.getOrderNo());
                } else {
                    log.error("取消超时订单失败: orderId={}", order.getId());
                }
            } catch (Exception e) {
                log.error("处理超时订单异常: orderId={}", order.getId(), e);
            }
        }
    }

    @Override
    public boolean cancelOrderByOrderNo(String orderNo) {
        log.info("根据订单号取消订单: orderNo={}", orderNo);

        OrderInfo orderInfo = orderInfoMapper.selectByOrderNo(orderNo);
        if (orderInfo == null) {
            log.warn("订单不存在: orderNo={}", orderNo);
            return false;
        }

        return cancelOrder(orderInfo.getId());
    }

    @Override
    public int countTimeoutOrders(Integer timeoutMinutes) {
        log.debug("统计超时订单数量，timeoutMinutes={}", timeoutMinutes);

        // 使用现有Mapper方法，查询超时订单但限制数量为0，只为了计数
        List<OrderInfo> timeoutOrders = orderInfoMapper.selectTimeoutOrders(timeoutMinutes, 0);
        return timeoutOrders != null ? timeoutOrders.size() : 0;
    }

    @Override
    @Transactional
    public int batchCancelOrders(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return 0;
        }

        log.info("批量取消订单，数量：{}", orderIds.size());

        int successCount = 0;
        int failCount = 0;

        for (Long orderId : orderIds) {
            try {
                boolean success = cancelOrder(orderId);
                if (success) {
                    successCount++;
                } else {
                    failCount++;
                    log.warn("批量取消订单失败：orderId={}", orderId);
                }
            } catch (Exception e) {
                failCount++;
                log.error("批量取消订单异常：orderId={}", orderId, e);
            }
        }

        log.info("批量取消订单完成：成功={}，失败={}", successCount, failCount);
        return successCount;
    }

    /**
     * 发送库存回退消息到延迟队列
     */
    private void sendStockReturnMessage(OrderInfo orderInfo) {
        StockReturnMessageDTO message = new StockReturnMessageDTO();
        message.setTierId(orderInfo.getTierId());
        message.setQuantity(orderInfo.getQuantity());
        message.setOrderId(orderInfo.getId());
        message.setReason("timeout");
        message.setTimestamp(System.currentTimeMillis());

        rabbitMQUtil.sendStockReturnMessage(message);
        log.debug("发送库存回退消息: orderId={}, tierId={}, quantity={}, delay={}分钟",
                orderInfo.getId(), orderInfo.getTierId(), orderInfo.getQuantity(), SeckillConstant.PAY_TIMEOUT_MINUTES);
    }

    /**
     * 将OrderInfo转换为OrderInfoVO
     */
    private OrderInfoVO convertToVO(OrderInfo orderInfo) {
        OrderInfoVO vo = new OrderInfoVO();
        vo.setId(orderInfo.getId());
        vo.setOrderNo(orderInfo.getOrderNo());
        vo.setUserId(orderInfo.getUserId());
        vo.setEventId(orderInfo.getEventId());
        vo.setSessionId(orderInfo.getSessionId());
        vo.setTierId(orderInfo.getTierId());
        vo.setQuantity(orderInfo.getQuantity());
        vo.setTotalAmount(orderInfo.getTotalAmount());
        vo.setOrderStatus(orderInfo.getOrderStatus());
        vo.setPayTime(orderInfo.getPayTime());
        vo.setCreateTime(orderInfo.getCreateTime());
        vo.setUpdateTime(orderInfo.getUpdateTime());

        // 查询关联信息
        TicketTier ticketTier = ticketTierMapper.selectById(orderInfo.getTierId());
        if (ticketTier != null) {
            vo.setTierName(ticketTier.getTierName());
        }

        Event event = eventMapper.selectById(orderInfo.getEventId());
        if (event != null) {
            vo.setEventName(event.getName());
        }

        EventSession session = eventSessionMapper.selectById(orderInfo.getSessionId());
        if (session != null) {
            vo.setSessionName(session.getSessionName());
            vo.setSessionTime(session.getSessionTime());
        }

        return vo;
    }
}