package org.example.ticketmanagement.serviceimpl;

import cn.dev33.satoken.stp.StpInterface;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.PermissionConstant;
import org.example.ticketmanagement.constant.RoleConstant;
import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.pojo.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SaToken 权限加载接口实现
 * 自定义权限验证，动态加载用户角色和权限,可用于后续细粒度的拓展
 */
@Slf4j
@Component
public class StpInterfaceImpl implements StpInterface {

    @Autowired
    private UserMapper userMapper;

    /**
     * 返回一个账号所拥有的权限码集合
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        log.debug("加载用户权限，用户ID: {}", loginId);

        List<String> permissionList = new ArrayList<>();

        try {
            Long userId = Long.parseLong(loginId.toString());

            // 使用您UserMapper中的方法 getUserById
            User user = userMapper.getUserById(userId);

            if (user == null) {
                log.warn("用户不存在，ID: {}", userId);
                return permissionList;
            }

            // 检查用户状态（1为启用，0为禁用）
            if (user.getStatus() != null && user.getStatus() == 0) {
                log.warn("用户已被禁用，ID: {}", userId);
                return permissionList; // 禁用用户无权限
            }

            String role = user.getRole();

            // 根据角色分配基础权限
            if (RoleConstant.SUPER_ADMIN.equals(role)) {
                // 超级管理员拥有所有权限
                permissionList.add(PermissionConstant.ALL_PERMISSIONS);
                log.debug("超级管理员 {} 拥有所有权限", userId);
            } else if (RoleConstant.ADMIN.equals(role)) {
                // 管理员权限
                permissionList.add(PermissionConstant.USER_VIEW);
                permissionList.add(PermissionConstant.USER_EDIT);
                permissionList.add(PermissionConstant.USER_ENABLE);
                permissionList.add(PermissionConstant.CITY_ALL); // 城市管理所有权限
                permissionList.add(PermissionConstant.SHOW_ALL); // 演出管理所有权限
                permissionList.add(PermissionConstant.ORDER_VIEW);
                permissionList.add(PermissionConstant.TICKET_VIEW);
                log.debug("管理员 {} 权限已加载", userId);
            } else if (RoleConstant.VIP.equals(role)) {
                // VIP用户权限
                permissionList.add(PermissionConstant.ORDER_CREATE);
                permissionList.add(PermissionConstant.ORDER_CANCEL);
                permissionList.add(PermissionConstant.TICKET_GRAB);
                permissionList.add(PermissionConstant.TICKET_VIP_GRAB);
                permissionList.add(PermissionConstant.TICKET_EARLY_ACCESS);
                permissionList.add(PermissionConstant.PAYMENT_CREATE);
                log.debug("VIP用户 {} 权限已加载", userId);
            } else if (RoleConstant.USER.equals(role)) {
                // 普通用户权限
                permissionList.add(PermissionConstant.ORDER_CREATE);
                permissionList.add(PermissionConstant.ORDER_CANCEL);
                permissionList.add(PermissionConstant.TICKET_GRAB);
                permissionList.add(PermissionConstant.PAYMENT_CREATE);
                log.debug("普通用户 {} 权限已加载", userId);
            } else if (RoleConstant.GUEST.equals(role)) {
                // 游客权限
                permissionList.add(PermissionConstant.PUBLIC_ACCESS);
                log.debug("游客 {} 权限已加载", userId);
            } else {
                // 默认给游客权限
                permissionList.add(PermissionConstant.PUBLIC_ACCESS);
                log.warn("未知角色 {}，分配游客权限", role);
            }

            // 这里可以添加从数据库动态加载权限的逻辑
            // 例如：从专门的权限表查询用户的额外权限
            // List<String> extraPermissions = loadExtraPermissionsFromDB(userId);
            // permissionList.addAll(extraPermissions);

        } catch (NumberFormatException e) {
            log.error("登录ID格式错误: {}", loginId, e);
        } catch (Exception e) {
            log.error("加载用户权限失败，用户ID: {}", loginId, e);
        }

        log.debug("用户 {} 权限列表: {}", loginId, permissionList);
        return permissionList;
    }

    /**
     * 返回一个账号所拥有的角色标识集合
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        log.debug("加载用户角色，用户ID: {}", loginId);

        List<String> roleList = new ArrayList<>();

        try {
            Long userId = Long.parseLong(loginId.toString());

            // 使用您UserMapper中的方法 getUserById
            User user = userMapper.getUserById(userId);

            if (user != null) {
                // 检查用户状态
                if (user.getStatus() != null && user.getStatus() == 0) {
                    log.warn("用户已被禁用，不分配角色，ID: {}", userId);
                    return roleList; // 禁用用户无角色
                }

                String role = user.getRole();
                if (role != null && !role.trim().isEmpty()) {
                    roleList.add(role);
                    if (RoleConstant.SUPER_ADMIN.equals(role)) {
                        roleList.add(RoleConstant.ADMIN);
                    }

                    // VIP用户同时拥有USER角色（VIP是USER的升级版）
                    if (RoleConstant.VIP.equals(role)) {
                        roleList.add(RoleConstant.USER);
                        log.debug("VIP用户 {} 同时拥有USER角色", userId);
                    }

                    // 管理员和超级管理员也同时拥有USER角色（便于访问基础功能）
                    if (RoleConstant.ADMIN.equals(role) || RoleConstant.SUPER_ADMIN.equals(role)) {
                        roleList.add(RoleConstant.USER);
                        log.debug("管理员 {} 同时拥有USER角色", userId);
                    }
                } else {
                    // 如果角色为空，默认为GUEST
                    roleList.add(RoleConstant.GUEST);
                    log.warn("用户 {} 角色为空，默认为GUEST", userId);
                }
            } else {
                log.warn("用户不存在，ID: {}", userId);
            }
        } catch (NumberFormatException e) {
            log.error("登录ID格式错误: {}", loginId, e);
            // 格式错误时返回GUEST角色
            roleList.add(RoleConstant.GUEST);
        } catch (Exception e) {
            log.error("加载用户角色失败，用户ID: {}", loginId, e);
        }

        log.debug("用户 {} 角色列表: {}", loginId, roleList);
        return roleList;
    }
}