package org.example.ticketmanagement.util;

import java.util.UUID;

/** 支付编号保持现行PAY前缀格式，退款继续支持历史最长64位流水。 */
public final class PaymentNoGenerator {
    private PaymentNoGenerator() {
    }

    public static String generatePaymentNo() {
        return "PAY" + UUID.randomUUID().toString().replace("-", "");
    }
}
