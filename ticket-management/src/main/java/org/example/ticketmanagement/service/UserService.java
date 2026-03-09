package org.example.ticketmanagement.service;

import org.example.ticketmanagement.dto.UserDTO;
import org.example.ticketmanagement.pojo.*;
import org.example.ticketmanagement.vo.UserVO;

import java.util.List;

public interface UserService {
    /**
     * 根据id查询用户
     */
    User getUserById(Long id);

    /**
     * 根据id删除用户
     */
    boolean deleteUserById(Long id);

    /**
     * 修改用户
     */
    boolean updateUser(User user);

    /**
     * 分页查询用户列表（管理端）
     * @param query 查询参数
     * @return 分页结果（UserDTO）
     */
    PageResult<UserVO> listUsers(UserQuery query);

    /**
     * 批量删除用户
     */
    boolean deleteUsersByIds(List<Long> ids);

    /**
     * 批量修改用户状态
     */
    boolean updateUsersStatus(List<Long> ids, Integer status);


    /**
     * 修改用户状态（带校验）
     */
    boolean updateUserStatus(Long userId, Integer status);

    /**
     * 修改密码（带旧密码验证）
     */
    boolean changePassword(Long userId, String oldPassword, String newPassword);
}