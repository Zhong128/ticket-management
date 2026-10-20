package org.example.ticketmanagement.trade.payment;

public interface TradePaymentPort {

    enum CloseDecision {

        CLOSED, PAID, UNKNOWN
    }

    CloseDecision closeForOrder(long orderId);
}
