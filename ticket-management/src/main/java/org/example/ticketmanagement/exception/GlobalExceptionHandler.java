// org/example/ticketmanagement/exception/GlobalExceptionHandler.java
package org.example.ticketmanagement.exception;

import cn.dev33.satoken.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.pojo.Result;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== SaToken 异常处理 ====================

    /**
     * 未登录异常
     */
    @ExceptionHandler(NotLoginException.class)
    public Result<?> handleNotLoginException(NotLoginException e) {
        log.warn("未登录访问: {}", e.getMessage());
        return Result.error(401, "请先登录");
    }

    /**
     * 无角色异常
     */
    @ExceptionHandler(NotRoleException.class)
    public Result<?> handleNotRoleException(NotRoleException e) {
        log.warn("无角色访问: {}", e.getMessage());
        return Result.error(403, "无权限访问，需要特定角色");
    }

    /**
     * 无权限异常
     */
    @ExceptionHandler(NotPermissionException.class)
    public Result<?> handleNotPermissionException(NotPermissionException e) {
        log.warn("无权限访问: {}", e.getMessage());
        return Result.error(403, "无操作权限");
    }

    /**
     * 二级认证异常
     */
    @ExceptionHandler(NotSafeException.class)
    public Result<?> handleNotSafeException(NotSafeException e) {
        log.warn("二级认证异常: {}", e.getMessage());
        return Result.error(403, "需要进行二级认证");
    }

    /**
     * 服务封禁异常
     */
    @ExceptionHandler(DisableServiceException.class)
    public Result<?> handleDisableServiceException(DisableServiceException e) {
        log.warn("服务封禁异常: {}", e.getMessage());
        return Result.error(403, "账号已被封禁，剩余时间: " + e.getDisableTime() + "秒");
    }

    // ==================== 原有异常处理保持不变 ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("参数校验失败", e);

        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return Result.error("参数校验失败: " + errors.toString());
    }

    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e) {
        log.warn("参数绑定失败", e);

        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return Result.error("参数绑定失败: " + errors.toString());
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统内部错误，请稍后重试");
    }
}