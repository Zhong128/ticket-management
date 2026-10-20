package org.example.ticketmanagement.trade.payment;

import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.mapper.TradePaymentMapper;
import org.example.ticketmanagement.pojo.PaymentRecord;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class PaymentRepository {

    private final TradePaymentMapper mapper;

    public PaymentRecord find(String no) {
        return mapper.find(no);
    }

    public PaymentRecord lock(String no) {
        return mapper.lock(no);
    }

    public Map<String, Object> lockOrder(long id) {
        var order = mapper.lockOrder(id);
        if (order == null) {
            throw new EmptyResultDataAccessException(1);
        }
        return order;
    }

    public List<PaymentRecord> paymentsForOrder(long id) {
        return mapper.paymentsForOrder(id);
    }

    public List<Long> payableOrders(long user, LocalDateTime now) {
        return mapper.payableOrders(user, now);
    }

    public List<PaymentRecord> waiting() {
        return mapper.waiting();
    }

    public void reconciled(long id, LocalDateTime now) {
        mapper.reconciled(id, now);
    }

    public List<Long> expiredOrders(LocalDateTime now) {
        return mapper.expiredOrders(now);
    }

    public void insert(PaymentRecord p) {
        mapper.insert(p);
        Objects.requireNonNull(p.getId(), "payment insert must return its generated id");
    }

    public void paid(long id, String tradeNo, LocalDateTime now) {
        mapper.paid(id, tradeNo, now);
    }

    public void close(long id, LocalDateTime now) {
        mapper.close(id, now);
    }
}
