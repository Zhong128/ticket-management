package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.dto.SeckillOrderDTO;
import org.example.ticketmanagement.mapper.EventMapper;
import org.example.ticketmanagement.mapper.EventSessionMapper;
import org.example.ticketmanagement.mapper.TicketTierMapper;
import org.example.ticketmanagement.mapper.TradeStockMapper;
import org.example.ticketmanagement.service.SeckillService;
import org.example.ticketmanagement.trade.core.TradeOperations;
import org.example.ticketmanagement.vo.SeckillResultVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final TradeOperations trade;
    private final TradeStockMapper stockMapper;
    private final TicketTierMapper tiers;
    private final EventSessionMapper sessions;
    private final EventMapper events;
    private final Clock clock;

    @Override
    public SeckillResultVO seckill(Long userId, SeckillOrderDTO dto) {
        if (dto.getQuantity() == null) {
            return SeckillResultVO.fail("购买数量不能为空");
        }
        String key = dto.getRequestKey();
        if (key == null || key.isBlank()) {
            key = UUID.randomUUID().toString();
        }
        try {
            var accepted = trade.reserve(userId, dto.getTierId(), dto.getQuantity(), key);
            SeckillResultVO result = SeckillResultVO.success((String) accepted.get("orderNo"),
                    (Long) accepted.get("orderId"), (BigDecimal) accepted.get("totalAmount"), null,
                    (BigDecimal) accepted.get("price"), ((Number) accepted.get("quantity")).intValue());
            result.setRequestId(((Number) accepted.get("requestId")).longValue());
            result.setRequestStatus((String) accepted.get("requestStatus"));
            result.setExpireTime((LocalDateTime) accepted.get("expireTime"));
            result.setMessage("申请已受理，请查询落单结果");
            return result;
        } catch (IllegalArgumentException | IllegalStateException e) {
            return SeckillResultVO.fail(e.getMessage());
        }
    }

    @Override
    public boolean checkSeckillQualification(Long userId, Long tierId, Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > 5) {
            return false;
        }
        var tier = tiers.selectById(tierId);
        if (tier == null || !Integer.valueOf(1).equals(tier.getStatus()) || tier.getAvailableStock() < quantity) {
            return false;
        }
        var session = sessions.selectById(tier.getSessionId());
        var event = events.selectById(tier.getEventId());
        if (session == null || !Integer.valueOf(1).equals(session.getStatus()) || event == null
                || !Integer.valueOf(1).equals(event.getStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (event.getSaleStartTime() != null && now.isBefore(event.getSaleStartTime())) {
            return false;
        }
        if (event.getSaleEndTime() != null && !now.isBefore(event.getSaleEndTime())) {
            return false;
        }
        return stockMapper.countUserActiveOrders(userId, tierId) == 0;
    }

    @Override
    public Integer getRealTimeStock(Long tierId) {
        return stockMapper.selectAvailableStock(tierId);
    }
}
