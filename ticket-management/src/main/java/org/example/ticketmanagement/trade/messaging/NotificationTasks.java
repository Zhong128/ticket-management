package org.example.ticketmanagement.trade.messaging;

import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.mapper.TradeTaskMapper;
import org.example.ticketmanagement.service.EmailService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trade.enabled", havingValue = "true")
public class NotificationTasks {

    private final TradeTaskMapper db;
    private final EmailService email;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Transactional
    public void accept(String type, JsonNode payload) {
        // 1. 按事件类型找到对应订单和通知幂等键。
        List<Map<String, Object>> orders;
        String key;
        if ("ORDER_CREATED".equals(type)) {
            long id = payload.path("reservationId").asLong();
            key = "reservation:" + id;
            orders = db.findOrderByReservation(id);
        } else {
            String no = payload.path("paymentNo").asText();
            key = "payment:" + no;
            orders = db.findPaidOrderByPayment(no);
        }
        if (orders.isEmpty()) {
            return;
        }

        // 2. 持久化邮件任务，再由定时任务异步发送。
        Map<String, Object> order = orders.getFirst();
        LocalDateTime now = LocalDateTime.now(clock);
        db.insertEmailTask(order.get("id"), order.get("order_no"), order.get("user_id"),
                "ORDER_CREATED".equals(type) ? "EMAIL_ORDER" : "EMAIL_PAYMENT", key, payload.toString(), now, now, now);
    }

    @Scheduled(fixedDelay = 1000)
    public void processDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Map<String, Object>> tasks = db.findDueEmailTasks(now, now);

        // 1. 按租约领取到期任务，避免多实例重复发送。
        for (Map<String, Object> task : tasks) {
            now = LocalDateTime.now(clock);
            long id = ((Number) task.get("id")).longValue();
            String token = UUID.randomUUID().toString();
            if (db.claimEmailTask(token, now.plusMinutes(5), now, now, id, now, now) != 1) {
                db.exhaustEmailTask(id, now);
                continue;
            }

            try {
                // 2. 重新读取订单和邮箱，支付通知还要确认订单状态。
                Map<String, Object> order = db.findOrderWithEmail(task.get("order_id"));
                String address = Objects.toString(order.get("email"), "");
                if (address.isBlank()) {
                    throw new IllegalStateException("User has no email");
                }
                boolean payment = "EMAIL_PAYMENT".equals(task.get("task_type"));
                if (payment && !Set.of(1, 3).contains(((Number) order.get("order_status")).intValue())) {
                    throw new IllegalStateException("Order is no longer paid");
                }
                boolean sent = email.sendSimpleEmail(address, payment ? "支付成功通知" : "订单创建成功通知",
                        "订单号：" + order.get("order_no") + "\n数量：" + order.get("quantity") + "\n金额："
                                + order.get("total_amount") + (payment ? "\n付款已确认。" : "\n请在订单截止时间前完成付款。"));
                if (!sent) {
                    throw new IllegalStateException("Email provider returned failure");
                }

                // 3. 发送成功后凭租约令牌结束任务。
                db.completeEmailTask(LocalDateTime.now(clock), id, token);
            } catch (Exception e) {
                // 失败记录重试时间，达到上限后等待人工处理。
                int attempts = ((Number) task.get("retry_count")).intValue() + 1;
                String error = Objects.toString(e.getMessage(), "Email failure");
                db.failEmailTask(attempts >= 3 ? 3 : 0, LocalDateTime.now(clock).plusSeconds(60),
                        error.substring(0, Math.min(500, error.length())), LocalDateTime.now(clock), id, token);
                if (attempts >= 3) {
                    log.error("EMAIL_MANUAL_REQUIRED task={}", id, e);
                }
            }
        }
    }
}
