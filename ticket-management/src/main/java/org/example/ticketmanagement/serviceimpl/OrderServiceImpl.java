// org/example/ticketmanagement/service/impl/OrderServiceImpl.java
package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.OrderConstant;
import org.example.ticketmanagement.dto.OrderQueryDTO;
import org.example.ticketmanagement.dto.OrderUpdateDTO;
import org.example.ticketmanagement.dto.SeckillMessageDTO;
import org.example.ticketmanagement.mapper.*;
import org.example.ticketmanagement.pojo.*;
import org.example.ticketmanagement.service.OrderService;
import org.example.ticketmanagement.trade.core.TradeOperations;
import org.example.ticketmanagement.trade.payment.TradePaymentPort;
import org.example.ticketmanagement.vo.OrderInfoVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
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
    private final TradeOperations tradeOperations;
    private final ObjectProvider<TradePaymentPort> paymentPort;
    private final TradeOrderQueryMapper tradeOrderQueryMapper;
    private final Clock clock;

    @Value("${trade.enabled:false}")
    private boolean tradeEnabled;

    // ==================== 基础订单操作 ====================
    @Override
    public Long createOrder(SeckillMessageDTO message) {
        checkTradeEnabled();
        // Legacy messages cannot introduce a second inventory deduction.
        var ids = tradeOrderQueryMapper.selectReservationIds(message.getOrderNo(), message.getUserId());
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("缺少持久化库存预占，拒绝旧消息直接落单");
        }
        return tradeOperations.createOrder(ids.getFirst());
    }

    @Override
    public OrderInfoVO getOrderByOrderNo(String orderNo) {
        OrderInfo orderInfo = orderInfoMapper.selectByOrderNo(orderNo);
        return convertToVO(orderInfo);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean cancelOrder(Long orderId) {
        return cancel(orderId, null);
    }

    private boolean cancel(Long orderId, Long userId) {
        if (!tradeOperations.beginCancel(orderId, userId)) {
            return false;
        }
        TradePaymentPort port = paymentPort.getIfAvailable();
        // Durable ORDER_CLOSE task retains ownership.
        if (port == null) {
            return false;
        }
        TradePaymentPort.CloseDecision decision = port.closeForOrder(orderId);
        return decision == TradePaymentPort.CloseDecision.CLOSED && tradeOperations.finishCancel(orderId);
    }

    // ==================== 客户端订单接口 ====================
    /**
     * 客户端分页查询订单列表
     */
    @Override
    public PageResult<OrderInfoVO> getUserOrders(OrderQueryDTO queryDTO, Long userId) {
        log.debug("用户查询订单列表: userId={}, page={}, size={}", userId, queryDTO.getPage(), queryDTO.getSize());
        queryDTO.setUserId(userId);
        // 校验价格范围参数
        if (!queryDTO.isValidAmountRange()) {
            String errorMsg = queryDTO.getAmountRangeErrorMessage();
            log.warn("价格范围参数校验失败: userId={}, error={}", userId, errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
        // 执行分页查询
        List<OrderInfo> orderList = orderInfoMapper.selectByCondition(queryDTO);
        Long total = orderInfoMapper.countByCondition(queryDTO);
        // 转换为VO
        List<OrderInfoVO> voList = orderList.stream().map(this::convertToSimpleVO).collect(Collectors.toList());
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
    public OrderInfo getLatestPendingOrderByUser(Long userId) {
        log.debug("获取用户最新待支付订单: userId={}", userId);
        return orderInfoMapper.selectByUserIdAndStatus(userId, OrderConstant.ORDER_STATUS_PENDING);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean userCancelOrder(Long orderId, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return cancel(orderId, userId);
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean adminUpdateOrder(Long orderId, OrderUpdateDTO updateDTO, Long adminId) {
        checkTradeEnabled();
        if (updateDTO.getTotalAmount() != null || updateDTO.getQuantity() != null) {
            throw new IllegalArgumentException("订单金额和数量不可修改");
        }
        if (updateDTO.getOrderStatus() != null) {
            if (updateDTO.getOrderStatus() != OrderConstant.ORDER_STATUS_CANCELED) {
                throw new IllegalArgumentException("订单状态必须通过支付或取消流程更新");
            }
            return cancelOrder(orderId);
        }
        if (updateDTO.getRemark() == null) {
            return false;
        }
        if (updateDTO.getRemark().length() > 500) {
            throw new IllegalArgumentException("备注最多500字符");
        }
        return tradeOrderQueryMapper.updateRemark(orderId, updateDTO.getRemark(), LocalDateTime.now(clock)) == 1;
    }

    // ==================== 定时任务/系统内部调用 ====================
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean updateOrderStatus(Long orderId, Integer orderStatus) {
        if (Integer.valueOf(OrderConstant.ORDER_STATUS_CANCELED).equals(orderStatus)) {
            return cancelOrder(orderId);
        }
        throw new IllegalArgumentException("必须通过统一支付状态机更新订单状态");
    }

    // ==================== 私有辅助方法 ====================
    private void checkTradeEnabled() {
        if (!tradeEnabled) {
            throw new IllegalStateException("交易功能尚未启用");
        }
    }

    /**
     * 转换为完整VO（包含所有关联信息）
     */
    private OrderInfoVO convertToVO(OrderInfo orderInfo) {
        if (orderInfo == null) {
            return null;
        }
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
        vo.setExpireTime(orderInfo.getExpireTime() == null ? orderInfo.getCreateTime().plusMinutes(30)
                : orderInfo.getExpireTime());
        vo.setCancelReason(orderInfo.getCancelReason());
        vo.setRemark(orderInfo.getRemark());
        // 查询关联信息并处理null值
        TicketTier ticketTier = ticketTierMapper.selectById(orderInfo.getTierId());
        if (ticketTier != null) {
            vo.setTierName(ticketTier.getTierName());
            vo.setTierPrice(ticketTier.getCurrentPrice());
        } else {
            vo.setTierName("");
            vo.setTierPrice(BigDecimal.ZERO);
        }
        Event event = eventMapper.selectById(orderInfo.getEventId());
        if (event != null) {
            vo.setEventName(event.getName());
            vo.setEventImage(event.getCoverImage());
        } else {
            vo.setEventName("");
            vo.setEventImage("");
        }
        EventSession session = eventSessionMapper.selectById(orderInfo.getSessionId());
        if (session != null) {
            vo.setSessionName(session.getSessionName());
            vo.setSessionTime(session.getSessionTime());
        } else {
            vo.setSessionName("");
            vo.setSessionTime(null);
            vo.setVenueName("");
        }
        return vo;
    }

    /**
     * 转换为简单VO（客户端用，不带敏感信息）
     */
    private OrderInfoVO convertToSimpleVO(OrderInfo orderInfo) {
        OrderInfoVO vo = convertToVO(orderInfo);
        if (vo != null) {
            // 客户端不需要用户ID等敏感信息
            vo.setUserId(null);
            // 处理可能为null的字段，设置默认值
            if (vo.getUserName() == null) {
                vo.setUserName("");
            }
            if (vo.getVenueName() == null) {
                vo.setVenueName("");
            }
            // payTime可以为null（未支付订单）
        }
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
