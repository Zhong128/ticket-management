package org.example.ticketmanagement.trade.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.mapper.TradeRecoveryMapper;
import org.example.ticketmanagement.service.OrderService;
import org.example.ticketmanagement.trade.core.TradeOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trade.enabled", havingValue = "true")
public class TradeRecovery {

    private final TradeRecoveryMapper db;
    private final TradeOperations core;
    private final OrderService orders;
    private final Clock clock;

    @Scheduled(fixedDelay = 60000)
    public void recover() {
        LocalDateTime now = LocalDateTime.now(clock);

        // 1. 扫描预占，补上丢失的创建消息或处理已到期的申请。
        for (Map<String, Object> row : db.findRecoverableReservations()) {
            long id = ((Number) row.get("id")).longValue();
            try {
                LocalDateTime expiry = asLocalDateTime(row.get("expires_at"));
                if (!now.isBefore(expiry)) {
                    if (row.get("order_id") == null) {
                        core.releaseUnordered(id, "TIMEOUT_RECOVERY");
                    } else {
                        orders.cancelOrder(((Number) row.get("order_id")).longValue());
                    }
                } else if ("RESERVED".equals(row.get("status"))) {
                    try {
                        core.createOrder(id);
                    } catch (Exception e) {
                        core.creationFailed(id, e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Reservation recovery failed id={}", id, e);
            }
        }

        // 2. 补偿需要关单的订单，也覆盖没有预占记录的旧订单。
        for (Long id : db.findOrdersRequiringCancellation(now)) {
            try {
                orders.cancelOrder(id);
            } catch (Exception e) {
                log.error("Cancellation recovery failed order={}", id, e);
            }
        }

        // 3. 清理订单状态已经解决的关单任务。
        db.completeResolvedCloseTasks(LocalDateTime.now(clock));
    }

    private static LocalDateTime asLocalDateTime(Object value) {
        return value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value;
    }
}
