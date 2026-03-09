// org/example/ticketmanagement/service/HomeService.java
package org.example.ticketmanagement.service;

import org.example.ticketmanagement.vo.HomeRecommendVO;

/**
 * 首页推荐服务接口
 */
public interface HomeService {

    /**
     * 获取首页演出推荐（综合方法）
     *
     * @param cityId 城市ID（可选，优先级：参数 > 用户设置 > IP定位 > 默认）
     * @param userId 用户ID（可选，已登录用户）
     * @param request HTTP请求对象（用于获取IP）
     * @param limit 每个分类显示的演出数量
     * @return 首页推荐数据
     */
    HomeRecommendVO getHomeRecommend(Long cityId, Long userId,
                                     jakarta.servlet.http.HttpServletRequest request,
                                     Integer limit);


    /**
     * 根据城市ID获取首页推荐
     */
    HomeRecommendVO getHomeRecommendByCityId(Long cityId, Integer limit);

    /**
     * 获取默认城市（北京）的首页推荐
     */
    HomeRecommendVO getDefaultHomeRecommend(Integer limit);
}