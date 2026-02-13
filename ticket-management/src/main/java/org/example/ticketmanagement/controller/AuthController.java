// org/example/ticketmanagement/controller/AuthController.java
package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.RoleConstant;
import org.example.ticketmanagement.dto.*;
import org.example.ticketmanagement.pojo.LoginInfo;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.pojo.User;
import org.example.ticketmanagement.service.AuthService;
import org.example.ticketmanagement.service.VerificationCodeService;
import org.example.ticketmanagement.service.WechatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证鉴权", description = "统一认证相关接口")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private WechatService wechatService;

    // ==================== 用户认证接口 ====================

    /**
     * 用户登录/注册（合并接口）
     */
    @Operation(summary = "用户登录/注册", tags = {"认证-用户端"})
    @PostMapping("/user/login-or-register")
    public Result<LoginInfo> userLoginOrRegister(@Validated @RequestBody UserLoginOrRegisterDTO dto) {
        log.info("用户登录/注册: email={}", dto.getEmail());

        try {
            // 转换为User对象
            User user = new User();
            user.setEmail(dto.getEmail());
            user.setPassword(dto.getPassword());
            user.setUsername(dto.getUsername());
            user.setNickname(dto.getNickname());

            // 调用统一登录/注册服务
            LoginInfo loginInfo = authService.loginOrRegister(user);

            // 判断是新注册用户还是已有用户
            String message = loginInfo.getIsNewUser() != null && loginInfo.getIsNewUser()
                    ? "注册并登录成功"
                    : "登录成功";

            return Result.success(message, loginInfo);
        } catch (RuntimeException e) {
            // 特殊异常处理：新用户需要验证码
            if ("NEW_USER_NEED_VERIFICATION".equals(e.getMessage())) {
                return Result.error(200, "新用户，请验证邮箱验证码");
            }
            log.error("登录/注册失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("登录/注册异常: {}", e.getMessage(), e);
            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 验证码验证注册（新用户第二步）
     */
    @Operation(summary = "验证码验证注册", tags = {"认证-用户端"})
    @PostMapping("/user/verify-registration")
    public Result<LoginInfo> verifyRegistration(@Valid @RequestBody LoginWithCodeDTO dto) {
        log.info("验证码验证注册: email={}", dto.getEmail());

        try {
            // 验证验证码
            boolean isValid = verificationCodeService.verifyCode(dto.getEmail(), dto.getCode());
            if (!isValid) {
                return Result.error("验证码错误或已过期");
            }

            // 完成注册并登录
            LoginInfo loginInfo = authService.completeRegistrationWithCode(dto.getEmail());

            return Result.success("注册成功", loginInfo);
        } catch (RuntimeException e) {
            log.error("验证码验证失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("验证码验证异常: {}", e.getMessage(), e);
            return Result.error("验证失败，请稍后重试");
        }
    }

    // ==================== 管理员认证接口 ====================

    /**
     * 管理员登录
     */
    @Operation(summary = "管理员登录", tags = {"认证-管理端"})
    @PostMapping("/admin/login")
    public Result<LoginInfo> adminLogin(@Valid @RequestBody UserLoginDTO userLoginDTO) {
        log.info("管理员登录: email={}", userLoginDTO.getEmail());

        try {
            User user = new User();
            user.setEmail(userLoginDTO.getEmail());
            user.setPassword(userLoginDTO.getPassword());

            LoginInfo loginInfo = authService.adminLogin(user);

            return Result.success("管理员登录成功", loginInfo);
        } catch (RuntimeException e) {
            log.error("管理员登录失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("管理员登录异常: {}", e.getMessage(), e);
            return Result.error("登录失败，请稍后重试");
        }
    }

    // ==================== 微信认证接口 ====================

    /**
     * 获取微信登录二维码URL
     */
    @Operation(summary = "获取微信登录二维码", tags = {"认证-微信"})
    @GetMapping("/wechat/qrcode")
    public Result<Map<String, String>> getQrCode() {
        try {
            String qrCodeUrl = wechatService.getLoginQrCode();
            Map<String, String> data = new HashMap<>();
            data.put("url", qrCodeUrl);
            return Result.success("获取二维码URL成功", data);
        } catch (Exception e) {
            log.error("获取微信二维码失败", e);
            return Result.error("获取微信登录二维码失败");
        }
    }

    /**
     * 微信授权回调接口
     */
    @Operation(summary = "微信登录回调", tags = {"认证-微信"})
    @PostMapping("/wechat/callback")
    public Result<WechatLoginResultDTO> callback(@RequestBody WechatLoginDTO loginDTO) {
        try {
            WechatLoginResultDTO result = wechatService.handleCallback(loginDTO.getCode(), loginDTO.getState());
            if (result.isSuccess()) {
                String message = result.isNewUser() ? "微信登录成功(新用户)" : "微信登录成功";
                return Result.success(message, result);
            } else {
                return Result.error(result.getMessage());
            }
        } catch (Exception e) {
            log.error("微信登录回调处理失败", e);
            return Result.error("微信登录处理失败");
        }
    }

    // ==================== 通用接口 ====================

    /**
     * 退出登录（通用）
     */
    @Operation(summary = "退出登录", tags = {"认证-公共接口"})
    @PostMapping("/logout")
    @SaCheckLogin
    public Result<Void> logout() {
        log.info("退出登录，用户ID: {}", StpUtil.getLoginId());

        try {
            authService.logout(null); // SaToken会自动处理，这里传null即可
            return Result.success("退出成功");
        } catch (Exception e) {
            log.error("退出登录异常: {}", e.getMessage(), e);
            return Result.error("退出失败，请稍后重试");
        }
    }

    /**
     * 获取当前用户信息
     */
    @Operation(summary = "获取当前用户信息", tags = {"认证-公共接口"})
    @GetMapping("/userinfo")
    @SaCheckLogin
    public Result<Map<String, Object>> getCurrentUserInfo() {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            String role = (String) StpUtil.getSession().get("role");

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("userId", userId);
            userInfo.put("role", role);
            userInfo.put("token", StpUtil.getTokenValue());

            return Result.success("查询成功", userInfo);
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            return Result.error("获取用户信息失败");
        }
    }

    /**
     * 获取当前用户角色
     */
    @Operation(summary = "获取当前用户角色", tags = {"认证-公共接口"})
    @GetMapping("/role")
    @SaCheckLogin
    public Result<Map<String, String>> getCurrentRole() {
        try {
            Map<String, String> data = new HashMap<>();
            data.put("role", (String) StpUtil.getSession().get("role"));
            return Result.success("查询成功", data);
        } catch (Exception e) {
            log.error("获取用户角色失败", e);
            return Result.error("获取用户角色失败");
        }
    }
}