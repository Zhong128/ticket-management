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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WechatServiceImpl implements WechatService {

    @Autowired
    private WechatConfig wechatConfig;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String WECHAT_STATE_PREFIX = "wechat:state:";
    private static final int STATE_EXPIRE_MINUTES = 5;

    @Override
    public String getLoginQrCode() {
        // 生成state参数防止CSRF
        String state = UUID.randomUUID().toString().replace("-", "");

        //保存state到Redis
        redisTemplate.opsForValue().set(
                WECHAT_STATE_PREFIX + state,
                "1",
                STATE_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );

        String url = "https://open.weixin.qq.com/connect/qrconnect" +
                "?appid=" + wechatConfig.getAppId() +
                "&redirect_uri=" + URLEncoder.encode(wechatConfig.getRedirectUri(), StandardCharsets.UTF_8) +
                "&response_type=code" +
                "&scope=snsapi_login" +  // 扫码登录用这个scope
                "&state=" + state +
                "#wechat_redirect";

        log.info("生成微信登录URL: {}", url);
        return url;
    }

    @Override
    public WechatLoginResultDTO handleCallback(String code, String state) {
        try {
            log.info("处理微信回调, code: {}, state: {}", code, state);

            // 1. 验证state
            if (!validateState(state)) {
                return WechatLoginResultDTO.fail("非法请求，state无效");
            }

            // 2. 使用code获取access_token
            WechatAccessTokenDTO tokenDTO = getAccessToken(code);
            if (tokenDTO == null || tokenDTO.getErrcode() != null) {
                String errorMsg = tokenDTO != null ? tokenDTO.getErrmsg() : "获取token失败";
                log.error("获取access_token失败: {}", errorMsg);
                return WechatLoginResultDTO.fail("微信授权失败: " + errorMsg);
            }

            // 3. 获取用户信息
            WechatUserInfoDTO userInfoDTO = getUserInfo(tokenDTO.getAccess_token(), tokenDTO.getOpenid());
            if (userInfoDTO == null || userInfoDTO.getErrcode() != null) {
                String errorMsg = userInfoDTO != null ? userInfoDTO.getErrmsg() : "获取用户信息失败";
                log.error("获取用户信息失败: {}", errorMsg);
                return WechatLoginResultDTO.fail("获取用户信息失败: " + errorMsg);
            }

            // 4. 查找或创建用户
            User user = findOrCreateUser(userInfoDTO);

            // 5. 更新最后登录时间
            user.setLastLoginTime(LocalDateTime.now());

            userMapper.updateUserWechatInfo(user);

            // 6. 使用SaToken登录
            StpUtil.login(user.getId());
            StpUtil.getSession().set("role", user.getRole());
            String token = StpUtil.getTokenValue();

            // 7. 判断是否为新用户
            boolean isNewUser = user.getCreateTime().isAfter(LocalDateTime.now().minusMinutes(1));

            return WechatLoginResultDTO.success(token, user, isNewUser);

        } catch (Exception e) {
            log.error("微信登录回调处理异常", e);
            return WechatLoginResultDTO.fail("系统异常: " + e.getMessage());
        }
    }

    /**
     * 验证state
     */
    private boolean validateState(String state) {
        if (state == null) return false;

        String key = WECHAT_STATE_PREFIX + state;
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            redisTemplate.delete(key); // 一次性使用
            return true;
        }
        return false;
    }

    /**
     * 获取access_token
     */
    private WechatAccessTokenDTO getAccessToken(String code) {
        String url = "https://api.weixin.qq.com/sns/oauth2/access_token" +
                "?appid=" + wechatConfig.getAppId() +
                "&secret=" + wechatConfig.getAppSecret() +
                "&code=" + code +
                "&grant_type=authorization_code";

        ResponseEntity<WechatAccessTokenDTO> response = restTemplate.getForEntity(url, WechatAccessTokenDTO.class);
        return response.getBody();
    }

    /**
     * 获取用户信息
     */
    private WechatUserInfoDTO getUserInfo(String accessToken, String openId) {
        String url = "https://api.weixin.qq.com/sns/userinfo" +
                "?access_token=" + accessToken +
                "&openid=" + openId +
                "&lang=zh_CN";

        ResponseEntity<WechatUserInfoDTO> response = restTemplate.getForEntity(url, WechatUserInfoDTO.class);
        return response.getBody();
    }

    /**
     * 查找或创建用户
     */
    private User findOrCreateUser(WechatUserInfoDTO wechatUser) {
        // 查找现有用户
        User user = userMapper.getUserByOpenId(wechatUser.getOpenid());
        LocalDateTime now = LocalDateTime.now();

        if (user == null) {
            // 创建新用户
            user = new User();
            user.setOpenId(wechatUser.getOpenid());
            user.setUnionId(wechatUser.getUnionid());
            user.setNickname(wechatUser.getNickname());
            user.setAvatar(wechatUser.getHeadimgurl());
            user.setUsername(generateUsername(wechatUser.getOpenid()));
            user.setEmail(""); // 微信登录可以没有邮箱
            user.setRole(RoleConstant.USER);
            user.setStatus(1);
            user.setLastLoginTime(now);
            user.setCreateTime(now);
            user.setUpdateTime(now);

            userMapper.insertWechatUser(user);
            log.info("创建新微信用户: {}, id: {}", user.getUsername(), user.getId());
        } else {
            // 更新用户信息
            user.setNickname(wechatUser.getNickname());
            user.setAvatar(wechatUser.getHeadimgurl());
            user.setUnionId(wechatUser.getUnionid());

            log.info("更新微信用户信息: {}, id: {}", user.getUsername(), user.getId());
        }

        return user;
    }

    /**
     * 生成用户名
     */
    private String generateUsername(String openId) {
        // 取openId前8位，确保长度合适
        String shortOpenId = openId.length() > 8 ? openId.substring(0, 8) : openId;
        return "wx_" + shortOpenId;
    }
}