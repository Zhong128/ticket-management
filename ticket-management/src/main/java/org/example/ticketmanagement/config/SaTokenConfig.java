package org.example.ticketmanagement.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.RoleConstant;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @PostConstruct
    public void init() {
        log.info("SaTokenConfig 已加载 - 使用拦截器方式");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> {
                    // 打印当前请求路径
                    System.out.println("========== 请求路径: " + SaHolder.getRequest().getRequestPath() + " ==========");

                    // 放行所有不需要登录的接口（放在最前面）
                    SaRouter.match("/api/auth/**", r -> {}).stop();  // 显式 stop
                    SaRouter.match("/api/public/**", r -> {}).stop();
                    SaRouter.match("/swagger-ui/**", r -> {}).stop();
                    SaRouter.match("/v3/api-docs/**", r -> {}).stop();

// 需要登录的接口
                    SaRouter.match("/api/user/**").check(r -> {
                        StpUtil.checkLogin();
                        StpUtil.checkRole(RoleConstant.USER);
                    });

                    SaRouter.match("/api/admin/**").check(r -> {
                        StpUtil.checkLogin();
                        StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.SUPER_ADMIN);
                    });

// 默认需要登录
                    // 对于未明确匹配的API接口，默认需要登录
                    SaRouter.match("/api/**").check(r -> {
                        log.warn("未明确权限配置的API接口: {}，默认要求登录",
                                SaHolder.getRequest().getRequestPath());
                        StpUtil.checkLogin();
                    });

                }))
                .addPathPatterns("/**")
                .excludePathPatterns("/error");
    }
}