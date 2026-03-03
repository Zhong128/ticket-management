package org.example.ticketmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 微信用户信息DTO
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WechatUserInfoDTO {
    private String openid;
    private String nickname;
    private Integer sex;
    private String province;
    private String city;
    private String country;
    private String headimgurl;
    private String unionid;
    private Integer errcode;
    private String errmsg;

    public boolean isSuccess() {
        return (errcode == null || errcode == 0) && openid != null;
    }
}
