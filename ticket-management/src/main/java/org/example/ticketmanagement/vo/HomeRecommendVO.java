package org.example.ticketmanagement.vo;

import lombok.Data;
import java.util.List;

/**
 * 首页推荐响应VO
 */
@Data
public class HomeRecommendVO {
    private CityVO currentCity;            // 当前城市信息
    private String locationSource;         // 位置来源：ip/db/default
    private String ipAddress = "";         // 用户IP地址，默认空字符串
    private List<CategoryWithEventsVO> categories; // 分类+演出列表
    private Integer totalEvents;           // 推荐演出总数
    private Long timestamp;                // 响应时间戳

    public HomeRecommendVO() {
        this.timestamp = System.currentTimeMillis();
    }
}
