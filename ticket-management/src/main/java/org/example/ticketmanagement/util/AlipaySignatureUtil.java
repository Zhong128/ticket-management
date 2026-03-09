// org/example/ticketmanagement/util/AlipaySignatureUtil.java
package org.example.ticketmanagement.util;

import com.alipay.api.internal.util.AlipaySignature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.config.AlipayProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付宝签名验证工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlipaySignatureUtil {

    private final AlipayProperties alipayProperties;

    /**
     * 验证支付宝异步通知签名
     *
     * @param params 回调参数Map
     * @return 签名是否有效
     */
    public boolean verifyAlipaySignature(Map<String, String> params) {
        try {
            // 获取签名和签名类型
            String sign = params.get("sign");
            String signType = params.get("sign_type");

            if (sign == null || signType == null) {
                log.warn("支付宝回调缺少签名参数: sign={}, sign_type={}", sign, signType);
                return false;
            }

            // 移除sign和sign_type参数，这两个参数不参与签名
            params.remove("sign");
            params.remove("sign_type");

            // 验证签名
            boolean verified = AlipaySignature.rsaCheckV1(
                    params,
                    alipayProperties.getAlipayPublicKey(),
                    alipayProperties.getCharset(),
                    signType
            );

            if (verified) {
                log.debug("支付宝签名验证成功");
            } else {
                log.warn("支付宝签名验证失败");
            }

            return verified;

        } catch (Exception e) {
            log.error("支付宝签名验证异常", e);
            return false;
        }
    }

    /**
     * 验证同步回调签名（页面跳转）
     *
     * @param params 回调参数Map
     * @return 签名是否有效
     */
    public boolean verifyAlipayReturnSignature(Map<String, String> params) {
        return verifyAlipaySignature(params);
    }

}
