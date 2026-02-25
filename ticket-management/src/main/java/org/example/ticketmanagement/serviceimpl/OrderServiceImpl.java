// org/example/ticketmanagement/service/impl/OrderServiceImpl.java
package org.example.ticketmanagement.serviceimpl;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.OrderConstant;
import org.example.ticketmanagement.constant.RoleConstant;
import org.example.ticketmanagement.dto.OrderQueryDTO;
import org.example.ticketmanagement.dto.OrderUpdateDTO;
import org.example.ticketmanagement.dto.SeckillMessageDTO;
import org.example.ticketmanagement.dto.StockReturnMessageDTO;
import org.example.ticketmanagement.mapper.*;
import org.example.ticketmanagement.pojo.*;
import org.example.ticketmanagement.service.OrderService;
import org.example.ticketmanagement.service.StockService;
import org.example.ticketmanagement.util.OrderNoGenerator;
import org.example.ticketmanagement.util.RabbitMQUtil;
import org.example.ticketmanagement.vo.OrderInfoVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final UserMapper userMapper;
    private final StockService stockService;
    private final RabbitMQUtil rabbitMQUtil;

    // ==================== 基础订单操作 ====================

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

            // 2. 更新数据库库存（关键修改）
            int updateResult = ticketTierMapper.updateStock(seckillMessage.getTierId(), seckillMessage.getQuantity());
            if (updateResult <= 0) {
                throw new RuntimeException("库存更新失败，可能库存不足");
            }
            log.info("数据库库存更新成功: tierId={}, quantity={}", seckillMessage.getTierId(), seckillMessage.getQuantity());

            // 3. 创建订单对象
            OrderInfo orderInfo = new OrderInfo();
            orderInfo.setOrderNo(seckillMessage.getOrderNo());
            orderInfo.setUserId(seckillMessage.getUserId());
            orderInfo.setEventId(seckillMessage.getEventId());
            orderInfo.setSessionId(seckillMessage.getSessionId());
            orderInfo.setTierId(seckillMessage.getTierId());
            orderInfo.setQuantity(seckillMessage.getQuantity());
            orderInfo.setTotalAmount(seckillMessage.getTotalAmount());
            orderInfo.setOrderStatus(OrderConstant.ORDER_STATUS_PENDING);
            orderInfo.setCreateTime(LocalDateTime.now());
            orderInfo.setUpdateTime(LocalDateTime.now());

            // 4. 插入订单到数据库
            int result = orderInfoMapper.insert(orderInfo);
            if (result <= 0) {
                throw new RuntimeException("订单创建失败");
            }

            log.info("订单创建成功: orderId={}, orderNo={}", orderInfo.getId(), orderInfo.getOrderNo());

            // 5. 发送库存回退消息到延迟队列（用于超时取消订单）
            sendStockReturnMessage(orderInfo);

            return orderInfo.getId();

        } catch (Exception e) {
            log.error("创建订单失败: orderNo={}", seckillMessage.getOrderNo(), e);
            // 创建订单失败，恢复Redis库存
            stockService.restoreStock(seckillMessage.getTierId(), seckillMessage.getQuantity());
            // 删除用户抢票记录
            stockService.deleteUserSeckillRecord(seckillMessage.getUserId(), seckillMessage.getTierId());
            throw new RuntimeException("订单创建失败", e);
        }
    }

    @Override
    public OrderInfoVO getOrderByOrderNo(String orderNo) {
        OrderInfo orderInfo = orderInfoMapper.selectByOrderNo(orderNo);
        return convertToVO(orderInfo);
    }


    @Override
    @Transactional
    public boolean cancelOrder(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (orderInfo == null) {
            log.warn("要取消的订单不存在: orderId={}", orderId);
            return false;
        }

        // 只能取消待支付状态的订单
        if (!orderInfo.getOrderStatus().equals(OrderConstant.ORDER_STATUS_PENDING)) {
            log.warn("订单状态不是待支付，无法取消: orderId={}, status={}",
                    orderId, orderInfo.getOrderStatus());
            return false;
        }

        try {
            // 1. 恢复库存
            stockService.restoreStock(orderInfo.getTierId(), orderInfo.getQuantity());

            // 2. 删除用户抢票记录
            stockService.deleteUserSeckillRecord(orderInfo.getUserId(), orderInfo.getTierId());

            // 3. 更新订单状态为已取消
            orderInfo.setOrderStatus(OrderConstant.ORDER_STATUS_CANCELED);
            orderInfo.setUpdateTime(LocalDateTime.now());

            int result = orderInfoMapper.updateStatus(orderInfo);
            boolean success = result > 0;

            if (success) {
                log.info("取消订单成功: orderId={}, orderNo={}", orderId, orderInfo.getOrderNo());
            }

            return success;

        } catch (Exception e) {
            log.error("取消订单异常: orderId={}", orderId, e);
            throw new RuntimeException("取消订单失败", e);
        }
    }

    // ==================== 客户端订单接口 ====================

    /**
     * 客户端分页查询订单列表
     */
    @Override
    public PageResult<OrderInfoVO> getUserOrders(OrderQueryDTO queryDTO, Long userId) {
        log.debug("用户查询订单列表: userId={}, page={}, size={}", userId, queryDTO.getPage(), queryDTO.getSize());

        // 强制设置用户ID，防止查询他人订单
        queryDTO.setUserId(userId);

        // 执行分页查询 - 使用selectByCondition
        List<OrderInfo> orderList = orderInfoMapper.selectByCondition(queryDTO);
        Long total = orderInfoMapper.countByCondition(queryDTO);

        // 转换为VO
        List<OrderInfoVO> voList = orderList.stream()
                .map(this::convertToSimpleVO)
                .collect(Collectors.toList());

        return new PageResult<>(voList, total, queryDTO);
    }

    @Override
    public OrderInfoVO getOrderDetail(Long orderId, Long userId) {
        log.debug("用户查询订单详情: orderId={}, userId={}", orderId, userId);

        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (orderInfo == null) {
            throw new RuntimeException("订单不存在");
        }

        // 验证权限：只能查看自己的订单
        if (!orderInfo.getUserId().equals(userId)) {
            log.warn("用户越权访问订单: userId={}, orderUserId={}", userId, orderInfo.getUserId());
            throw new RuntimeException("无权查看此订单");
        }

        return convertToVO(orderInfo);
    }

    @Override
    @Transactional
    public boolean userCancelOrder(Long orderId, Long userId) {
        log.info("用户取消订单: orderId={}, userId={}", orderId, userId);

        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (orderInfo == null) {
            throw new RuntimeException("订单不存在");
        }

        // 验证权限
        if (!orderInfo.getUserId().equals(userId)) {
            log.warn("用户越权取消订单: userId={}, orderUserId={}", userId, orderInfo.getUserId());
            throw new RuntimeException("无权操作此订单");
        }

        // 检查订单状态
        if (!orderInfo.getOrderStatus().equals(OrderConstant.ORDER_STATUS_PENDING)) {
            throw new RuntimeException("当前订单状态不可取消");
        }

        // 检查订单是否超时（可选：超时订单只能由系统取消）
        LocalDateTime deadline = orderInfo.getCreateTime().plusMinutes(OrderConstant.ORDER_TIMEOUT_MINUTES);
        if (LocalDateTime.now().isAfter(deadline)) {
            throw new RuntimeException("订单已超时，请联系客服");
        }

        return cancelOrder(orderId);
    }

    // ==================== 管理端订单接口 ====================

    /**
     * 管理端分页查询订单列表
     */
    @Override
    public PageResult<OrderInfoVO> adminQueryOrders(OrderQueryDTO queryDTO) {
        log.info("管理员查询订单列表: {}", queryDTO);

        // 管理端使用联表查询 - 使用selectAdminByCondition
        List<OrderInfoVO> voList = orderInfoMapper.selectAdminByCondition(queryDTO);
        Long total = orderInfoMapper.countAdminByCondition(queryDTO);

        return new PageResult<>(voList, total, queryDTO);
    }

    @Override
    public OrderInfoVO adminGetOrderDetail(Long orderId) {
        log.debug("管理员查询订单详情: orderId={}", orderId);

        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (orderInfo == null) {
            throw new RuntimeException("订单不存在");
        }

        return convertToAdminVO(orderInfo);
    }

    @Override
    @Transactional
    public boolean adminUpdateOrder(Long orderId, OrderUpdateDTO updateDTO, Long adminId) {
        log.info("管理员修改订单: orderId={}, adminId={}, updateDTO={}",
                orderId, adminId, updateDTO);

        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (orderInfo == null) {
            throw new RuntimeException("订单不存在");
        }

        // 记录原状态
        Integer oldStatus = orderInfo.getOrderStatus();

        // 更新订单信息
        boolean needUpdate = false;

        if (updateDTO.getOrderStatus() != null) {
            orderInfo.setOrderStatus(updateDTO.getOrderStatus());
            needUpdate = true;

            // 如果状态改为已支付，设置支付时间
            if (updateDTO.getOrderStatus().equals(OrderConstant.ORDER_STATUS_PAID)
                    && orderInfo.getPayTime() == null) {
                orderInfo.setPayTime(LocalDateTime.now());
            }
        }

        if (updateDTO.getTotalAmount() != null) {
            orderInfo.setTotalAmount(updateDTO.getTotalAmount());
            needUpdate = true;
        }

        if (updateDTO.getQuantity() != null) {
            orderInfo.setQuantity(updateDTO.getQuantity());
            needUpdate = true;
        }

        if (!needUpdate) {
            log.warn("没有需要更新的字段");
            return false;
        }

        orderInfo.setUpdateTime(LocalDateTime.now());
        int result = orderInfoMapper.updateOrderInfo(orderInfo);
        boolean success = result > 0;

        if (success) {
            log.info("管理员修改订单成功: orderId={}, oldStatus={}, newStatus={}, adminId={}",
                    orderId, oldStatus, orderInfo.getOrderStatus(), adminId);

            // TODO: 记录操作日志
            // adminLogService.logOrderUpdate(orderId, adminId, oldStatus, updateDTO);
        }

        return success;
    }

    // ==================== 定时任务/系统内部调用 ====================

    @Override
    public void processTimeoutOrders(Integer timeoutMinutes, Integer batchSize) {
        log.info("开始处理超时未支付订单: timeoutMinutes={}, batchSize={}", timeoutMinutes, batchSize);

        Integer actualTimeout = OrderConstant.ORDER_TIMEOUT_MINUTES + 1;
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
                }
            } catch (Exception e) {
                log.error("取消超时订单异常: orderId={}", order.getId(), e);
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
        for (Long orderId : orderIds) {
            try {
                if (cancelOrder(orderId)) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("批量取消订单异常：orderId={}", orderId, e);
            }
        }

        log.info("批量取消订单完成：成功={}", successCount);
        return successCount;
    }

    @Override
    @Transactional
    public boolean updateOrderStatus(Long orderId, Integer orderStatus) {
        log.info("更新订单状态: orderId={}, newStatus={}", orderId, orderStatus);

        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (orderInfo == null) {
            log.warn("订单不存在: orderId={}", orderId);
            return false;
        }

        // 记录原状态
        Integer oldStatus = orderInfo.getOrderStatus();

        // 更新状态
        orderInfo.setOrderStatus(orderStatus);
        orderInfo.setUpdateTime(LocalDateTime.now());

        // 如果是支付成功，设置支付时间
        if (orderStatus.equals(OrderConstant.ORDER_STATUS_PAID)) {
            orderInfo.setPayTime(LocalDateTime.now());
        }

        int result = orderInfoMapper.updateStatus(orderInfo);
        boolean success = result > 0;

        if (success) {
            log.info("更新订单状态成功: orderId={}, oldStatus={}, newStatus={}",
                    orderId, oldStatus, orderStatus);
        } else {
            log.error("更新订单状态失败: orderId={}, status={}", orderId, orderStatus);
        }

        return success;
    }

    @Override
    public PageResult<OrderInfoVO> getOrdersByUserEmail(String email, OrderQueryDTO queryDTO) {
        log.info("管理端根据邮箱查询用户订单: email={}, queryDTO={}", email, queryDTO);

        try {
            // 1. 先根据邮箱查找用户
            User user = userMapper.getUserByEmail(email);
            if (user == null) {
                log.warn("用户不存在，邮箱: {}", email);
                throw new RuntimeException("用户不存在");
            }

            // 2. 设置用户ID到查询条件中
            queryDTO.setUserId(user.getId());

            // 3. 执行分页查询 - 使用管理端联表查询
            List<OrderInfoVO> orderList = orderInfoMapper.selectAdminByCondition(queryDTO);
            Long total = orderInfoMapper.countAdminByCondition(queryDTO);

            // 4. 计算有效页码
            int maxPage = (int) Math.ceil((double) total / queryDTO.getSize());
            if (queryDTO.getPage() > maxPage && maxPage > 0) {
                queryDTO.setPage(maxPage);
                orderList = orderInfoMapper.selectAdminByCondition(queryDTO);
            }

            log.info("根据邮箱查询订单成功: email={}, userId={}, 总数: {}, 当前页数量: {}", 
                    email, user.getId(), total, orderList.size());

            return new PageResult<>(orderList, total, queryDTO);

        } catch (IllegalArgumentException e) {
            log.warn("分页参数错误: {}", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.warn("根据邮箱查询订单失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("根据邮箱查询订单异常: {}", e.getMessage(), e);
            throw new RuntimeException("查询订单列表失败");
        }
    }

    // ==================== 私有辅助方法 ====================

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

        rabbitMQUtil.sendStockReturnDelayMessage(message);
        log.debug("发送库存回退消息: orderId={}, tierId={}, quantity={}, delay={}分钟",
                orderInfo.getId(), orderInfo.getTierId(), orderInfo.getQuantity(),
                OrderConstant.ORDER_TIMEOUT_MINUTES);
    }

    /**
     * 转换为完整VO（包含所有关联信息）
     */
    private OrderInfoVO convertToVO(OrderInfo orderInfo) {
        if (orderInfo == null) return null;

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
            vo.setTierPrice(ticketTier.getCurrentPrice());
        }

        Event event = eventMapper.selectById(orderInfo.getEventId());
        if (event != null) {
            vo.setEventName(event.getName());
            vo.setEventImage(event.getCoverImage());
        }

        EventSession session = eventSessionMapper.selectById(orderInfo.getSessionId());
        if (session != null) {
            vo.setSessionName(session.getSessionName());
            vo.setSessionTime(session.getSessionTime());
        }

        return vo;
    }

    /**
     * 转换为简单VO（客户端用，不带敏感信息）
     */
    private OrderInfoVO convertToSimpleVO(OrderInfo orderInfo) {
        OrderInfoVO vo = convertToVO(orderInfo);
        // 客户端不需要用户ID等敏感信息
        vo.setUserId(null);
        return vo;
    }

    /**
     * 转换为管理端VO（包含用户信息等）
     */
    private OrderInfoVO convertToAdminVO(OrderInfo orderInfo) {
        OrderInfoVO vo = convertToVO(orderInfo);

        // 查询用户信息
        User user = userMapper.getUserById(orderInfo.getUserId());
        if (user != null) {
            vo.setUserName(user.getUsername());
        }

        return vo;
    }
}