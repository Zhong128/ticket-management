// org/example/ticketmanagement/util/OrderNoGenerator.java
package org.example.ticketmanagement.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Component
public class OrderNoGenerator {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String ORDER_NO_KEY_PREFIX = "order:no:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public OrderNoGenerator(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成订单号
     * 格式：ORD + yyyyMMdd + 6位序列号
     */
    public String generateOrderNo() {
        String date = LocalDateTime.now().format(DATE_FORMAT);
        String key = ORDER_NO_KEY_PREFIX + date;

        // Redis自增，每天从1开始
        Long sequence = redisTemplate.opsForValue().increment(key);

        // 设置过期时间（2天）
        if (sequence != null && sequence == 1) {
            redisTemplate.expire(key, 2, TimeUnit.DAYS);
        }

        // 格式化：ORD + 日期 + 6位序列号
        return "ORD" + date + String.format("%06d", sequence);
    }
}