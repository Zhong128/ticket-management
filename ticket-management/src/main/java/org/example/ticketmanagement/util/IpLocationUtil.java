package org.example.ticketmanagement.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.pojo.City;
import org.example.ticketmanagement.service.CityService;
import org.example.ticketmanagement.vo.CityVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class IpLocationUtil {

    @Autowired
    private Ip2RegionUtil ip2RegionUtil;

    // 默认城市（北京）(便于测试先把北京的id改为2，对应数据库)
    private static final Long DEFAULT_CITY_ID = 1L;
    private static final String DEFAULT_CITY_NAME = "北京";

    // 首页默认显示的四大分类ID
    private static final Long[] HOME_CATEGORY_IDS = {1L, 2L, 3L, 4L};

    // 城市名称到ID的映射（从数据库加载或缓存）
    private final Map<String, Long> cityNameToIdMap = new HashMap<>();

    @Autowired
    private CityService cityService; // 假设你有城市服务

    public IpLocationUtil() {
        // 初始化常用城市映射（可以后续从数据库加载）
        cityNameToIdMap.put("北京", 1L);
        cityNameToIdMap.put("北京市", 1L);
        cityNameToIdMap.put("上海", 2L);
        cityNameToIdMap.put("上海市", 2L);
        cityNameToIdMap.put("广州", 3L);
        cityNameToIdMap.put("广州市", 3L);
        cityNameToIdMap.put("深圳", 4L);
        cityNameToIdMap.put("深圳市", 4L);
        cityNameToIdMap.put("杭州", 5L);
        cityNameToIdMap.put("杭州市", 5L);
        cityNameToIdMap.put("南京", 6L);
        cityNameToIdMap.put("南京市", 6L);
        cityNameToIdMap.put("成都", 7L);
        cityNameToIdMap.put("成都市", 7L);
        cityNameToIdMap.put("重庆", 8L);
        cityNameToIdMap.put("重庆市", 8L);
        cityNameToIdMap.put("武汉", 9L);
        cityNameToIdMap.put("武汉市", 9L);
        cityNameToIdMap.put("西安", 10L);
        cityNameToIdMap.put("西安市", 10L);

        // 添加运营商映射（针对"移动"这类情况）
        cityNameToIdMap.put("移动", 1L); // 映射到默认城市
        cityNameToIdMap.put("联通", 1L);
        cityNameToIdMap.put("电信", 1L);
    }

    /**
     * 获取客户端真实IP（增强版）
     */
    public String getClientIp(HttpServletRequest request) {
        String ip = null;

        // 常见的代理服务器转发IP的请求头
        String[] ipHeaders = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_VIA",
                "REMOTE_ADDR"
        };

        // 按优先级检查各个请求头
        for (String header : ipHeaders) {
            ip = request.getHeader(header);
            if (isValidIp(ip)) {
                break;
            }
        }

        // 如果所有请求头都没有有效的IP，使用remoteAddr
        if (!isValidIp(ip)) {
            ip = request.getRemoteAddr();
        }

        // 处理多个IP的情况（如X-Forwarded-For: client, proxy1, proxy2）
        if (ip != null && ip.contains(",")) {
            String[] ips = ip.split(",");
            for (String ipStr : ips) {
                ipStr = ipStr.trim();
                if (isValidIp(ipStr) && !isInternalIp(ipStr)) {
                    ip = ipStr;
                    break;
                }
            }
        }

        // 处理本地IP
        if ("0:0:0:0:0:0:0:1".equals(ip) || "127.0.0.1".equals(ip)) {
            // 测试环境下，可以返回一个模拟的公网IP用于测试
            if (isDevelopmentEnvironment()) {
                ip = "223.104.3.1"; // 模拟北京IP
            }
        }

        log.debug("获取到客户端IP: {}", ip);
        return ip;
    }

    /**
     * 根据IP获取城市ID（使用ip2region）
     */
    public Long getCityIdByIp(String ip) {
        log.info("根据IP定位城市: {}", ip);

        // 1. 处理本地和无效IP
        if (!isValidIp(ip)) {
            log.warn("无效IP地址，使用默认城市: {}", DEFAULT_CITY_NAME);
            return DEFAULT_CITY_ID;
        }

        // 2. 检查是否是内网IP
        if (isInternalIp(ip)) {
            log.warn("内网IP地址: {}，使用默认城市: {}", ip, DEFAULT_CITY_NAME);
            return DEFAULT_CITY_ID;
        }

        // 3. 使用ip2region获取城市名称
        try {
            String cityName = ip2RegionUtil.getCityName(ip);

            if (StringUtils.hasText(cityName)) {
                log.info("IP {} 定位到城市: {}", ip, cityName);

                // 4. 根据城市名称获取城市ID
                Long cityId = getCityIdByName(cityName);
                if (cityId != null) {
                    return cityId;
                }

                // 5. 尝试从数据库查询（如果缓存中没有）
                log.info("城市 {} 不在缓存中，尝试查询数据库", cityName);
                City city = cityService.getCityByName(cityName);
                if (city != null) {
                    // 添加到缓存
                    cityNameToIdMap.put(cityName, city.getId());
                    cityNameToIdMap.put(city.getName(), city.getId());
                    return city.getId();
                }
            }
        } catch (Exception e) {
            log.error("IP定位失败: {}", ip, e);
        }

        // 6. 定位失败，返回默认城市
        log.warn("无法根据IP {} 定位城市，使用默认: {}", ip, DEFAULT_CITY_NAME);
        return DEFAULT_CITY_ID;
    }

    /**
     * 根据城市名称获取城市ID
     */
    private Long getCityIdByName(String cityName) {
        // 直接匹配
        if (cityNameToIdMap.containsKey(cityName)) {
            return cityNameToIdMap.get(cityName);
        }

        // 模糊匹配（如"上海市"匹配"上海"）
        for (Map.Entry<String, Long> entry : cityNameToIdMap.entrySet()) {
            if (entry.getKey().contains(cityName) || cityName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 判断IP地址是否有效
     */
    private boolean isValidIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }

        if ("unknown".equalsIgnoreCase(ip)) {
            return false;
        }

        // 简单的IP地址格式验证
        return ip.matches("^(\\d{1,3}\\.){3}\\d{1,3}$") ||
                ip.matches("^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");
    }

    /**
     * 判断是否是内网IP
     */
    private boolean isInternalIp(String ip) {
        if (ip == null) return false;

        // IPv4内网地址段
        if (ip.matches("^(\\d{1,3}\\.){3}\\d{1,3}$")) {
            String[] parts = ip.split("\\.");
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);

            // 10.0.0.0/8
            if (first == 10) return true;
            // 172.16.0.0/12
            if (first == 172 && second >= 16 && second <= 31) return true;
            // 192.168.0.0/16
            if (first == 192 && second == 168) return true;
            // 127.0.0.0/8
            if (first == 127) return true;
            // 169.254.0.0/16
            if (first == 169 && second == 254) return true;
        }

        // IPv6内网地址
        return ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1");
    }

    /**
     * 判断是否是开发环境
     */
    private boolean isDevelopmentEnvironment() {
        String env = System.getProperty("spring.profiles.active");
        return "dev".equals(env) || env == null;
    }

    /**
     * 获取默认城市ID
     */
    public Long getDefaultCityId() {
        return DEFAULT_CITY_ID;
    }

    /**
     * 获取默认城市名称
     */
    public String getDefaultCityName() {
        return DEFAULT_CITY_NAME;
    }

    /**
     * 获取首页默认的分类ID列表
     */
    public Long[] getHomeCategoryIds() {
        return HOME_CATEGORY_IDS.clone();
    }

    /**
     * 预加载城市数据到缓存
     */
    public void preloadCityData() {
        try {
            // 从数据库加载所有城市到缓存
            List<CityVO> cities = cityService.getAllCities();
            for (CityVO city : cities) {
                cityNameToIdMap.put(city.getName(), city.getId());
            }
            log.info("预加载了 {} 个城市到缓存", cities.size());
        } catch (Exception e) {
            log.error("预加载城市数据失败", e);
        }
    }
}