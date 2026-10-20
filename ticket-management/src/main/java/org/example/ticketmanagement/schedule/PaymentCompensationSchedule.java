package org.example.ticketmanagement.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.service.PaymentService;
import org.example.ticketmanagement.trade.payment.RefundService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trade.enabled", havingValue = "true")
public class PaymentCompensationSchedule {

    private final PaymentService paymentService;
    private final RefundService refunds;

    @Scheduled(fixedDelay = 60000)
    public void processPaymentCompensation() {
        try {
            paymentService.processCompensation(10);
        } catch (Exception e) {
            log.error("Payment reconciliation failed", e);
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void processRefundCompensation() {
        try {
            refunds.processDueRefunds();
        } catch (Exception e) {
            log.error("Refund reconciliation failed", e);
        }
    }
}
