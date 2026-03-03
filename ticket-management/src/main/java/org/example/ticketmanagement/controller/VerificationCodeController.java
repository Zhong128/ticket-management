package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckDisable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.SendCodeRequestDTO;
import org.example.ticketmanagement.dto.VerificationCodeDTO;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.service.CaptchaService;
import org.example.ticketmanagement.service.VerificationCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/public/verification")
@Tag(name = "验证码/短信验证码", description = "验证码发送与验证相关接口")
public class VerificationCodeController {

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private CaptchaService captchaService;

    /**
     * 发送验证码
     */
    @Operation(summary = "发送验证码", tags = {"验证码"})
    @PostMapping("/send")
    public Result<Void> sendVerificationCode(@Valid @RequestBody SendCodeRequestDTO requestDTO,
                                             @RequestParam(required = false) String captchaKey,
                                             @RequestParam(required = false) String captchaCode) {
        log.info("发送验证码，邮箱: {}, 类型: {}", requestDTO.getEmail(), requestDTO.getType());

        try {
            // 验证图形验证码（如果是新用户）
            if (captchaKey != null && captchaCode != null) {
                boolean captchaValid = captchaService.validateCaptcha(captchaKey, captchaCode);
                if (!captchaValid) {
                    return Result.error("图形验证码错误");
                }
            }

            boolean success = verificationCodeService.sendVerificationCode(requestDTO.getEmail());
            if (success) {
                // 记录用户主动发送验证码的状态
                verificationCodeService.recordUserInitiatedSend(requestDTO.getEmail());
                return Result.success("验证码发送成功");
            } else {
                return Result.error("验证码发送失败");
            }
        } catch (IllegalArgumentException e) {
            log.warn("参数错误: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("发送验证码失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("发送验证码异常: {}", e.getMessage(), e);
            return Result.error("验证码发送失败");
        }
    }

    /**
     * 重新发送验证码(不需要图形验证码)
     */
    @Operation(summary = "重新发送验证码", tags = {"验证码"})
    @PostMapping("/resend")
    public Result<Void> resendVerificationCode(@Valid @RequestBody SendCodeRequestDTO requestDTO) {
        log.info("重新发送验证码，邮箱: {}", requestDTO.getEmail());

        try {
            boolean success = verificationCodeService.resendVerificationCode(requestDTO.getEmail());
            if (success) {
                return Result.success("验证码重新发送成功");
            } else {
                return Result.error("验证码重新发送失败");
            }
        } catch (IllegalArgumentException e) {
            log.warn("参数错误: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("重新发送验证码失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("重新发送验证码异常: {}", e.getMessage(), e);
            return Result.error("验证码重新发送失败");
        }
    }


    /**
     * 获取验证码剩余时间
     */
    @Operation(summary = "获取验证码剩余时间", tags = {"验证码"})
    @GetMapping("/remaining-time/{email}")
    public Result<Long> getRemainingTime(@PathVariable String email) {
        log.info("获取验证码剩余时间，邮箱: {}", email);

        try {
            Long remainingTime = verificationCodeService.getRemainingTime(email);
            return Result.success("查询成功", remainingTime);
        } catch (Exception e) {
            log.error("获取验证码剩余时间失败: {}", e.getMessage(), e);
            return Result.error("查询失败");
        }
    }

}