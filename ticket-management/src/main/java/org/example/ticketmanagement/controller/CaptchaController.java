
package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckDisable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.CaptchaResult;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.service.CaptchaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/captcha/public")
@Tag(name = "验证码/图形验证码", description = "图形验证码相关接口")
@SaCheckDisable
public class CaptchaController {

    @Autowired
    private CaptchaService captchaService;

    /**
     * 获取图形验证码
     */
    @Operation(summary = "获取图形验证码", tags = {"图形验证码"})
    @GetMapping
    public Result<CaptchaResult> getCaptcha() {
        try {
            CaptchaResult captchaResult = captchaService.generateCaptcha();
            return Result.success(captchaResult);
        } catch (Exception e) {
            log.error("获取图形验证码失败", e);
            return Result.error("获取验证码失败");
        }
    }
}
