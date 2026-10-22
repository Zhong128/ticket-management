package org.example.ticketmanagement.service;

import java.util.Map;

public interface ReservationService {

    /** 支付先取得代次屏障，再锁定订单。 */
    void lockOrderEpoch(long orderId);

    /** 通过抢票入口执行预扣与可靠受理。 */
    Map<String, Object> reserve(long userId, long tierId, int quantity, String requestKey);

    /** 查询当前用户的申请结果。 */
    Map<String, Object> request(long reservationId, long userId);

    /** 幂等推进申请到数据库扣库存和建单。 */
    Long createOrder(long reservationId);

    /** 标记取消中，保留库存等待渠道确认。 */
    boolean beginCancel(long orderId, Long userId);

    /** 渠道确认后一次性释放订单占用。 */
    boolean finishCancel(long orderId);

    /** 核验订单与流水后统一入账。 */
    boolean applyPaid(long orderId, long paymentId);

    /** 仅释放尚未创建订单的申请。 */
    void releaseUnordered(long reservationId, String reason);

    /** 持久记录建单失败并按上限处理。 */
    void creationFailed(long reservationId, String error);
}
