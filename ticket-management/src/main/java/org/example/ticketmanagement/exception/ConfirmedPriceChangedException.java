package org.example.ticketmanagement.exception;

/** 确认单价与受理事务即将固化的单价不符；外层须先封存拒绝再返回用户。 */
public class ConfirmedPriceChangedException extends IllegalArgumentException {

    public ConfirmedPriceChangedException() {
        super("票价已变化，请重新确认");
    }
}
