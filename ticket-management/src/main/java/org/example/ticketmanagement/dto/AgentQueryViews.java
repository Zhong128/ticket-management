package org.example.ticketmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 只向模型提供客服查询必要的字段，不透传账户、地址和订单实体。 */
public final class AgentQueryViews {

    private AgentQueryViews() {
    }

    public record Profile(boolean hasRealName, boolean hasPhone, boolean hasIdentity, String maskedPhone) {
    }

    public record Address(String id, String province, String city, String district,
                          String maskedPhone, boolean defaultAddress) {
    }

    public record Order(String id, String orderNo, String eventName, String sessionName,
                        LocalDateTime sessionTime, String tierName, Integer quantity,
                        BigDecimal totalAmount, Integer orderStatus, String orderStatusDesc,
                        LocalDateTime expireTime, LocalDateTime payTime) {
    }

    public record Tier(String id, String eventId, String sessionId, String tierName, BigDecimal price) {
    }
}
