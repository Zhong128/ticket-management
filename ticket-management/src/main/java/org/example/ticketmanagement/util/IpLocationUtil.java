package org.example.ticketmanagement.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.pojo.City;
import org.example.ticketmanagement.service.CityService;
import org.example.ticketmanagement.vo.CityVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class IpLocationUtil {

    @Autowired
    private Ip2RegionUtil ip2RegionUtil;

    @Autowired
    private CityService cityService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 默认城市（北京）
    private static final Long DEFAULT_CITY_ID = 1L;
    private static final String DEFAULT_CITY_NAME = "北京";

    // 首页默认显示的四大分类ID
    private static final Long[] HOME_CATEGORY_IDS = {1L, 2L, 3L, 4L};

    // Redis缓存Key前缀
    private static final String CITY_NAME_TO_ID_KEY = "city:name:id:";
    private static final String CITY_ID_KEY = "city:id:";
    private static final String IP_CITY_KEY_PREFIX = "ip:city:";

    // 缓存过期时间
    private static final long CITY_CACHE_EXPIRE_HOURS = 24;
    private static final long IP_CITY_CACHE_EXPIRE_HOURS = 6;

    // 开发环境模拟配置
    @Value("${dev.mock.ip.enabled:false}")
    private boolean mockIpEnabled;

    @Value("${dev.mock.ip:223.104.3.1}")
    private String mockIp;

    @Value("${dev.mock.city:北京}")
    private String mockCity;

    @PostConstruct
    public void init() {
        // 预热城市数据到Redis
        preloadCityData();
    }

    /**
     * 预加载所有城市到Redis缓存
     */
    public void preloadCityData() {
        try {
            List<CityVO> cities = cityService.getAllCities();
            if (cities == null || cities.isEmpty()) {
                log.warn("没有城市数据可预加载");
                return;
            }

            int successCount = 0;
            for (CityVO city : cities) {
                try {
                    // 缓存城市ID信息
                    String cityIdKey = CITY_ID_KEY + city.getId();
                    redisTemplate.opsForValue().set(cityIdKey, city, CITY_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

                    // 缓存城市名称到ID的映射
                    String nameKey = CITY_NAME_TO_ID_KEY + city.getName();
                    redisTemplate.opsForValue().set(nameKey, city.getId(), CITY_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

                    // 缓存带"市"的名称
                    if (!city.getName().endsWith("市")) {
                        String nameWithCityKey = CITY_NAME_TO_ID_KEY + city.getName() + "市";
                        redisTemplate.opsForValue().set(nameWithCityKey, city.getId(), CITY_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                    }
                    successCount++;
                } catch (Exception e) {
                    log.error("缓存城市失败: {}", city.getName(), e);
                }
            }
            log.info("预加载了 {}/{} 个城市到Redis缓存", successCount, cities.size());
        } catch (Exception e) {
            log.error("预加载城市数据失败", e);
        }
    }

    /**
     * 获取客户端真实IP
     */
    public String getClientIp(HttpServletRequest request) {
        // 开发环境模拟
        if (mockIpEnabled) {
            log.info("开发环境模拟IP: {}", mockIp);
            return mockIp;
        }

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
            // 测试环境下，返回一个模拟的公网IP
            ip = "223.104.3.1"; // 模拟北京IP
        }

        log.debug("获取到客户端IP: {}", ip);
        return ip;
    }

    /**
     * 根据IP获取城市ID（使用Redis缓存）
     */
    public Long getCityIdByIp(String ip) {
        log.info("根据IP定位城市: {}", ip);

        // 1. 开发环境模拟
        if (mockIpEnabled && mockIp.equals(ip)) {
            log.info("开发环境模拟城市: {}", mockCity);
            return getCityIdByName(mockCity);
        }

        // 2. 处理无效IP
        if (!isValidIp(ip)) {
            log.warn("无效IP地址，使用默认城市: {}", DEFAULT_CITY_NAME);
            return DEFAULT_CITY_ID;
        }

        // 3. 检查是否是内网IP
        if (isInternalIp(ip)) {
            log.warn("内网IP地址: {}，使用默认城市: {}", ip, DEFAULT_CITY_NAME);
            return DEFAULT_CITY_ID;
        }

        // 4. 先从Redis缓存查询IP对应的城市ID
        String ipCacheKey = IP_CITY_KEY_PREFIX + ip;
        Long cachedCityId = (Long) redisTemplate.opsForValue().get(ipCacheKey);
        if (cachedCityId != null) {
            log.debug("IP城市缓存命中: {} -> 城市ID: {}", ip, cachedCityId);
            return cachedCityId;
        }

        // 5. 使用ip2region获取城市名称
        try {
            String cityName = ip2RegionUtil.getCityName(ip);

            if (StringUtils.hasText(cityName)) {
                log.info("IP {} 定位到城市: {}", ip, cityName);

                // 6. 规范化城市名称（处理运营商等情况）
                cityName = normalizeCityName(cityName);

                // 7. 根据城市名称获取城市ID
                Long cityId = getCityIdByName(cityName);

                if (cityId != null) {
                    // 8. 存入Redis缓存（6小时过期）
                    redisTemplate.opsForValue().set(ipCacheKey, cityId, IP_CITY_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                    return cityId;
                }

                // 9. 如果精确匹配失败，尝试模糊匹配（省级匹配省会）
                cityId = fuzzyMatchCity(cityName);
                if (cityId != null) {
                    redisTemplate.opsForValue().set(ipCacheKey, cityId, IP_CITY_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                    return cityId;
                }
            }
        } catch (Exception e) {
            log.error("IP定位失败: {}", ip, e);
        }

        // 10. 定位失败，返回默认城市
        log.warn("无法根据IP {} 定位城市，使用默认: {}", ip, DEFAULT_CITY_NAME);
        return DEFAULT_CITY_ID;
    }

    /**
     * 根据城市名称获取城市ID（从Redis缓存查询，后备数据库）
     */
    private Long getCityIdByName(String cityName) {
        if (!StringUtils.hasText(cityName)) {
            return null;
        }

        // 1. 从Redis缓存查询
        String cacheKey = CITY_NAME_TO_ID_KEY + cityName;
        Long cityId = (Long) redisTemplate.opsForValue().get(cacheKey);
        if (cityId != null) {
            return cityId;
        }

        // 2. 缓存未命中，查询数据库
        try {
            City city = cityService.getCityByName(cityName);
            if (city != null) {
                // 存入缓存
                redisTemplate.opsForValue().set(cacheKey, city.getId(), CITY_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

                // 同时缓存城市信息
                String cityIdKey = CITY_ID_KEY + city.getId();
                CityVO cityVO = convertToVO(city);
                redisTemplate.opsForValue().set(cityIdKey, cityVO, CITY_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

                return city.getId();
            }
        } catch (Exception e) {
            log.error("查询数据库获取城市ID失败: {}", cityName, e);
        }

        return null;
    }

    /**
     * 根据城市ID获取城市信息（从Redis缓存查询）
     */
    public CityVO getCityById(Long cityId) {
        if (cityId == null) {
            return null;
        }

        // 1. 从Redis缓存查询
        String cacheKey = CITY_ID_KEY + cityId;
        CityVO cityVO = (CityVO) redisTemplate.opsForValue().get(cacheKey);
        if (cityVO != null) {
            return cityVO;
        }

        // 2. 缓存未命中，查询数据库
        try {
            CityVO city = cityService.getCityById(cityId);
            if (city != null) {
                redisTemplate.opsForValue().set(cacheKey, city, CITY_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            }
            return city;
        } catch (Exception e) {
            log.error("查询数据库获取城市信息失败: {}", cityId, e);
            return null;
        }
    }

    /**
     * 规范化城市名称（处理运营商、带"市"等情况）
     */
    private String normalizeCityName(String rawName) {
        // 处理 "江苏移动" -> "江苏"
        String[] operators = {"移动", "联通", "电信", "铁通", "广电"};
        for (String op : operators) {
            if (rawName.contains(op)) {
                return rawName.replace(op, "").trim();
            }
        }

        // 处理 "上海市" -> "上海"
        if (rawName.endsWith("市")) {
            return rawName.substring(0, rawName.length() - 1);
        }

        return rawName;
    }

    /**
     * 模糊匹配城市（省级名称匹配省会）
     */
    private Long fuzzyMatchCity(String cityName) {
        // 省级名称到省会城市的映射
        java.util.Map<String, String> provinceToCapital = new java.util.HashMap<>();
        provinceToCapital.put("江苏", "南京");
        provinceToCapital.put("浙江", "杭州");
        provinceToCapital.put("广东", "广州");
        provinceToCapital.put("四川", "成都");
        provinceToCapital.put("福建", "福州");
        provinceToCapital.put("湖北", "武汉");
        provinceToCapital.put("湖南", "长沙");
        provinceToCapital.put("河南", "郑州");
        provinceToCapital.put("河北", "石家庄");
        provinceToCapital.put("山东", "济南");
        provinceToCapital.put("山西", "太原");
        provinceToCapital.put("陕西", "西安");
        provinceToCapital.put("辽宁", "沈阳");
        provinceToCapital.put("吉林", "长春");
        provinceToCapital.put("黑龙江", "哈尔滨");
        provinceToCapital.put("安徽", "合肥");
        provinceToCapital.put("江西", "南昌");
        provinceToCapital.put("广西", "南宁");
        provinceToCapital.put("云南", "昆明");
        provinceToCapital.put("贵州", "贵阳");
        provinceToCapital.put("甘肃", "兰州");
        provinceToCapital.put("青海", "西宁");
        provinceToCapital.put("海南", "海口");
        provinceToCapital.put("台湾", "台北");
        provinceToCapital.put("内蒙古", "呼和浩特");
        provinceToCapital.put("新疆", "乌鲁木齐");
        provinceToCapital.put("西藏", "拉萨");
        provinceToCapital.put("宁夏", "银川");

        String capital = provinceToCapital.get(cityName);
        if (capital != null) {
            return getCityIdByName(capital);
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
     * 将City实体转换为CityVO
     */
    private CityVO convertToVO(City city) {
        if (city == null) return null;
        CityVO vo = new CityVO();
        org.springframework.beans.BeanUtils.copyProperties(city, vo);
        return vo;
    }

    /**
     * 清除指定IP的城市缓存
     */
    public void clearIpCityCache(String ip) {
        String cacheKey = IP_CITY_KEY_PREFIX + ip;
        redisTemplate.delete(cacheKey);
        log.debug("已清除IP城市缓存: {}", ip);
    }

    /**
     * 刷新所有城市缓存
     */
    public void refreshCityCache() {
        log.info("开始刷新城市缓存");
        try {
            // 清除所有城市相关的缓存（需要遍历）
            // 这里简化处理，直接重新预热
            preloadCityData();
            log.info("城市缓存刷新完成");
        } catch (Exception e) {
            log.error("刷新城市缓存失败", e);
        }
    }
}