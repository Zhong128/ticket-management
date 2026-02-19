// org/example/ticketmanagement/service/impl/StockServiceImpl.java
package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.SeckillConstant;
import org.example.ticketmanagement.service.StockService;
import org.example.ticketmanagement.util.LuaScriptLoader;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final LuaScriptLoader luaScriptLoader;

    /**
     * 获取库存Key
     */
    private String getStockKey(Long tierId) {
        return SeckillConstant.STOCK_KEY_PREFIX + tierId;
    }

    /**
     * 获取用户抢票记录Key
     */
    private String getUserSeckillKey(Long userId, Long tierId) {
        return SeckillConstant.USER_KEY_PREFIX + userId + ":" + tierId;
    }

    @Override
    public void initStock(Long tierId, Integer totalStock) {
        String stockKey = getStockKey(tierId);
        // 如果库存不存在，则初始化
        if (!redisTemplate.hasKey(stockKey)) {
            redisTemplate.opsForValue().set(stockKey, totalStock);
            log.info("初始化票档库存: tierId={}, stock={}", tierId, totalStock);
        }
    }

    @Override
    public Long decrStock(Long tierId, Integer quantity) {
        String stockKey = getStockKey(tierId);

        try {
            // 使用Lua脚本原子扣减库存
            Long result = (Long) redisTemplate.execute(
                    luaScriptLoader.getScript("decrStock.lua"),
                    Arrays.asList(stockKey),
                    quantity
            );

            if (result != null && result >= 0) {
                log.debug("扣减库存成功: tierId={}, quantity={}, remaining={}", tierId, quantity, result);
            } else {
                log.warn("库存不足: tierId={}, quantity={}", tierId, quantity);
            }

            return result;
        } catch (Exception e) {
            log.error("扣减库存失败: tierId={}, quantity={}", tierId, quantity, e);
            return -1L;
        }
    }

    @Override
    public Long restoreStock(Long tierId, Integer quantity) {
        String stockKey = getStockKey(tierId);

        try {
            Long result = redisTemplate.opsForValue().increment(stockKey, quantity);
            log.debug("恢复库存成功: tierId={}, quantity={}, total={}", tierId, quantity, result);
            return result;
        } catch (Exception e) {
            log.error("恢复库存失败: tierId={}, quantity={}", tierId, quantity, e);
            return -1L;
        }
    }

    @Override
    public Integer getStock(Long tierId) {
        String stockKey = getStockKey(tierId);

        try {
            Object stockObj = redisTemplate.opsForValue().get(stockKey);
            if (stockObj instanceof Integer) {
                return (Integer) stockObj;
            } else if (stockObj instanceof String) {
                return Integer.parseInt((String) stockObj);
            }
            return null;
        } catch (Exception e) {
            log.error("获取库存失败: tierId={}", tierId, e);
            return null;
        }
    }

    @Override
    public boolean hasEnoughStock(Long tierId, Integer quantity) {
        Integer stock = getStock(tierId);
        return stock != null && stock >= quantity;
    }

    @Override
    public boolean hasUserSeckilled(Long userId, Long tierId) {
        String userKey = getUserSeckillKey(userId, tierId);

        try {
            Boolean exists = redisTemplate.hasKey(userKey);
            return exists;
        } catch (Exception e) {
            log.error("检查用户抢票记录失败: userId={}, tierId={}", userId, tierId, e);
            return false;
        }
    }

    @Override
    public void setUserSeckillRecord(Long userId, Long tierId, long expireSeconds) {
        String userKey = getUserSeckillKey(userId, tierId);

        try {
            redisTemplate.opsForValue().set(userKey, "1", expireSeconds, TimeUnit.SECONDS);
            log.debug("设置用户抢票记录: userId={}, tierId={}, expire={}s", userId, tierId, expireSeconds);
        } catch (Exception e) {
            log.error("设置用户抢票记录失败: userId={}, tierId={}", userId, tierId, e);
        }
    }

    @Override
    public void deleteUserSeckillRecord(Long userId, Long tierId) {
        String userKey = getUserSeckillKey(userId, tierId);

        try {
            redisTemplate.delete(userKey);
            log.debug("删除用户抢票记录: userId={}, tierId={}", userId, tierId);
        } catch (Exception e) {
            log.error("删除用户抢票记录失败: userId={}, tierId={}", userId, tierId, e);
        }
    }
}