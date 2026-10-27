package org.example.ticketmanagement.maintenance;

import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradeQueryRequest;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** 显式的沙箱查单诊断，不创建、支付或退款交易，不连接数据库。 */
public final class AlipaySandboxCheck {

    private AlipaySandboxCheck() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Usage: AlipaySandboxCheck <local-sandbox-payment-no>");
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("config/application-local.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        var client = new DefaultAlipayClient("https://openapi-sandbox.dl.alipaydev.com/gateway.do",
                setting(properties, "ALIPAY_APP_ID", "alipay.app-id"),
                setting(properties, "ALIPAY_MERCHANT_PRIVATE_KEY", "alipay.merchant-private-key"), "json", "UTF-8",
                setting(properties, "ALIPAY_PUBLIC_KEY", "alipay.alipay-public-key"), "RSA2");
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(args[0]);
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizModel(model);
        try {
            var response = client.execute(request);
            System.out.println("sandbox-query-code=" + response.getCode());
            System.out.println("sandbox-query-subcode=" + response.getSubCode());
            System.out.println("sandbox-channel-reachable=" + (response.isSuccess()
                    || "ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())));
        } catch (Exception exception) {
            System.out.println("sandbox-channel-reachable=false; exception=" + exception.getClass().getSimpleName());
            System.exit(1);
        }
    }

    private static String setting(Properties properties, String name, String property) {
        String environment = System.getenv(name);
        String value = environment == null || environment.isBlank()
                ? properties.getProperty(name, properties.getProperty(property, "")) : environment;
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing " + name);
        }
        return value;
    }
}
