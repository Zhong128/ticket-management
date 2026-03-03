package org.example.ticketmanagement.dto;

import lombok.Data;
import org.example.ticketmanagement.pojo.User;

// 微信登录结果DTO
@Data
public class WechatLoginResultDTO {
    private boolean success;
    private String token;
    private User user;
    private boolean isNewUser;
    private String message;

    public static WechatLoginResultDTO success(String token, User user, boolean isNewUser) {
        WechatLoginResultDTO result = new WechatLoginResultDTO();
        result.setSuccess(true);
        result.setToken(token);
        result.setUser(user);
        result.setNewUser(isNewUser);
        return result;
    }

    public static WechatLoginResultDTO fail(String message) {
        WechatLoginResultDTO result = new WechatLoginResultDTO();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }
}