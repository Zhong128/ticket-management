package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.service.AvatarService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/user/avatar")
@SaCheckLogin
public class AvatarController {
    private final AvatarService avatarService;

    public AvatarController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @PutMapping(consumes = "multipart/form-data")
    public Result<Map<String, String>> upload(@RequestPart("file") MultipartFile file) {
        try {
            String avatar = avatarService.upload(StpUtil.getLoginIdAsLong(), file);
            return Result.success(Collections.singletonMap("avatar", avatar));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("头像上传失败", e);
            return Result.error("头像上传失败，请稍后重试");
        }
    }

    @DeleteMapping
    public Result<Map<String, String>> clear() {
        try {
            String avatar = avatarService.clear(StpUtil.getLoginIdAsLong());
            return Result.success(Collections.singletonMap("avatar", avatar));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("恢复默认头像失败", e);
            return Result.error("恢复默认头像失败，请稍后重试");
        }
    }
}
