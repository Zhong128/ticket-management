package org.example.ticketmanagement.serviceimpl;

import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.service.*;
import org.example.ticketmanagement.util.IpLocationUtil;
import org.example.ticketmanagement.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HomeServiceImpl implements HomeService {

    @Autowired
    private CityService cityService;

    @Autowired
    private EventCategoryService eventCategoryService;

    @Autowired
    private EventService eventService;

    @Autowired
    private IpLocationUtil ipLocationUtil;

    // 每个分类默认显示4场演出
    private static final Integer DEFAULT_LIMIT_PER_CATEGORY = 4;

    @Override
    public HomeRecommendVO getHomeRecommend(Long cityId, Long userId,
                                            HttpServletRequest request, Integer limit) {
        log.info("获取首页推荐 - userId: {}, cityId: {}, limit: {}", userId, cityId, limit);

        // 1. 确定城市ID（按优先级）
        Long targetCityId = determineCityId(cityId, userId, request);

        // 2. 获取城市信息
        CityVO currentCity = cityService.getCityById(targetCityId);
        if (currentCity == null) {
            log.error("城市不存在，ID: {}", targetCityId);
            return createErrorHomeRecommend();
        }

        // 3. 获取IP地址
        String clientIp = ipLocationUtil.getClientIp(request);

        // 4. 获取位置来源描述
        String locationSource = getLocationSource(cityId, userId, request);

        // 5. 获取默认分类ID
        Long[] categoryIds = ipLocationUtil.getHomeCategoryIds();

        // 6. 获取每个分类的演出
        List<CategoryWithEventsVO> categoriesWithEvents = new ArrayList<>();
        int totalEvents = 0;

        for (Long categoryId : categoryIds) {
            try {
                // 获取分类信息
                EventCategoryVO category = eventCategoryService.getCategoryById(categoryId);
                if (category == null || category.getStatus() != 1) {
                    log.debug("分类不存在或已禁用: {}", categoryId);
                    continue;
                }

                // 获取该分类在该城市下的演出
                List<EventVO> events = getEventsForCategoryAndCity(categoryId, targetCityId, limit);

                if (!events.isEmpty()) {
                    CategoryWithEventsVO categoryWithEvents = new CategoryWithEventsVO(category, events);
                    categoriesWithEvents.add(categoryWithEvents);
                    totalEvents += events.size();
                }

            } catch (Exception e) {
                log.error("获取分类 {} 的演出失败", categoryId, e);
            }
        }

        // 7. 构建返回结果
        HomeRecommendVO result = new HomeRecommendVO();
        result.setCurrentCity(currentCity);
        result.setLocationSource(locationSource);
        result.setIpAddress(clientIp);
        result.setCategories(categoriesWithEvents);
        result.setTotalEvents(totalEvents);

        log.info("首页推荐生成完成 - 城市: {}, 分类数: {}, 演出总数: {}",
                currentCity.getName(), categoriesWithEvents.size(), totalEvents);

        return result;
    }


    @Override
    public HomeRecommendVO getHomeRecommendByCityId(Long cityId, Integer limit) {
        log.info("根据城市ID获取首页推荐: {}, limit: {}", cityId, limit);

        // 获取城市信息
        CityVO currentCity = cityService.getCityById(cityId);
        if (currentCity == null) {
            log.error("城市不存在，ID: {}", cityId);
            return createErrorHomeRecommend();
        }

        // 获取默认分类ID
        Long[] categoryIds = ipLocationUtil.getHomeCategoryIds();

        // 获取每个分类的演出
        List<CategoryWithEventsVO> categoriesWithEvents = new ArrayList<>();
        int totalEvents = 0;

        for (Long categoryId : categoryIds) {
            try {
                EventCategoryVO category = eventCategoryService.getCategoryById(categoryId);
                if (category == null || category.getStatus() != 1) {
                    continue;
                }

                List<EventVO> events = getEventsForCategoryAndCity(categoryId, cityId, limit);

                if (!events.isEmpty()) {
                    CategoryWithEventsVO categoryWithEvents = new CategoryWithEventsVO(category, events);
                    categoriesWithEvents.add(categoryWithEvents);
                    totalEvents += events.size();
                }

            } catch (Exception e) {
                log.error("获取分类 {} 的演出失败", categoryId, e);
            }
        }

        HomeRecommendVO result = new HomeRecommendVO();
        result.setCurrentCity(currentCity);
        result.setLocationSource("city_id");
        result.setCategories(categoriesWithEvents);
        result.setTotalEvents(totalEvents);

        return result;
    }

    @Override
    public HomeRecommendVO getDefaultHomeRecommend(Integer limit) {
        Long defaultCityId = ipLocationUtil.getDefaultCityId();
        return getHomeRecommendByCityId(defaultCityId, limit);
    }

    /**
     * 确定最终使用的城市ID（优先级）
     */
    private Long determineCityId(Long paramCityId, Long userId, HttpServletRequest request) {
        // 1. 优先级最高：传入的城市ID
        if (paramCityId != null) {
            log.debug("使用传入的城市ID: {}", paramCityId);
            // 验证城市ID是否存在
            if (cityService.getCityById(paramCityId) != null) {
                return paramCityId;
            } else {
                log.warn("传入的城市ID不存在: {}, 使用其他方式确定城市", paramCityId);
            }
        }


        // 2. IP定位
        if (request != null) {
            String clientIp = ipLocationUtil.getClientIp(request);
            Long ipCityId = ipLocationUtil.getCityIdByIp(clientIp);
            if (ipCityId != null) {
                log.debug("使用IP定位的城市ID: {}", ipCityId);
                return ipCityId;
            }
        }

        // 3. 默认城市（北京）
        Long defaultCityId = ipLocationUtil.getDefaultCityId();
        log.debug("使用默认城市ID: {}", defaultCityId);
        return defaultCityId;
    }

    /**
     * 获取位置来源描述
     */
    private String getLocationSource(Long cityId, Long userId, HttpServletRequest request) {
        if (cityId != null) {
            return "parameter";  // 参数指定
        } else if (userId != null) {
            return "user_setting"; // 用户设置
        } else if (request != null) {
            return "ip_location";  // IP定位
        } else {
            return "default";      // 默认
        }
    }

    /**
     * 获取指定分类和城市下的演出列表
     */
    private List<EventVO> getEventsForCategoryAndCity(Long categoryId, Long cityId, Integer limit) {
        // 1. 获取符合条件的演出列表
        List<EventVO> events = eventService.getEventsByCategoryAndCity(categoryId, cityId);

        // 2. 过滤：只显示已发布且未结束的演出
        events = events.stream()
                .filter(event -> event != null)
                .filter(event -> event.getStatus() == 1) // 已发布
                .filter(event -> event.getEventEndTime() != null)
                .filter(event -> event.getEventEndTime().isAfter(java.time.LocalDateTime.now())) // 未结束
                .sorted((e1, e2) -> {
                    // 排序：即将开始的在前，然后按开始时间升序
                    if (e1.getEventStartTime() != null && e2.getEventStartTime() != null) {
                        return e1.getEventStartTime().compareTo(e2.getEventStartTime());
                    }
                    return 0;
                })
                .collect(Collectors.toList());

        // 3. 限制数量
        int actualLimit = (limit != null && limit > 0) ? limit : DEFAULT_LIMIT_PER_CATEGORY;
        if (events.size() > actualLimit) {
            events = events.subList(0, actualLimit);
        }

        return events;
    }

    /**
     * 创建错误时的首页推荐（返回空数据）
     */
    private HomeRecommendVO createErrorHomeRecommend() {
        HomeRecommendVO result = new HomeRecommendVO();
        result.setLocationSource("error");
        result.setCategories(new ArrayList<>());
        result.setTotalEvents(0);
        return result;
    }
}