package org.example.ticketmanagement.trade.core;

import java.util.Map;

public interface TradeOperations {

    Map<String, Object> reserve(long userId, long tierId, int quantity, String requestKey);

    Map<String, Object> request(long reservationId, long userId);

    Long createOrder(long reservationId);

    boolean beginCancel(long orderId, Long userId);

    boolean finishCancel(long orderId);

    boolean applyPaid(long orderId, long paymentId);

    void releaseUnordered(long reservationId, String reason);

    void creationFailed(long reservationId, String error);
}
