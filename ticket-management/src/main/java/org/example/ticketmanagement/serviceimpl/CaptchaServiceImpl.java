package org.example.ticketmanagement.serviceimpl;

import com.google.code.kaptcha.impl.DefaultKaptcha;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.CaptchaResult;
import org.example.ticketmanagement.service.CaptchaService;
import org.example.ticketmanagement.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CaptchaServiceImpl implements CaptchaService {

    @Autowired
    private DefaultKaptcha captchaProducer;

    @Autowired
    private RedisUtil redisUtil;

    private static final long CAPTCHA_EXPIRE_TIME = 5; // 验证码过期时间(分钟)

    @Override
    public CaptchaResult generateCaptcha() {
        // 生成验证码文本
        String capText = captchaProducer.createText();

        // 生成验证码图片
        BufferedImage bi = captchaProducer.createImage(capText);

        // 将图片转换为Base64编码
        String imageBase64 = "";
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", baos);
            byte[] bytes = baos.toByteArray();
            imageBase64 = Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.error("验证码图片转换失败", e);
        }

        // 生成唯一标识符
        String captchaKey = UUID.randomUUID().toString();

        // 将验证码文本存储到Redis中
        redisUtil.set("captcha:" + captchaKey, capText, CAPTCHA_EXPIRE_TIME, TimeUnit.MINUTES);

        CaptchaResult result = new CaptchaResult();
        result.setCaptchaKey(captchaKey);
        result.setImageBase64("data:image/png;base64," + imageBase64);
        return result;
    }

    @Override
    public boolean validateCaptcha(String captchaKey, String userInput) {
        if (captchaKey == null || userInput == null) {
            return false;
        }

        // 从Redis中获取验证码文本
        String storedCaptcha = redisUtil.getString("captcha:" + captchaKey);
        if (storedCaptcha == null) {
            return false;
        }

        // 验证用户输入
        boolean isValid = storedCaptcha.equalsIgnoreCase(userInput.trim());

        // 验证成功后删除验证码
        if (isValid) {
            redisUtil.delete("captcha:" + captchaKey);
        }

        return isValid;
    }
}
