package org.example.ticketmanagement.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // 注册JavaTime模块，支持Java8时间API
        objectMapper.registerModule(new JavaTimeModule());

        // 禁用日期序列化为时间戳
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 美化输出（开发环境使用）
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        return objectMapper;
    }
}