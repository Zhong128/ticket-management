// org/example/ticketmanagement/config/AlipayProperties.java
package org.example.ticketmanagement.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝配置属性类
 */
@Data
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {
    private String appId;               // 应用ID
    private String merchantPrivateKey;  // 商户私钥
    private String alipayPublicKey;     // 支付宝公钥
    private String gatewayUrl;          // 支付宝网关
    private String notifyUrl;           // 异步通知地址
    private String returnUrl;           // 同步返回地址
    private String signType = "RSA2";   // 签名类型
    private String charset = "UTF-8";   // 字符集
    private Integer timeoutMinutes = 30;// 支付超时时间（分钟）

    /**
     * 获取格式化后的超时时间（支付宝格式：30m）
     */
    public String getFormattedTimeout() {
        return timeoutMinutes + "m";
    }
}