package org.example.ticketmanagement.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Path;

/** 头像只公开专用目录内规范化的PNG，不接受用户指定任意文件路径。 */
@Configuration
public class AvatarResourcesConfig implements WebMvcConfigurer {
    private final String location;

    public AvatarResourcesConfig(@Value("${app.avatar-directory:./uploads/avatars}") String directory) {
        String uri = Path.of(directory).toAbsolutePath().normalize().toUri().toString();
        this.location = uri.endsWith("/") ? uri : uri + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/avatars/**")
                .addResourceLocations(location)
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        if (!path.matches("[1-9][0-9]*-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png")) {
                            return null;
                        }
                        return super.getResource(path, location);
                    }
                });
    }
}
