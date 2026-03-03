
package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.SeckillConstant;
import org.example.ticketmanagement.dto.SeckillMessageDTO;
import org.example.ticketmanagement.dto.SeckillOrderDTO;
import org.example.ticketmanagement.mapper.EventMapper;
import org.example.ticketmanagement.mapper.EventSessionMapper;
import org.example.ticketmanagement.mapper.TicketTierMapper;
import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.pojo.Event;
import org.example.ticketmanagement.pojo.EventSession;
import org.example.ticketmanagement.pojo.TicketTier;
import org.example.ticketmanagement.pojo.User;
import org.example.ticketmanagement.service.EmailService;
import org.example.ticketmanagement.service.SeckillService;
import org.example.ticketmanagement.service.StockService;
import org.example.ticketmanagement.service.SeckillLockService;
import org.example.ticketmanagement.util.OrderNoGenerator;
import org.example.ticketmanagement.util.RabbitMQUtil;
import org.example.ticketmanagement.vo.SeckillResultVO;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;


@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final TicketTierMapper ticketTierMapper;
    private final EventSessionMapper eventSessionMapper;
    private final StockService stockService;
    private final EventMapper eventMapper;
    private final SeckillLockService seckillLockService;
    private final RabbitMQUtil rabbitMQUtil;
    private final OrderNoGenerator orderNoGenerator;
    private final EmailService emailService;
    private final UserMapper userMapper;

    @Override
    public SeckillResultVO seckill(Long userId, SeckillOrderDTO seckillOrderDTO) {
        Long tierId = seckillOrderDTO.getTierId();
        Integer quantity = seckillOrderDTO.getQuantity();

        log.info("用户开始抢票: userId={}, tierId={}, quantity={}", userId, tierId, quantity);

        // 1. 基础参数校验
        if (quantity == null || quantity <= 0) {
            return SeckillResultVO.fail("购买数量必须大于0");
        }
        if (quantity > SeckillConstant.MAX_QUANTITY) {
            return SeckillResultVO.fail(SeckillConstant.ERROR_MAX_QUANTITY+",单次最多购买" + SeckillConstant.MAX_QUANTITY + "张");
        }

        // 2. 使用用户锁防止重复提交（等待3秒，持有5秒）
        return seckillLockService.executeWithUserLock(userId, tierId, () -> {
            // 3. 使用票档锁保护核心业务逻辑（等待5秒，持有10秒）
            return seckillLockService.executeWithTierLock(tierId, () -> {
                return doSeckill(userId, tierId, quantity);
            });
        });
    }

    /**
     * 执行抢票核心逻辑
     */
    private SeckillResultVO doSeckill(Long userId, Long tierId, Integer quantity) {
        // 1. 检查票档信息
        TicketTier ticketTier = ticketTierMapper.selectById(tierId);
        if (ticketTier == null) {
            return SeckillResultVO.fail(SeckillConstant.ERROR_TIER_NOT_FOUND);
        }

        // 2. 检查票档状态
        if (ticketTier.getStatus() != 1) {
            return SeckillResultVO.fail(SeckillConstant.ERROR_TIER_NOT_AVAILABLE);
        }

        // 3. 检查场次状态
        EventSession session = eventSessionMapper.selectById(ticketTier.getSessionId());
        if (session == null || session.getStatus() != 1) {
            return SeckillResultVO.fail("场次不存在或不可用");
        }

        // 4. 检查演出是否在售票时间内（关键修改）
        Event event = eventMapper.selectById(ticketTier.getEventId());
        if (event == null || event.getStatus() != 1) {
            return SeckillResultVO.fail("演出不存在或不可用");
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (event.getSaleStartTime() != null && event.getSaleStartTime().isAfter(now)) {
            return SeckillResultVO.fail("抢票尚未开始"); // 还未到售票开始时间
        }
        if (event.getSaleEndTime() != null && event.getSaleEndTime().isBefore(now)) {
            return SeckillResultVO.fail("抢票已结束"); // 已超过售票结束时间
        }

        // 5. 检查用户是否已抢过该票档（防重）
        if (stockService.hasUserSeckilled(userId, tierId)) {
            return SeckillResultVO.fail(SeckillConstant.ERROR_USER_REPEAT);
        }

        // 6. 预扣库存（原子操作）
        Long remainingStock = stockService.decrStock(tierId, quantity);
        if (remainingStock == null || remainingStock < 0) {
            return SeckillResultVO.fail(SeckillConstant.ERROR_STOCK_NOT_ENOUGH);
        }

        try {
            // 7. 生成订单号
            String orderNo = orderNoGenerator.generateOrderNo();

            // 8. 设置用户抢票记录（防重，过期时间 30 分钟）
            stockService.setUserSeckillRecord(userId, tierId, 30 * 60);

            // 9. 构建抢票消息
            SeckillMessageDTO message = buildSeckillMessage(userId, ticketTier, quantity, orderNo);

            // 10. 发送消息到 RabbitMQ，异步创建订单
            rabbitMQUtil.sendSeckillOrderMessage(message);

            // 11. 发送成功通知邮件（使用封装的方法）
            sendSuccessEmail(userId, message, event, session, ticketTier);

            // 12. 返回抢票成功结果
            log.info("抢票成功：userId={}, tierId={}, orderNo={}", userId, tierId, orderNo);

            return SeckillResultVO.success(
                    orderNo,
                    null,  // orderId 暂时为空，符合新的接口定义
                    message.getTotalAmount(),
                    ticketTier.getTierName(),
                    ticketTier.getCurrentPrice(),
                    quantity
            );

        } catch (Exception e) {
            // 如果后续步骤失败，回滚库存
            log.error("抢票后续步骤失败，回滚库存：userId={}, tierId={}, quantity={}",
                    userId, tierId, quantity, e);
            stockService.restoreStock(tierId, quantity);
            stockService.deleteUserSeckillRecord(userId, tierId);

            return SeckillResultVO.fail(SeckillConstant.ERROR_SYSTEM_BUSY);
        }
    }

    @Override
    public boolean checkSeckillQualification(Long userId, Long tierId, Integer quantity) {
        // 1. 检查票档是否存在且可用
        TicketTier ticketTier = ticketTierMapper.selectById(tierId);
        if (ticketTier == null || ticketTier.getStatus() != 1) {
            return false;
        }

        // 2. 检查场次是否可用
        EventSession session = eventSessionMapper.selectById(ticketTier.getSessionId());
        if (session == null || session.getStatus() != 1) {
            return false;
        }

        // 3. 检查演出是否在售票时间内
        Event event = eventMapper.selectById(ticketTier.getEventId());
        if (event == null || event.getStatus() != 1) {
            return false;
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (event.getSaleStartTime() != null && event.getSaleStartTime().isAfter(now)) {
            return false; // 还未到售票开始时间
        }
        if (event.getSaleEndTime() != null && event.getSaleEndTime().isBefore(now)) {
            return false; // 已超过售票结束时间
        }

        // 4. 检查库存是否充足
        if (!stockService.hasEnoughStock(tierId, quantity)) {
            return false;
        }

        // 5. 检查用户是否已抢过
        if (stockService.hasUserSeckilled(userId, tierId)) {
            return false;
        }

        // 6. 检查购买数量限制
        if (quantity > SeckillConstant.MAX_QUANTITY) {
            return false;
        }

        return true;
    }

    @Override
    public boolean preDeductStock(Long tierId, Integer quantity) {
        Long result = stockService.decrStock(tierId, quantity);
        return result != null && result >= 0;
    }

    @Override
    public void restoreStock(Long tierId, Integer quantity) {
        stockService.restoreStock(tierId, quantity);
    }

    @Override
    public Integer getRealTimeStock(Long tierId) {
        return stockService.getStock(tierId);
    }

    /**
     * 构建抢票消息
     */
    private SeckillMessageDTO buildSeckillMessage(Long userId, TicketTier ticketTier,
                                                  Integer quantity, String orderNo) {
        SeckillMessageDTO message = new SeckillMessageDTO();
        message.setUserId(userId);
        message.setEventId(ticketTier.getEventId());
        message.setSessionId(ticketTier.getSessionId());
        message.setTierId(ticketTier.getId());
        message.setTierName(ticketTier.getTierName());
        message.setPrice(ticketTier.getCurrentPrice());
        message.setQuantity(quantity);

        // 计算总金额
        BigDecimal totalAmount = ticketTier.getCurrentPrice()
                .multiply(new BigDecimal(quantity));
        message.setTotalAmount(totalAmount);

        message.setOrderNo(orderNo);
        message.setRequestId(System.currentTimeMillis());
        message.setTimestamp(System.currentTimeMillis());

        return message;
    }

    /**
     * 发送抢票成功通知邮件（调用 EmailService 的封装方法）
     */
    private void sendSuccessEmail(Long userId, SeckillMessageDTO message, 
                                  Event event, EventSession session, TicketTier ticketTier) {
        try {
            User user = userMapper.getUserById(userId);
            if (user == null) {
                log.warn("用户不存在，无法发送邮件：userId={}", userId);
                return;
            }

            // 调用 EmailService 的封装方法
            emailService.sendSeckillSuccessEmail(user, message, event, session, ticketTier);
        } catch (Exception e) {
            log.error("发送抢票成功邮件异常：userId={}", userId, e);
            // 邮件发送失败不影响抢票流程
        }
    }
}