package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.mapper.TradeRecoveryMapper;
import org.example.ticketmanagement.service.OrderService;
import org.example.ticketmanagement.service.ReservationService;
import org.example.ticketmanagement.exception.StockCacheNotReadyException;
import org.example.ticketmanagement.exception.StockVersionConflictException;
import org.example.ticketmanagement.exception.OrderCreationDeferredException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

import static org.example.ticketmanagement.util.ReservationValues.number;
import static org.example.ticketmanagement.util.ReservationValues.time;

/** 定时兜底不依赖Broker可用性，与消息调用同一事务状态机。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderRecoveryService {
    private final TradeRecoveryMapper mapper;
    private final ReservationService reservations;
    private final OrderService orders;
    private final Clock clock;
    private long reservationCursor;

    public synchronized void recover() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (int batch = 0; batch < 10; batch++) {
            var rows = mapper.findDueReservations(reservationCursor, now);
            if (rows.isEmpty()) {
                reservationCursor = 0;
                break;
            }
            for (Map<String, Object> row : rows) {
                long id = number(row, "id");
                reservationCursor = id;
                try {
                    if (!now.isBefore(time(row, "expires_at"))) {
                        if (row.get("order_id") == null) {
                            reservations.releaseUnordered(id, "TIMEOUT_RECOVERY");
                        } else {
                            orders.cancelOrder(number(row, "order_id"));
                        }
                    } else {
                        try {
                            reservations.createOrder(id);
                        } catch (StockCacheNotReadyException | StockVersionConflictException
                                | OrderCreationDeferredException paused) {
                            throw paused;
                        } catch (RuntimeException failed) {
                            reservations.creationFailed(id, failed.getMessage());
                        }
                    }
                } catch (RuntimeException unavailable) {
                    log.warn("申请恢复待下轮处理 reservationId={}", id, unavailable);
                }
            }
        }

        // 也覆盖没有申请记录的历史订单，不要求先创建支付流水。
        for (Long id : mapper.findOrdersRequiringCancellation(now)) {
            try {
                orders.cancelOrder(id);
            } catch (RuntimeException unavailable) {
                log.warn("关单恢复待下轮处理 orderId={}", id, unavailable);
            }
        }
        mapper.completeResolvedCloseTasks(LocalDateTime.now(clock));
    }
}
