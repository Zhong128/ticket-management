package org.example.ticketmanagement.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/** 同源打包的Vue页面刷新回退，API及缺失静态文件不回退HTML。 */
@Configuration
public class FrontendResourcesConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource requested = super.getResource(path, location);
                        if (requested != null) {
                            return requested;
                        }

                        // 控制器仍优先匹配；未知API和资源保持原有错误响应。
                        if (path.equals("api") || path.startsWith("api/") || path.startsWith("assets/")
                                || path.contains(".")) {
                            return null;
                        }
                        return super.getResource("index.html", location);
                    }
                });
    }
}
