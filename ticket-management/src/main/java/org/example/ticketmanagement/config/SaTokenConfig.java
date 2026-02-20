// org/example/ticketmanagement/config/SaTokenConfig.java
package org.example.ticketmanagement.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.RoleConstant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SaToken 配置类
 * 1. 注册拦截器
 * 2. 配置全局过滤器
 * 注意：需要根据原有白名单路径进行调整
 */
@Slf4j
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 注册 Sa-Token 拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，打开注解式鉴权功能
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }

    /**
     * 注册 Sa-Token 全局过滤器
     */
    @Bean
    public SaServletFilter saServletFilter() {
        return new SaServletFilter()
                // 拦截所有路径
                .addInclude("/**")
                // 排除不需要过滤的路径（保持与原白名单一致）
                .addExclude("/favicon.ico")
                .addExclude("/error")
                .addExclude("/swagger-ui/**")
                .addExclude("/v3/api-docs/**")
                .addExclude("/webjars/**")

                // 认证函数：每次请求执行
                .setAuth(obj -> {
                    log.debug("SaToken过滤器：验证路由权限");

                    // ============ 公共接口直接放行 ============

                    // 认证鉴权相关接口
                    SaRouter
                            .match("/api/auth/**")
                            .check(r -> {});

                    // 其他公共接口
                    SaRouter
                            .match("/api/**/public","/api/**/public/**")                        // 所有公共接口(查+验证码有关)
                            .check(r -> {});

                    // ============ 用户端接口权限验证 ============

                    // 用户端接口需要 USER 或以上角色
                    SaRouter.match("/api/**/user","/api/**/user/**").check(r -> {
                        StpUtil.checkLogin();
                        if (!StpUtil.hasRole(RoleConstant.USER)) {
                            throw new RuntimeException("需要用户角色才能访问");
                        }
                        log.debug("用户 {} 访问用户接口: {}",
                                StpUtil.getLoginId(),
                                SaHolder.getRequest().getRequestPath());
                    });

                    // ============ VIP接口权限验证 ============ (目前暂未使用，后续可考虑拓展)

                    SaRouter.match("/api/**/vip","/api/**/vip/**").check(r -> {
                        StpUtil.checkLogin();
                        if (!StpUtil.hasRole(RoleConstant.VIP)) {
                            throw new RuntimeException("需要VIP角色才能访问");
                        }
                        log.debug("VIP用户 {} 访问VIP接口: {}",
                                StpUtil.getLoginId(),
                                SaHolder.getRequest().getRequestPath());
                    });

                    // ============ 管理端接口权限验证 ============

                    SaRouter.match("/api/**/admin","/api/**/admin/**").check(r -> {
                        StpUtil.checkLogin();
                        if (!StpUtil.hasRole(RoleConstant.ADMIN) &&
                                !StpUtil.hasRole(RoleConstant.SUPER_ADMIN)) {
                            throw new RuntimeException("需要管理员角色才能访问");
                        }
                        log.debug("管理员 {} 访问管理接口: {}",
                                StpUtil.getLoginId(),
                                SaHolder.getRequest().getRequestPath());
                    });

                    // ============ 其他接口默认需要登录 ============

                    // 对于未明确匹配的API接口，默认需要登录
                    SaRouter.match("/api/**").check(r -> {
                        log.warn("未明确权限配置的API接口: {}，默认要求登录",
                                SaHolder.getRequest().getRequestPath());
                        StpUtil.checkLogin();
                    });
                })

                // 异常处理函数
                .setError(e -> {
                    log.error("SaToken过滤器异常: {}", e.getMessage());
                    // 返回统一的JSON格式
                    return "{\"code\": 0, \"msg\": \"" + e.getMessage() + "\", \"data\": null}";
                })

                // 前置函数：在每次认证函数之前执行
                .setBeforeAuth(r -> {
                    // 记录请求日志
                    String requestPath = SaHolder.getRequest().getRequestPath();
                    String method = SaHolder.getRequest().getMethod();
                    log.debug("请求进入: {} {}", method, requestPath);

                    // 设置一些安全响应头
                    SaHolder.getResponse()
                            .setHeader("Server", "Ticket-Management-Server")
                            .setHeader("X-Content-Type-Options", "nosniff")
                            .setHeader("X-Frame-Options", "DENY");

                    // 设置CORS头（如果还没在全局配置）
                    SaHolder.getResponse()
                            .setHeader("Access-Control-Allow-Origin", "*")
                            .setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                            .setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, token")
                            .setHeader("Access-Control-Max-Age", "3600");
                });


    }
}