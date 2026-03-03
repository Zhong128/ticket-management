package org.example.ticketmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 微信access_token响应DTO
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WechatAccessTokenDTO {
    private String access_token;
    private Integer expires_in;
    private String refresh_token;
    private String openid;
    private String scope;
    private String unionid;
    private Integer errcode;
    private String errmsg;
    //完善错误信息
    public boolean isSuccess() {
        return (errcode == null || errcode == 0) && access_token != null;
    }

    public String getErrorMessage() {
        if (isSuccess()) return null;
        return String.format("错误码:%d, 错误信息:%s", errcode, errmsg);
    }
}
