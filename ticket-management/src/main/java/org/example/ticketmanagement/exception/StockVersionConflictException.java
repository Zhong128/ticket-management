package org.example.ticketmanagement.exception;

/** 版本竞争属于可重试调度，不能消耗普通建单失败次数。 */
public class StockVersionConflictException extends IllegalStateException {

    public StockVersionConflictException() {
        super("库存版本冲突，请退避重试建单");
    }
}
