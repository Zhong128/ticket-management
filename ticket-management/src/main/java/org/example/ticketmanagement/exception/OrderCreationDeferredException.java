package org.example.ticketmanagement.exception;

/** 建单调度暂不可用，不属于申请本身的业务失败。 */
public class OrderCreationDeferredException extends IllegalStateException {

    public OrderCreationDeferredException(String message) {
        super(message);
    }

    public OrderCreationDeferredException(String message, Throwable cause) {
        super(message, cause);
    }
}
