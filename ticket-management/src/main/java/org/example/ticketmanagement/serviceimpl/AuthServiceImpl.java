// org/example/ticketmanagement/serviceimpl/AuthServiceImpl.java
package org.example.ticketmanagement.serviceimpl;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.RoleConstant;
import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.pojo.LoginInfo;
import org.example.ticketmanagement.pojo.User;
import org.example.ticketmanagement.service.AuthService;
import org.example.ticketmanagement.service.VerificationCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public LoginInfo login(User user) {
        User u = null;

        // 就只用邮箱登录
        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            u = userMapper.getUserByEmail(user.getEmail());

            // 验证密码（现在使用加密密码验证）
            if (u != null && !passwordEncoder.matches(user.getPassword(), u.getPassword())) {
                u = null; // 密码错误，置为空
            }
        }

        if (u != null && u.getStatus() == 1) {
            log.info("登录成功: {}", u.getUsername());

            // 更新最后登录时间
            u.setLastLoginTime(LocalDateTime.now());
            userMapper.updateUser(u);

            // 使用SaToken登录（替换原来的JWT）
            return doSaTokenLogin(u);
        } else if (u != null && u.getStatus() == 0) {
            throw new RuntimeException("用户已被禁用");
        }

        throw new RuntimeException("邮箱或密码错误");
    }

    @Override
    public LoginInfo userLogin(User user) {
        LoginInfo loginInfo = login(user);

        // 验证用户角色（现在使用常量）
        User u = userMapper.getUserById(loginInfo.getId());
        if (!RoleConstant.USER.equals(u.getRole())) {
            throw new RuntimeException("非用户角色，请使用用户端登录");
        }

        return loginInfo;
    }

    @Override
    public LoginInfo adminLogin(User user) {
        LoginInfo loginInfo = login(user);

        // 验证管理员角色（现在使用常量）
        User u = userMapper.getUserById(loginInfo.getId());
        if (!RoleConstant.ADMIN.equals(u.getRole()) &&
                !RoleConstant.SUPER_ADMIN.equals(u.getRole())) {
            throw new RuntimeException("非管理员角色，请使用管理端登录");
        }

        return loginInfo;
    }

    @Override
    public void logout(String token) {
        // 删除原来的TokenManager逻辑，直接使用SaToken的logout
        if (StpUtil.isLogin()) {
            StpUtil.logout();
            log.info("用户退出登录成功");
        }
    }

    @Override
    public void register(User user) {
        // 检查邮箱是否已存在
        User existingEmail = userMapper.getUserByEmail(user.getEmail());
        if (existingEmail != null) {
            throw new RuntimeException("邮箱已被注册");
        }

        // 设置默认值
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setStatus(1); // 默认启用状态
        user.setLastLoginTime(LocalDateTime.now());
        user.setRole(RoleConstant.USER); // 使用常量

        // 生成默认用户名（使用邮箱前缀）
        if (user.getUsername() == null || user.getUsername().isEmpty()) {
            String emailPrefix = user.getEmail().split("@")[0];
            user.setUsername(emailPrefix);
        }

        // 检查用户名是否已存在，如果存在则添加随机后缀
        User existingUsername = userMapper.getUserByUsername(user.getUsername());
        if (existingUsername != null) {
            user.setUsername(user.getUsername() + "_" + System.currentTimeMillis() % 1000);
        }

        // 加密密码
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        userMapper.registerUser(user);
    }

    @Override
    public LoginInfo loginOrRegister(User user) {
        log.info("登录/注册请求: email={}", user.getEmail());

        // 检查用户是否存在
        User existingUser = userMapper.getUserByEmail(user.getEmail());
        if (existingUser != null) {
            // 老用户，直接登录
            log.info("老用户登录: email={}", user.getEmail());

            // 构建登录用户对象
            User loginUser = new User();
            loginUser.setEmail(user.getEmail());
            loginUser.setPassword(user.getPassword());

            return userLogin(loginUser);
        } else {
            // 新用户，发送验证码并提示用户验证
            log.info("新用户注册，发送验证码: email={}", user.getEmail());

            // 设置用户基本信息
            if (user.getUsername() == null || user.getUsername().isEmpty()) {
                String emailPrefix = user.getEmail().split("@")[0];
                user.setUsername(emailPrefix);
            }

            if (user.getNickname() == null || user.getNickname().isEmpty()) {
                user.setNickname(user.getUsername());
            }

            // 发送验证码
            boolean sent = verificationCodeService.sendVerificationCode(user.getEmail());
            if (!sent) {
                throw new RuntimeException("验证码发送失败");
            }

            // 抛出特殊异常，提示前端需要验证码验证
            throw new RuntimeException("NEW_USER_NEED_VERIFICATION");
        }
    }

    @Override
    public LoginInfo completeRegistrationWithCode(String email) {
        log.info("完成验证码注册: email={}", email);

        // 检查用户是否已存在
        User existingUser = userMapper.getUserByEmail(email);
        if (existingUser != null) {
            throw new RuntimeException("用户已存在");
        }

        try {
            // 创建新用户
            User user = new User();
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode("")); // 验证码注册不需要密码，但设置空密码

            // 生成默认用户名
            String emailPrefix = email.split("@")[0];
            user.setUsername(emailPrefix);
            user.setNickname(emailPrefix);

            user.setRole(RoleConstant.USER);
            user.setStatus(1);
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            user.setLastLoginTime(LocalDateTime.now());

            // 执行注册
            register(user);
            log.info("新用户注册成功: email={}, username={}", email, user.getUsername());

            // 注册成功后登录
            LoginInfo loginInfo = doSaTokenLogin(user);
            loginInfo.setIsNewUser(true); // 标记为新用户

            return loginInfo;

        } catch (DuplicateKeyException e) {
            // 并发情况下，可能其他请求已经注册了该用户
            log.info("注册时发现用户已存在（并发情况），重新尝试登录");
            User existing = userMapper.getUserByEmail(email);
            return doSaTokenLogin(existing);
        } catch (Exception e) {
            log.error("注册失败: {}", e.getMessage(), e);
            throw new RuntimeException("注册失败: " + e.getMessage());
        }
    }

    /**
     * SaToken登录方法（内部公共方法）
     */
    private LoginInfo doSaTokenLogin(User user) {
        // 1. SaToken登录（自动生成token）
        StpUtil.login(user.getId());

        // 2. 设置Session信息
        StpUtil.getSession().set("userInfo", user);
        StpUtil.getSession().set("role", user.getRole());

        // 3. 获取token值
        String token = StpUtil.getTokenValue();

        // 4. 返回登录信息
        LoginInfo loginInfo = new LoginInfo(user.getId(), user.getUsername(), user.getEmail(), token);
        return loginInfo;
    }
}