package org.example.ticketmanagement.exception;

/** 缓存暂停是可恢复状态，不能当作一次建单业务失败计数。 */
public class StockCacheNotReadyException extends IllegalStateException {
    public StockCacheNotReadyException() {
        super("票档库存正在恢复，请使用原请求键稍后重试");
    }
}
