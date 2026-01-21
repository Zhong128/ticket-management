// org/example/ticketmanagement/config/RedissonConfig.java
package org.example.ticketmanagement.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RedissonConfig {

    @Value("${redisson.config}")
    private String redissonConfig;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        try {
            Config config = Config.fromYAML(redissonConfig);
            RedissonClient redisson = Redisson.create(config);
            log.info("Redisson客户端初始化成功");
            return redisson;
        } catch (Exception e) {
            log.error("Redisson客户端初始化失败", e);
            throw new RuntimeException("Redisson客户端初始化失败", e);
        }
    }
}