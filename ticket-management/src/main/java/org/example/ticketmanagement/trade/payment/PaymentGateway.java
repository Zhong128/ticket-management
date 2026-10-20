package org.example.ticketmanagement.trade.payment;

import org.example.ticketmanagement.pojo.PaymentRecord;

import java.math.BigDecimal;

public interface PaymentGateway {

    enum State {

        PAID, WAITING, CLOSED, NOT_FOUND, UNKNOWN
    }

    record Query(State state, String paymentNo, String tradeNo, BigDecimal amount) {

        public static Query unknown() {
            return new Query(State.UNKNOWN, null, null, null);
        }
    }

    enum RefundState {

        SUCCESS, NOT_FOUND, FAILED, UNKNOWN
    }

    record RefundResult(RefundState state, String tradeNo, String detail) {
    }

    String create(PaymentRecord payment);

    Query query(String paymentNo);

    boolean close(String paymentNo);

    RefundResult refund(String paymentNo, String refundNo, BigDecimal amount);

    RefundResult queryRefund(String paymentNo, String refundNo, BigDecimal amount);
}
