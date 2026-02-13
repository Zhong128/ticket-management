package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckDisable;
import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.vo.HomeRecommendVO;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.service.HomeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/home")
@Tag(name = "首页推荐", description = "首页公共推荐接口")
public class HomeController {

    @Autowired
    private HomeService homeService;

    // ==================== 公共接口（无需登录） ====================

    /**
     * 获取默认首页推荐（北京地区四大类）
     */
    @Operation(summary = "获取默认首页推荐", tags = {"首页推荐-公共接口"})
    @GetMapping("/public/recommend/default")
    @SaCheckDisable
    public Result<HomeRecommendVO> getDefaultHomeRecommend(
            @RequestParam(defaultValue = "4") Integer limit) {

        log.info("获取默认首页推荐，limit: {}", limit);

        try {
            HomeRecommendVO result = homeService.getDefaultHomeRecommend(limit);
            return Result.success("获取成功", result);
        } catch (Exception e) {
            log.error("获取默认首页推荐失败", e);
            return Result.error("获取默认首页推荐失败");
        }
    }

    // ==================== 用户端接口（需要登录） ====================

    /**
     * 获取首页推荐
     * 逻辑：
     * 1. 如果用户已登录 -> 从token获取用户ID -> 使用用户设置城市或IP定位
     * 2. 如果传入了cityId -> 优先使用传入的城市
     */
    @Operation(summary = "获取首页推荐", tags = {"首页推荐-用户端"})
    @GetMapping("/user/recommend")
    @SaCheckLogin
    public Result<HomeRecommendVO> getHomeRecommend(
            @RequestParam(required = false) Long cityId,
            @RequestParam(defaultValue = "4") Integer limit,
            HttpServletRequest request) {

        log.info("首页推荐请求 - 路径: {}, cityId: {}, limit: {}",
                request.getRequestURI(), cityId, limit);

        try {
            // 从请求属性中获取用户ID（拦截器设置的）
            Long userId = (Long) request.getAttribute("userId");

            HomeRecommendVO result = homeService.getHomeRecommend(cityId, userId, request, limit);

            return Result.success("获取成功", result);
        } catch (Exception e) {
            log.error("获取首页推荐失败", e);
            return Result.error("获取首页推荐失败，请稍后重试");
        }
    }

    /**
     * 根据指定城市获取首页推荐
     */
    @Operation(summary = "根据城市获取首页推荐", tags = {"首页推荐-用户端"})
    @GetMapping("/recommend/city/{cityId}")
    @SaCheckLogin
    public Result<HomeRecommendVO> getHomeRecommendByCity(
            @PathVariable Long cityId,
            @RequestParam(defaultValue = "4") Integer limit) {

        log.info("根据城市获取首页推荐，cityId: {}, limit: {}", cityId, limit);

        try {
            HomeRecommendVO result = homeService.getHomeRecommendByCityId(cityId, limit);
            return Result.success("获取成功", result);
        } catch (Exception e) {
            log.error("根据城市获取首页推荐失败", e);
            return Result.error("获取失败，请检查城市ID是否正确");
        }
    }

    /**
     * 获取客户端IP和用户状态信息（调试用）
     */
    @Operation(summary = "获取调试信息", tags = {"首页推荐-用户端"})
    @GetMapping("/debug/info")
    @SaCheckLogin
    public Result<Object> getDebugInfo(HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            String ip = request.getRemoteAddr();

            // 使用显式构造避免字段读取问题
            return Result.success("获取成功", new Object() {
                public final Long currentUserId = userId;  // 改变名称以避免混淆
                public final String clientIp = ip;
                public final boolean isAuthenticated = currentUserId != null;
                public final String requestURI = request.getRequestURI();
            });
        } catch (Exception e) {
            return Result.error("获取调试信息失败");
        }
    }
}
