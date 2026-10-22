package org.example.ticketmanagement.dto;

/**
 * 一次Redis预扣的稳定身份；时间使用毫秒，重试不能延长原截止时间。
 */
public record RedisReservation(long tierId, long userId, String requestKey, int quantity,
        String token, long epoch, long createdAt, long expiresAt) {
}
