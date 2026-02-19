// org/example/ticketmanagement/util/PaymentNoGenerator.java
package org.example.ticketmanagement.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 支付流水号生成器
 * 格式：PAY + yyyyMMdd + 8位序列号
 * 示例：PAY2025021300000001
 */
@Component
public class PaymentNoGenerator {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String PAYMENT_NO_KEY_PREFIX = "payment:no:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public PaymentNoGenerator(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成支付流水号
     */
    public String generatePaymentNo() {
        String date = LocalDateTime.now().format(DATE_FORMAT);
        String key = PAYMENT_NO_KEY_PREFIX + date;

        // Redis自增，每天从1开始
        Long sequence = redisTemplate.opsForValue().increment(key);

        // 设置过期时间（2天）
        if (sequence != null && sequence == 1) {
            redisTemplate.expire(key, 2, TimeUnit.DAYS);
        }

        // 格式化：PAY + 日期 + 8位序列号
        return "PAY" + date + String.format("%08d", sequence);
    }

    /**
     * 从支付流水号中提取日期
     */
    public static String extractDate(String paymentNo) {
        if (paymentNo != null && paymentNo.length() >= 12) {
            return paymentNo.substring(3, 11);
        }
        return null;
    }
}