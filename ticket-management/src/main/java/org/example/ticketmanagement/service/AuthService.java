// org/example/ticketmanagement/service/AuthService.java
package org.example.ticketmanagement.service;

import org.example.ticketmanagement.pojo.LoginInfo;
import org.example.ticketmanagement.pojo.User;

/**
 * 认证服务接口
 */
public interface AuthService {
    /**
     * 通用登录方法
     */
    LoginInfo login(User user);

    /**
     * 用户登录（验证角色为USER）
     */
    LoginInfo userLogin(User user);

    /**
     * 管理员登录（使用用户名和密码）
     */
    LoginInfo adminLoginByUsername(String username, String password);


    /**
     * 退出登录
     */
    void logout(String token);

    /**
     * 用户注册
     */
    void register(User user);

    /**
     * 登录或注册（合并接口）
     * 用户存在则登录，不存在则注册并登录
     */
    LoginInfo loginOrRegister(User user);

    /**
     * 通过验证码完成注册
     */
    LoginInfo completeRegistrationWithCode(String email);
}
