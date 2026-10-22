package org.example.ticketmanagement.trade.messaging;

import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.constant.SeckillConstant;
import org.example.ticketmanagement.util.LuaScriptLoader;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StockProjection {

    private final StringRedisTemplate redis;
    private final LuaScriptLoader scripts;

    public void apply(long tierId, long version, int stock) {
        String key = SeckillConstant.STOCK_KEY_PREFIX + tierId;
        // 这里只更新展示投影，不能覆盖抢票配额；脚本由统一加载器管理。
        redis.execute(scripts.<Long>getScript("applyStockProjection.lua"), List.of(key, key + ":version"),
                Long.toString(version), Integer.toString(stock));
    }
}
