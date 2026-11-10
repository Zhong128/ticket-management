package org.example.ticketmanagement.exception;

/** 明确未进入受理或库存脚本在写入前拒绝；不用于预扣连接超时或提交结果未知。 */
public class ReservationRejectedException extends IllegalStateException {

    public ReservationRejectedException(String message) {
        super(message);
    }
}
