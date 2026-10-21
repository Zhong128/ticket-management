package org.example.ticketmanagement.trade.payment;

import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.mapper.PaymentNotifyLogMapper;
import org.example.ticketmanagement.mapper.TradePaymentMapper;
import org.example.ticketmanagement.pojo.PaymentNotifyLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentNotificationAudit {

    private final TradePaymentMapper paymentMapper;
    private final PaymentNotifyLogMapper logMapper;
    private final Clock clock;

    @Transactional
    public void recordHandled(String paymentNo, String tradeNo, String status, BigDecimal amount) {
        LocalDateTime now = LocalDateTime.now(clock);
        String summary = "total_amount=" + amount.toPlainString();

        // 首次验签通过的回调时间保留在支付流水，后续重复送达不覆盖。
        if (paymentMapper.recordNotification(paymentNo, summary, now) != 1) {
            throw new IllegalStateException("支付回调审计流水不存在");
        }

        // 每次成功处理的送达各记一条，不把重复通知当作重复付款。
        PaymentNotifyLog log = new PaymentNotifyLog();
        log.setPaymentNo(paymentNo);
        log.setTradeNo(tradeNo);
        log.setNotifyData(summary);
        log.setNotifyStatus(status);
        log.setProcessStatus(1);
        log.setCreateTime(now);
        if (logMapper.insert(log) != 1) {
            throw new IllegalStateException("支付回调审计日志写入失败");
        }
    }
}
