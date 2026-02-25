// org/example/ticketmanagement/serviceimpl/WechatServiceImpl.java
package org.example.ticketmanagement.serviceimpl;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.config.WechatConfig;
import org.example.ticketmanagement.dto.*;
import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.pojo.User;
import org.example.ticketmanagement.service.WechatService;
import org.example.ticketmanagement.constant.RoleConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class WechatServiceImpl implements WechatService {

    @Autowired
    private WechatConfig wechatConfig;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public String getLoginQrCode() {
        // 生成state参数防止CSRF
        String state = UUID.randomUUID().toString();

        String url = "https://open.weixin.qq.com/connect/qrconnect" +
                "?appid=" + wechatConfig.getAppId() +
                "&redirect_uri=" + URLEncoder.encode(wechatConfig.getRedirectUri(), StandardCharsets.UTF_8) +
                "&response_type=code" +
                "&scope=snsapi_userinfo" +
                "&state=" + state +
                "#wechat_redirect";

        log.info("生成微信登录URL: {}", url);
        return url;
    }

    @Override
    public WechatLoginResultDTO handleCallback(String code, String state) {
        try {
            log.info("处理微信回调, code: {}, state: {}", code, state);

            // 1. 使用code获取access_token
            WechatAccessTokenDTO tokenDTO = getAccessToken(code);
            if (tokenDTO.getErrcode() != null) {
                log.error("获取access_token失败: {}", tokenDTO.getErrmsg());
                return WechatLoginResultDTO.fail("微信授权失败: " + tokenDTO.getErrmsg());
            }

            // 2. 获取用户信息
            WechatUserInfoDTO userInfoDTO = getUserInfo(tokenDTO.getAccess_token(), tokenDTO.getOpenid());
            if (userInfoDTO.getErrcode() != null) {
                log.error("获取用户信息失败: {}", userInfoDTO.getErrmsg());
                return WechatLoginResultDTO.fail("获取用户信息失败: " + userInfoDTO.getErrmsg());
            }

            // 3. 查找或创建用户
            User user = findOrCreateUser(userInfoDTO);

            // 4. 更新最后登录时间
            user.setLastLoginTime(LocalDateTime.now());
            userMapper.updateUserWechatInfo(user);

            // 5. 使用SaToken登录（替换原来的JWT）
            StpUtil.login(user.getId());
            StpUtil.getSession().set("role", user.getRole());
            String token = StpUtil.getTokenValue();

            // 6. 判断是否为新用户
            boolean isNewUser = user.getCreateTime().isAfter(LocalDateTime.now().minusMinutes(1));

            return WechatLoginResultDTO.success(token, user, isNewUser);

        } catch (Exception e) {
            log.error("微信登录回调处理异常", e);
            return WechatLoginResultDTO.fail("系统异常: " + e.getMessage());
        }
    }

    private WechatAccessTokenDTO getAccessToken(String code) {
        String url = "https://api.weixin.qq.com/sns/oauth2/access_token" +
                "?appid=" + wechatConfig.getAppId() +
                "&secret=" + wechatConfig.getAppSecret() +
                "&code=" + code +
                "&grant_type=authorization_code";

        ResponseEntity<WechatAccessTokenDTO> response = restTemplate.getForEntity(url, WechatAccessTokenDTO.class);
        return response.getBody();
    }

    private WechatUserInfoDTO getUserInfo(String accessToken, String openId) {
        String url = "https://api.weixin.qq.com/sns/userinfo" +
                "?access_token=" + accessToken +
                "&openid=" + openId +
                "&lang=zh_CN";

        ResponseEntity<WechatUserInfoDTO> response = restTemplate.getForEntity(url, WechatUserInfoDTO.class);
        return response.getBody();
    }

    private User findOrCreateUser(WechatUserInfoDTO wechatUser) {
        // 查找现有用户
        User user = userMapper.getUserByOpenId(wechatUser.getOpenid());

        if (user == null) {
            // 创建新用户
            user = new User();
            user.setOpenId(wechatUser.getOpenid());
            user.setUnionId(wechatUser.getUnionid());
            user.setNickname(wechatUser.getNickname());
            user.setAvatar(wechatUser.getHeadimgurl());
            user.setUsername(generateUsername(wechatUser.getOpenid()));
            user.setEmail(""); // 微信登录可以没有邮箱
            user.setRole(RoleConstant.USER); // 使用常量
            user.setStatus(1);
            user.setLastLoginTime(LocalDateTime.now());
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());

            userMapper.insertWechatUser(user);
            log.info("创建新微信用户: {}", user.getUsername());
        } else {
            // 更新用户信息
            user.setNickname(wechatUser.getNickname());
            user.setAvatar(wechatUser.getHeadimgurl());
            user.setUnionId(wechatUser.getUnionid());
            log.info("更新微信用户信息: {}", user.getUsername());
        }

        return user;
    }

    private String generateUsername(String openId) {
        return "wx_" + openId.substring(0, 8);
    }
}