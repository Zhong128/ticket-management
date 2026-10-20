package org.example.ticketmanagement.trade.messaging;

import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.mapper.TradeRecoveryMapper;
import org.example.ticketmanagement.service.OrderService;
import org.example.ticketmanagement.trade.core.TradeOperations;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

/**
 * Opt-in migration bridge. Unmapped historical messages remain queued for manual reconciliation.
 */
@Service
@RequiredArgsConstructor
public class LegacyMessageAdapter {

    private final TradeRecoveryMapper db;
    private final TradeOperations core;
    private final OrderService orders;
    private final Clock clock;

    public void create(String orderNo) {
        List<Long> ids = db.findReservationIdsByOrderNo(orderNo);
        if (!ids.isEmpty()) {
            core.createOrder(ids.getFirst());
            return;
        }
        if (db.countOrdersByOrderNo(orderNo) > 0) {
            return;
        }
        throw new IllegalStateException("Unmapped legacy message requires migration review");
    }

    public void deadline(String orderNo) {
        for (Long id : db.findExpiredLegacyOrders(orderNo, LocalDateTime.now(clock))) {
            orders.cancelOrder(id);
        }
    }
}
