// org/example/ticketmanagement/serviceimpl/VerificationCodeServiceImpl.java
package org.example.ticketmanagement.serviceimpl;

import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.service.EmailService;
import org.example.ticketmanagement.service.VerificationCodeService;
import org.example.ticketmanagement.util.RedisUtil;
import org.example.ticketmanagement.util.VerificationCodeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private EmailService emailService;

    // 验证码过期时间（分钟）
    @Value("${verification.code.expire-time:15}")
    private int expireTime;

    // 验证码长度
    @Value("${verification.code.length:6}")
    private int codeLength;

    // 发送间隔时间（秒）
    @Value("${verification.code.send-interval:60}")
    private int sendInterval;

    // 最大重试次数
    @Value("${verification.code.max-retry:3}")
    private int maxRetry;

    @Override
    public boolean sendVerificationCode(String email) {
        // 检查发送间隔
        if (isInSendInterval(email)) {
            log.warn("发送验证码过于频繁，邮箱: {}", email);
            throw new RuntimeException("发送验证码过于频繁，请稍后再试");
        }

        // 检查重试次数
        if (isExceedMaxRetry(email)) {
            log.warn("超过最大重试次数，邮箱: {}", email);
            throw new RuntimeException("超过最大重试次数，请稍后再试");
        }

        // 生成验证码
        String code = VerificationCodeUtil.generateNumericCode(codeLength);

        // 保存验证码到Redis，设置过期时间
        String codeKey = getCodeKey(email);
        // 添加随机过期时间防止雪崩
        int randomExpire = expireTime * 60 + new Random().nextInt(300); // ±5分钟随机
        redisUtil.set(codeKey, code, randomExpire, TimeUnit.SECONDS);


        // 设置发送间隔
        String intervalKey = getIntervalKey(email);
        redisUtil.set(intervalKey, "1", sendInterval, TimeUnit.SECONDS);

        // 初始化重试次数为0
        String retryKey = getRetryKey(email);
        redisUtil.set(retryKey, "0", expireTime, TimeUnit.MINUTES);

        // 发送邮件
        boolean sent = emailService.sendVerificationCodeEmail(email, code);
        if (sent) {
            log.info("验证码发送成功，邮箱: {}，验证码: {}", email, code);
            return true;
        } else {
            // 邮件发送失败，删除Redis中的验证码相关数据
            redisUtil.delete(codeKey);
            redisUtil.delete(intervalKey);
            redisUtil.delete(retryKey);
            throw new RuntimeException("验证码发送失败，请稍后重试");
        }
    }

    @Override
    public boolean resendVerificationCode(String email) {
        // 检查发送间隔
        if (isInSendInterval(email)) {
            log.warn("发送验证码过于频繁，邮箱: {}", email);
            throw new RuntimeException("发送验证码过于频繁，请稍后再试");
        }

        // 检查重试次数
        if (isExceedMaxRetry(email)) {
            log.warn("超过最大重试次数，邮箱: {}", email);
            throw new RuntimeException("超过最大重试次数，请稍后再试");
        }

        // 增加重试次数
        String retryKey = getRetryKey(email);
        redisUtil.increment(retryKey, 1);

        // 生成新的验证码
        String code = VerificationCodeUtil.generateNumericCode(codeLength);

        // 保存验证码到Redis，设置过期时间
        String codeKey = getCodeKey(email);
        redisUtil.set(codeKey, code, expireTime, TimeUnit.MINUTES);

        // 设置发送间隔
        String intervalKey = getIntervalKey(email);
        redisUtil.set(intervalKey, "1", sendInterval, TimeUnit.SECONDS);

        // 发送邮件
        boolean sent = emailService.sendVerificationCodeEmail(email, code);
        if (sent) {
            log.info("验证码重新发送成功，邮箱: {}，验证码: {}", email, code);
            return true;
        } else {
            // 邮件发送失败，减少重试次数
            redisUtil.decrement(retryKey, 1);
            throw new RuntimeException("验证码发送失败，请稍后重试");
        }
    }

    @Override
    public boolean verifyCode(String email, String code) {
        // 测试环境固定验证码
        if ("test".equals(System.getProperty("spring.profiles.active")) && "1234".equals(code)) {
            return true;
        }
        if (email == null || code == null) {
            log.warn("验证码验证失败，参数为空");
            return false;
        }

        String codeKey = getCodeKey(email);
        String storedCode = redisUtil.getString(codeKey);

        if (storedCode == null) {
            log.warn("验证码不存在或已过期，邮箱: {}", email);
            return false;
        }

        boolean isValid = storedCode.equals(code.trim());
        if (isValid) {
            // 验证成功，删除验证码及相关数据
            redisUtil.delete(codeKey);
            redisUtil.delete(getIntervalKey(email));
            redisUtil.delete(getRetryKey(email));
            log.info("验证码验证成功，邮箱: {}", email);
        } else {
            log.warn("验证码验证失败，邮箱: {}，输入: {}，存储: {}", email, code, storedCode);
        }

        return isValid;
    }

    @Override
    public Long getRemainingTime(String email) {
        String codeKey = getCodeKey(email);
        return redisUtil.getExpire(codeKey, TimeUnit.SECONDS);
    }

    @Override
    public void clearCode(String email) {
        String codeKey = getCodeKey(email);
        redisUtil.delete(codeKey);
        redisUtil.delete(getIntervalKey(email));
        redisUtil.delete(getRetryKey(email));
        log.info("验证码已清除，邮箱: {}", email);
    }
    
    @Override
    public void recordUserInitiatedSend(String email) {
        String key = getUserInitiatedSendKey(email);
        // 记录用户主动发送验证码的状态，有效期与验证码一致
        redisUtil.set(key, "1", expireTime, TimeUnit.MINUTES);
        log.info("记录用户主动发送验证码状态: email={}", email);
    }
    
    @Override
    public boolean hasUserInitiatedSend(String email) {
        String key = getUserInitiatedSendKey(email);
        return redisUtil.hasKey(key);
    }
    
    /**
     * 生成用户主动发送验证码状态的key
     */
    private String getUserInitiatedSendKey(String email) {
        return "verification:user-initiated:" + email;
    }
    
    @Override
    public void clearUserInitiatedSend(String email) {
        String key = getUserInitiatedSendKey(email);
        redisUtil.delete(key);
        log.info("清除用户主动发送验证码状态: email={}", email);
    }
    
    /**
     * 生成验证码前缀
     */
    private String getCodeKey(String email) {
        return "verification:code:" + email;
    }

    /**
     * 生成发送间隔前缀
     */
    private String getIntervalKey(String email) {
        return "verification:interval:" + email;
    }

    /**
     * 生成重试次数前缀
     */
    private String getRetryKey(String email) {
        return "verification:retry:" + email;
    }

    /**
     * 检查是否在发送间隔内
     */
    private boolean isInSendInterval(String email) {
        String intervalKey = getIntervalKey(email);
        return redisUtil.hasKey(intervalKey);
    }

    /**
     * 检查是否超过最大重试次数
     */
    private boolean isExceedMaxRetry(String email) {
        String retryKey = getRetryKey(email);
        if (!redisUtil.hasKey(retryKey)) {
            return false;
        }
        String retryCountStr = redisUtil.getString(retryKey);
        int retryCount = Integer.parseInt(retryCountStr);
        return retryCount >= maxRetry;
    }
}
