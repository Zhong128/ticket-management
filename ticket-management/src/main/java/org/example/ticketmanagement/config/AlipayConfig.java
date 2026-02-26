package org.example.ticketmanagement.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝配置类
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AlipayConfig {

    private final AlipayProperties alipayProperties;

    @Bean
    public AlipayClient alipayClient() {
        log.info("初始化支付宝客户端，网关地址：{}，APPID：{}",
                alipayProperties.getGatewayUrl(), alipayProperties.getAppId());

        return new DefaultAlipayClient(
                alipayProperties.getGatewayUrl(),
                alipayProperties.getAppId(),
                alipayProperties.getMerchantPrivateKey(),
                "json",
                alipayProperties.getCharset(),
                alipayProperties.getAlipayPublicKey(),
                alipayProperties.getSignType()
        );
    }
}