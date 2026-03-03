package org.example.ticketmanagement.util;

import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * ip2region IP定位工具
 * 离线IP地址定位库，99.9%准确率，微秒级查询
 */
@Slf4j
@Component
public class Ip2RegionUtil {

    private Searcher searcher;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Redis缓存Key前缀
    private static final String IP_CACHE_KEY_PREFIX = "ip:location:";
    // 缓存过期时间（小时）
    private static final long CACHE_EXPIRE_HOURS = 24;

    @PostConstruct
    public void init() {
        try {
            // 完全基于文件的查询
            String dbPath = new ClassPathResource("ipdb/ip2region.xdb").getFile().getPath();
            searcher = Searcher.newWithFileOnly(dbPath);
            log.info("ip2region初始化成功");
        } catch (Exception e) {
            log.error("ip2region初始化失败", e);
        }
    }

    /**
     * 根据IP地址查询地理位置（带Redis缓存）
     * @param ip IP地址
     * @return 地理位置信息
     */
    public String search(String ip) {
        if (searcher == null) {
            log.warn("ip2region未初始化");
            return null;
        }

        // 1. 先从Redis缓存查询
        String cacheKey = IP_CACHE_KEY_PREFIX + ip;
        String cachedRegion = (String) redisTemplate.opsForValue().get(cacheKey);
        if (cachedRegion != null) {
            log.debug("IP缓存命中: {} -> {}", ip, cachedRegion);
            return cachedRegion;
        }

        // 2. 缓存未命中，执行查询
        try {
            long startTime = System.nanoTime();
            String region = searcher.search(ip);
            long cost = (System.nanoTime() - startTime) / 1000; // 微秒

            log.debug("IP查询: {} -> {}，耗时: {}μs", ip, region, cost);

            // 3. 存入Redis缓存（24小时过期）
            if (region != null) {
                redisTemplate.opsForValue().set(cacheKey, region, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            }

            return region;
        } catch (Exception e) {
            log.error("IP查询失败: {}", ip, e);
            return null;
        }
    }

    /**
     * 根据IP获取城市名称
     * @param ip IP地址
     * @return 城市名称（如：北京、上海）
     */
    public String getCityName(String ip) {
        String region = search(ip);
        if (region == null) {
            return null;
        }

        // 解析结果格式：国家|区域|省份|城市|ISP
        // 例如：中国|0|上海|上海市|电信
        String[] parts = region.split("\\|");
        if (parts.length >= 4) {
            String city = parts[3];
            // 去除"市"后缀
            if (city.endsWith("市")) {
                city = city.substring(0, city.length() - 1);
            }
            return city;
        }

        return null;
    }

    /**
     * 根据IP获取省份名称
     * @param ip IP地址
     * @return 省份名称
     */
    public String getProvinceName(String ip) {
        String region = search(ip);
        if (region == null) {
            return null;
        }

        String[] parts = region.split("\\|");
        if (parts.length >= 3) {
            return parts[2];
        }

        return null;
    }

    /**
     * 根据IP获取运营商
     */
    public String getIsp(String ip) {
        String region = search(ip);
        if (region == null) {
            return null;
        }

        String[] parts = region.split("\\|");
        if (parts.length >= 5) {
            return parts[4];
        }

        return null;
    }

    /**
     * 清除指定IP的缓存
     */
    public void clearCache(String ip) {
        String cacheKey = IP_CACHE_KEY_PREFIX + ip;
        redisTemplate.delete(cacheKey);
        log.debug("已清除IP缓存: {}", ip);
    }

    @PreDestroy
    public void destroy() {
        if (searcher != null) {
            try {
                searcher.close();
                log.info("ip2region资源已释放");
            } catch (IOException e) {
                log.error("ip2region资源释放失败", e);
            }
        }
    }
}