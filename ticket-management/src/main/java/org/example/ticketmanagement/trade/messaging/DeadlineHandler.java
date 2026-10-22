package org.example.ticketmanagement.trade.messaging;

import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.mapper.TradeRecoveryMapper;
import org.example.ticketmanagement.service.OrderService;
import org.example.ticketmanagement.service.ReservationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DeadlineHandler {

    private final TradeRecoveryMapper db;
    private final ReservationService core;
    private final OrderService orders;
    private final OutboxStore outbox;
    private final Clock clock;

    @Transactional
    public void handle(long reservationId) {
        // 1. 查预占状态，已结束的申请无需再次处理。
        List<Map<String, Object>> rows = db.findReservationDeadline(reservationId);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> row = rows.getFirst();
        String status = (String) row.get("status");
        if (!Set.of("RESERVED", "ORDER_CREATED").contains(status)) {
            return;
        }

        // 2. 消息提前到达时，按原截止时间重新投递。
        LocalDateTime expires = asLocalDateTime(row.get("expires_at"));
        if (LocalDateTime.now(clock).isBefore(expires)) {
            outbox.enqueue("ORDER_DEADLINE", "deadline-retry:" + reservationId + ":" + UUID.randomUUID(),
                    Map.of("reservationId", reservationId));
            return;
        }

        // 3. 未建单可直接释放；已建单由关单流程确认渠道状态。
        if (row.get("order_id") == null) {
            core.releaseUnordered(reservationId, "TIMEOUT");
        }
    }

    public void cancelIfExpired(long reservationId) {
        // 订单取消可能调用支付渠道，放在上方事务之外执行。
        List<Map<String, Object>> rows = db.findExpiredOrderedReservation(reservationId, LocalDateTime.now(clock));
        if (!rows.isEmpty() && rows.getFirst().get("order_id") != null) {
            orders.cancelOrder(((Number) rows.getFirst().get("order_id")).longValue());
        }
    }

    private static LocalDateTime asLocalDateTime(Object value) {
        return value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value;
    }
}
