package org.example.ticketmanagement.serviceimpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.RedisReservation;
import org.example.ticketmanagement.exception.StockCacheNotReadyException;
import org.example.ticketmanagement.mapper.SeckillAdmissionMapper;
import org.example.ticketmanagement.mapper.TradeStockMapper;
import org.example.ticketmanagement.pojo.StockEpoch;
import org.example.ticketmanagement.pojo.StockReservation;
import org.example.ticketmanagement.service.StockService;
import org.example.ticketmanagement.trade.messaging.StockProjection;
import org.example.ticketmanagement.util.LuaScriptLoader;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.example.ticketmanagement.util.ReservationValues.number;

/** Redis配额和预扣协议。所有库存脚本只从LuaScriptLoader取得。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    private final TradeStockMapper stockMapper;
    private final StockProjection projection;
    private final SeckillAdmissionMapper admissions;
    private final StringRedisTemplate redis;
    private final LuaScriptLoader scripts;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Value("${trade.enabled:false}")
    private boolean enabled;

    public static String metaKey(long tierId) {
        return "seckill:{" + tierId + "}:meta";
    }

    public static List<String> keys(long tierId, long epoch) {
        String base = "seckill:{" + tierId + "}:";
        return List.of(base + "meta", base + "quota:" + epoch, base + "users:" + epoch,
                base + "requests:" + epoch, base + "tokens:" + epoch, base + "pending:" + epoch);
    }

    /** 缓存缺失时走受控恢复，正常请求不读取数据库库存。 */
    public long prepare(long tierId) {
        requireEnabled();
        Map<Object, Object> meta = redis.opsForHash().entries(metaKey(tierId));
        if ("1".equals(meta.get("ready")) && meta.get("epoch") != null) {
            return Long.parseLong(meta.get("epoch").toString());
        }
        return rebuild(tierId);
    }

    public RedisReservation reserve(long userId, long tierId, int quantity, String requestKey) {
        long epoch = prepare(tierId);
        long created = clock.millis();
        String token = UUID.randomUUID().toString().replace("-", "");

        // 同键重试使用原token和原期限，不能重新计时。
        List<?> result = redis.execute(scripts.<List>getScript("reserveStock.lua"), keys(tierId, epoch),
                Long.toString(epoch), Long.toString(userId), requestKey, Integer.toString(quantity), token,
                Long.toString(created), Long.toString(created + 30 * 60_000L));
        if (result == null || result.size() != 4) {
            throw new StockCacheNotReadyException();
        }
        String state = result.getFirst().toString();
        if (!Set.of("OK", "EXISTING").contains(state)) {
            if ("NOT_READY".equals(state)) {
                redis.opsForHash().put(metaKey(tierId), "ready", "0");
            }
            String message = switch (state) {
                case "DUPLICATE" -> "已持有该票档的有效申请";
                case "SOLD_OUT" -> "库存不足";
                case "MISMATCH" -> "requestKey已用于不同购买参数";
                case "INVALID" -> "抢票参数或缓存格式异常";
                default -> "票档库存正在恢复，请稍后重试";
            };
            throw new IllegalStateException(message);
        }
        return new RedisReservation(tierId, userId, requestKey, quantity, result.get(1).toString(), epoch,
                Long.parseLong(result.get(2).toString()), Long.parseLong(result.get(3).toString()));
    }

    public void confirm(RedisReservation reservation) {
        executeStateScript("confirmReservation.lua", reservation);
    }

    public void release(RedisReservation reservation) {
        executeStateScript("releaseReservation.lua", reservation);
    }

    private void executeStateScript(String name, RedisReservation reservation) {
        Long result = redis.execute(scripts.<Long>getScript(name), keys(reservation.tierId(), reservation.epoch()),
                Long.toString(reservation.epoch()), reservation.token());
        if (result == null || result < 0) {
            // 缺失token不能凭数量直接回补，关闭入口后由数据库快照恢复。
            redis.opsForHash().put(metaKey(reservation.tierId()), "ready", "0");
            throw new StockCacheNotReadyException();
        }
    }

    public RedisReservation from(StockReservation row) {
        return new RedisReservation(row.getTierId(), row.getUserId(), row.getRequestKey(), row.getQuantity(),
                row.getRedisToken(), row.getStockEpoch(), millis(row.getCreateTime()), millis(row.getExpiresAt()));
    }

    /** 每批最多读取100个孤立预扣，不扫描整个Redis键空间。 */
    public List<RedisReservation> provisional(long tierId, long beforeMillis, int limit) {
        Object value = redis.opsForHash().get(metaKey(tierId), "epoch");
        if (value == null) {
            return List.of();
        }
        long epoch = Long.parseLong(value.toString());
        List<String> names = keys(tierId, epoch);
        Set<String> tokens = redis.opsForZSet().rangeByScore(names.get(5), 0, beforeMillis, 0, Math.min(100, limit));
        List<RedisReservation> result = new ArrayList<>();
        if (tokens == null) {
            return result;
        }
        for (String token : tokens) {
            Object raw = redis.opsForHash().get(names.get(4), token);
            if (raw == null) {
                throw new StockCacheNotReadyException();
            }
            try {
                JsonNode row = json.readTree(raw.toString());
                result.add(new RedisReservation(tierId, Long.parseLong(row.path("userId").asText()),
                        row.path("requestKey").asText(), row.path("quantity").asInt(), token, epoch,
                        row.path("createdAt").asLong(), row.path("expiresAt").asLong()));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("预扣记录无法解析，需恢复票档缓存", exception);
            }
        }
        return result;
    }

    /** 必须先于申请、订单及库存锁取得共享代次屏障。 */
    public StockEpoch requireReady(long tierId, Long expected) {
        StockEpoch epoch = admissions.shareEpoch(tierId);
        if (epoch == null || !"READY".equals(epoch.getCacheStatus())
                || (expected != null && !expected.equals(epoch.getEpoch()))) {
            throw new StockCacheNotReadyException();
        }
        return epoch;
    }

    public long rebuild(long tierId) {
        requireEnabled();
        String owner = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(clock);

        // 暂停等待旧事务完成；Redis网络写入不占数据库事务。
        Long epoch = transactions.execute(status -> {
            admissions.initEpoch(tierId, now);
            StockEpoch current = admissions.lockEpoch(tierId);
            if (current.getEpoch() > 0 && "PAUSED".equals(current.getCacheStatus())
                    && current.getUpdatedAt().isAfter(now.minusSeconds(60))) {
                throw new StockCacheNotReadyException();
            }
            if (admissions.sale(tierId) == null) {
                throw new IllegalArgumentException("票档不存在");
            }
            admissions.pauseEpoch(tierId, owner, now);
            return current.getEpoch() + 1;
        });
        if (epoch == null) {
            throw new StockCacheNotReadyException();
        }
        redis.opsForHash().put(metaKey(tierId), "ready", "0");

        // 暂停期间业务变更被共享屏障拒绝，快照计入PENDING占用。
        int quota = transactions.execute(status -> {
            StockEpoch current = admissions.shareEpoch(tierId);
            if (!owner.equals(current.getBuildToken()) || current.getEpoch().longValue() != epoch) {
                throw new StockCacheNotReadyException();
            }
            admissions.rebase(tierId, epoch);
            return stockMapper.selectAvailableStock(tierId) - admissions.pendingQuantity(tierId);
        });
        if (quota < 0) {
            throw new IllegalStateException("可靠申请超过数据库可售库存，票档保持暂停");
        }
        int expected = admissions.activeCount(tierId) + admissions.historicalCount(tierId);
        List<String> names = keys(tierId, epoch);
        writeBatch(names, epoch, quota, expected, "init", List.of(), true);
        long after = 0;
        while (true) {
            List<StockReservation> batch = admissions.activeBatch(tierId, after);
            if (batch.isEmpty()) {
                break;
            }
            List<Map<String, Object>> records = batch.stream().map(this::cacheRecord).toList();
            writeBatch(names, epoch, quota, expected, "after:" + after, records, false);
            after = batch.getLast().getId();
        }
        after = 0;
        while (true) {
            List<StockReservation> batch = admissions.historicalBatch(tierId, after);
            if (batch.isEmpty()) {
                break;
            }
            for (StockReservation historical : batch) {
                historical.setRedisToken("legacy-order:" + historical.getId());
                historical.setRequestKey("historical-order:" + historical.getId());
            }
            writeBatch(names, epoch, quota, expected, "historical:" + after,
                    batch.stream().map(this::cacheRecord).toList(), false);
            after = batch.getLast().getId();
        }

        // 先确认数据库代次，再开放Redis。失败时维持关闭，下次重建新代次。
        Integer changed = transactions.execute(status -> {
            admissions.lockEpoch(tierId);
            return admissions.readyEpoch(tierId, epoch, owner, LocalDateTime.now(clock));
        });
        if (changed == null || changed != 1 || !Long.valueOf(1).equals(redis.execute(
                scripts.<Long>getScript("activateStockEpoch.lua"), names.subList(0, 2), epoch.toString()))) {
            throw new StockCacheNotReadyException();
        }
        refresh(tierId);
        try {
            retireOldEpochs(tierId);
        } catch (RuntimeException unavailable) {
            log.warn("旧代次数据待下轮退役 tierId={}", tierId, unavailable);
        }
        return epoch;
    }

    /** 新代次已开放后有界退役，独立游标使进程退出后仍能继续清理。 */
    public void retireOldEpochs(long tierId) {
        requireEnabled();
        Object active = redis.opsForHash().get(metaKey(tierId), "epoch");
        if (active == null) {
            return;
        }
        long epoch = Long.parseLong(active.toString());
        String cursorKey = "seckill:{" + tierId + "}:retired-through";
        String previous = redis.opsForValue().get(cursorKey);
        long before = previous == null ? 0 : Long.parseLong(previous);
        long through = Math.min(epoch - 1, before + 100);
        if (through <= before) {
            return;
        }

        List<String> names = new ArrayList<>(List.of(metaKey(tierId), cursorKey));
        for (long old = before + 1; old <= through; old++) {
            names.addAll(keys(tierId, old).subList(1, 6));
        }
        Long result = redis.execute(scripts.<Long>getScript("retireStockEpochs.lua"), names,
                Long.toString(epoch), Long.toString(before), Long.toString(through));
        if (result == null || result < 0) {
            throw new IllegalStateException("退役代次参数或缓存状态异常");
        }
    }

    private Map<String, Object> cacheRecord(StockReservation row) {
        return Map.of("token", row.getRedisToken(), "userId", row.getUserId().toString(),
                "requestKey", row.getRequestKey(), "quantity", row.getQuantity(),
                "createdAt", millis(row.getCreateTime()), "expiresAt", millis(row.getExpiresAt()));
    }

    private void writeBatch(List<String> names, long epoch, int quota, int expected, String batch,
            List<Map<String, Object>> records, boolean initialize) {
        try {
            Long result = redis.execute(scripts.<Long>getScript("rebuildStockBatch.lua"), names,
                    Long.toString(epoch), Integer.toString(quota), Integer.toString(expected), batch,
                    json.writeValueAsString(records), initialize ? "1" : "0");
            if (result == null || result < 0) {
                throw new StockCacheNotReadyException();
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("库存快照序列化失败", exception);
        }
    }

    private long millis(LocalDateTime time) {
        return time.atZone(clock.getZone()).toInstant().toEpochMilli();
    }

    /** 只有核对和脚本发现异常时关闭配额，普通展示投影不会触发。 */
    public void pauseCache(long tierId) {
        redis.opsForHash().put(metaKey(tierId), "ready", "0");
    }

    public boolean hasProvisional(long tierId) {
        Object epoch = redis.opsForHash().get(metaKey(tierId), "epoch");
        return epoch != null && Long.valueOf(0).compareTo(
                redis.opsForZSet().size(keys(tierId, Long.parseLong(epoch.toString())).get(5))) < 0;
    }

    @Override
    public void initStock(Long tierId, Integer ignored) {
        prepare(tierId);
    }

    public void refresh(Long tierId) {
        Map<String, Object> row = stockMapper.selectStock(tierId);
        if (row != null) {
            projection.apply(tierId, number(row, "stock_version"), (int) number(row, "available_stock"));
        }
    }

    @Override
    public Integer getStock(Long tierId) {
        long epoch = prepare(tierId);
        String value = redis.opsForValue().get(keys(tierId, epoch).get(1));
        if (value == null) {
            pauseCache(tierId);
            throw new StockCacheNotReadyException();
        }
        return Integer.valueOf(value);
    }

    @Override
    public boolean hasEnoughStock(Long tierId, Integer quantity) {
        Integer stock = getStock(tierId);
        return stock != null && quantity != null && quantity > 0 && stock >= quantity;
    }

    @Override
    public boolean hasUserSeckilled(Long userId, Long tierId) {
        long epoch = prepare(tierId);
        return redis.opsForHash().hasKey(keys(tierId, epoch).get(2), userId.toString());
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("交易功能尚未启用");
        }
    }
}
