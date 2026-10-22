package org.example.ticketmanagement.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** 统一32位订单号入口，Redis序列丢失不能造成编号重用。 */
@Component
public class OrderNoGenerator {
    public String generateOrderNo() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
