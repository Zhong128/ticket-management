package org.example.ticketmanagement.trade;

import com.alipay.api.internal.util.AlipaySignature;
import org.example.ticketmanagement.config.AlipayProperties;
import org.example.ticketmanagement.util.AlipaySignatureUtil;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AlipaySignatureUtilTest {

    @Test
    void validSignatureIsAcceptedWithoutMutatingOriginalParameters() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        var properties = new AlipayProperties();
        properties.setAlipayPublicKey(Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
        properties.setCharset("UTF-8");
        Map<String, String> params = new HashMap<>();
        params.put("out_trade_no", "P1");
        params.put("total_amount", "10.00");
        params.put("sign", AlipaySignature.rsaSign(AlipaySignature.getSignContent(params),
                Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()), "UTF-8", "RSA2"));
        params.put("sign_type", "RSA2");
        var original = Map.copyOf(params);
        assertTrue(new AlipaySignatureUtil(properties).verifyAlipaySignature(params));
        assertEquals(original, params);
    }
}
