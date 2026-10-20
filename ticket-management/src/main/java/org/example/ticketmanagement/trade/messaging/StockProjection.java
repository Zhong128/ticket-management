package org.example.ticketmanagement.trade.messaging;

import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.constant.SeckillConstant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StockProjection {

    private final StringRedisTemplate redis;
    // Store numeric versions as decimal strings. Compare length then lexical order to avoid Lua
    // double precision loss.
    private static final DefaultRedisScript<Long> APPLY = new DefaultRedisScript<>(
            "local old=redis.call('get',KEYS[2]); local v=ARGV[1]; if old and (#old>#v or (#old==#v"
                    + " and old>v)) then return 0 end; redis.call('set',KEYS[1],ARGV[2]);"
                    + " redis.call('set',KEYS[2],v); return 1",
            Long.class);

    public void apply(long tierId, long version, int stock) {
        String key = SeckillConstant.STOCK_KEY_PREFIX + tierId;
        redis.execute(APPLY, List.of(key, key + ":version"), Long.toString(version), Integer.toString(stock));
    }
}
