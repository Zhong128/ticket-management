// org/example/ticketmanagement/service/VerificationCodeService.java
package org.example.ticketmanagement.service;

import java.util.concurrent.TimeUnit;

public interface VerificationCodeService {
    /**
     * 发送验证码到邮箱
     */
    boolean sendVerificationCode(String email);

    /**
     * 重新发送验证码到邮箱
     */
    boolean resendVerificationCode(String email);

    /**
     * 验证验证码是否正确
     */
    boolean verifyCode(String email, String code);

    /**
     * 获取验证码剩余时间（秒）
     */
    Long getRemainingTime(String email);

    /**
     * 清除验证码（可用于某些场景下的手动清除）
     */
    void clearCode(String email);


    void recordUserInitiatedSend(String email);

    /**
     * 检查用户是否已主动发送过验证码
     */
    boolean hasUserInitiatedSend(String email);

    /**
     * 清除用户主动发送验证码的状态记录
     */
    void clearUserInitiatedSend(String email);

}
