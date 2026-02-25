package org.example.ticketmanagement.serviceimpl;

import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.pojo.User;
import org.example.ticketmanagement.pojo.UserQuery;
import org.example.ticketmanagement.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public User getUserById(Long id) {
        log.debug("根据ID查询用户，ID: {}", id);
        try {
            User user = userMapper.getUserById(id);
            if (user == null) {
                log.debug("未找到用户，ID: {}", id);
            }
            return user;
        } catch (Exception e) {
            log.error("根据ID查询用户失败，ID: {}", id, e);
            throw new RuntimeException("查询用户失败");
        }
    }


    @Override
    @Transactional
    public boolean deleteUserById(Long id) {
        log.info("删除用户，ID: {}", id);

        try {
            // 检查用户是否存在
            User existingUser = userMapper.getUserById(id);
            if (existingUser == null) {
                log.warn("删除用户失败，用户不存在，ID: {}", id);
                throw new RuntimeException("用户不存在");
            }

            // 检查是否为管理员（可选：防止删除管理员）
            if ("ADMIN".equals(existingUser.getRole())) {
                log.warn("删除用户失败，不能删除管理员账户，ID: {}", id);
                throw new RuntimeException("不能删除管理员账户");
            }

            // TODO: 检查用户是否有未完成的订单等业务约束

            userMapper.deleteUserById(id);
            log.info("删除用户成功，ID: {}", id);
            return true;

        } catch (RuntimeException e) {
            log.warn("删除用户失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("删除用户异常: {}", e.getMessage(), e);
            throw new RuntimeException("删除用户失败");
        }
    }

    @Override
    @Transactional
    public boolean updateUser(User user) {
        log.info("更新用户，ID: {}", user.getId());

        try {
            // 检查用户是否存在
            User existingUser = userMapper.getUserById(user.getId());
            if (existingUser == null) {
                log.warn("更新用户失败，用户不存在，ID: {}", user.getId());
                throw new RuntimeException("用户不存在");
            }

            // 校验用户名是否与其他用户冲突
            if (StringUtils.hasText(user.getUsername()) &&
                    !user.getUsername().equals(existingUser.getUsername())) {
                User userByUsername = userMapper.getUserByUsername(user.getUsername());
                if (userByUsername != null && !userByUsername.getId().equals(user.getId())) {
                    log.warn("更新用户失败，用户名已存在: {}", user.getUsername());
                    throw new RuntimeException("用户名已存在");
                }
            }

            // 校验邮箱是否与其他用户冲突
            if (StringUtils.hasText(user.getEmail()) &&
                    !user.getEmail().equals(existingUser.getEmail())) {
                User userByEmail = userMapper.getUserByEmail(user.getEmail());
                if (userByEmail != null && !userByEmail.getId().equals(user.getId())) {
                    log.warn("更新用户失败，邮箱已存在: {}", user.getEmail());
                    throw new RuntimeException("邮箱已存在");
                }
            }

            // 校验手机号是否与其他用户冲突
            if (StringUtils.hasText(user.getPhone()) &&
                    !user.getPhone().equals(existingUser.getPhone())) {
                User userByPhone = userMapper.getUserByPhone(user.getPhone());
                if (userByPhone != null && !userByPhone.getId().equals(user.getId())) {
                    log.warn("更新用户失败，手机号已存在: {}", user.getPhone());
                    throw new RuntimeException("手机号已存在");
                }
            }

            // 保留原始创建时间
            user.setCreateTime(existingUser.getCreateTime());
            user.setUpdateTime(LocalDateTime.now());

            // 如果不修改密码，保留原密码
            if (!StringUtils.hasText(user.getPassword())) {
                user.setPassword(existingUser.getPassword());
            }

            userMapper.updateUser(user);
            log.info("更新用户成功，ID: {}", user.getId());
            return true;

        } catch (RuntimeException e) {
            log.warn("更新用户失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("更新用户异常: {}", e.getMessage(), e);
            throw new RuntimeException("更新用户失败");
        }
    }


    @Override
    public PageResult<User> listUsers(UserQuery query) {
        log.debug("分页查询用户，参数: {}", query);

        try {
            // 验证分页参数
            if (!query.validate()) {
                log.warn("分页查询用户失败，分页参数不合法: {}", query);
                throw new IllegalArgumentException("分页参数不合法");
            }

            // 查询数据
            List<User> users = userMapper.selectUsersByPage(query);

            // 查询总数
            Long total = userMapper.countUsers(query);

            // 计算有效页码（防止请求超出范围的页码）
            int maxPage = (int) Math.ceil((double) total / query.getSize());
            if (query.getPage() > maxPage && maxPage > 0) {
                query.setPage(maxPage);
                users = userMapper.selectUsersByPage(query);
            }

            log.debug("查询到用户总数: {}, 当前页数量: {}", total, users.size());
            return new PageResult<>(users, total, query);

        } catch (IllegalArgumentException e) {
            log.warn("分页查询用户参数错误: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("分页查询用户失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询用户列表失败");
        }
    }

    @Override
    @Transactional
    public boolean deleteUsersByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            log.warn("批量删除用户失败，用户ID列表不能为空");
            throw new IllegalArgumentException("用户ID列表不能为空");
        }

        log.info("批量删除用户，ID列表: {}", ids);

        try {
            // 检查用户是否存在
            List<String> notExistIds = new ArrayList<>();
            List<String> adminIds = new ArrayList<>();

            for (Long id : ids) {
                User user = userMapper.getUserById(id);
                if (user == null) {
                    notExistIds.add(String.valueOf(id));
                } else if ("ADMIN".equals(user.getRole())) {
                    adminIds.add(String.valueOf(id));
                }
            }

            if (!notExistIds.isEmpty()) {
                log.warn("批量删除用户失败，部分用户不存在: {}", notExistIds);
                throw new RuntimeException("以下用户不存在: " + String.join(",", notExistIds));
            }

            if (!adminIds.isEmpty()) {
                log.warn("批量删除用户失败，不能删除管理员账户: {}", adminIds);
                throw new RuntimeException("不能删除管理员账户: " + String.join(",", adminIds));
            }

            // TODO: 检查用户是否有未完成的订单等业务约束

            // 批量删除 - 由于Mapper没有批量删除方法，我们循环删除
            int deletedCount = 0;
            for (Long id : ids) {
                userMapper.deleteUserById(id);
                deletedCount++;
            }

            log.info("批量删除用户成功，删除数量: {}", deletedCount);
            return true;

        } catch (IllegalArgumentException e) {
            log.warn("批量删除用户参数错误: {}", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.warn("批量删除用户失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("批量删除用户异常: {}", e.getMessage(), e);
            throw new RuntimeException("批量删除用户失败");
        }
    }

    @Override
    @Transactional
    public boolean updateUsersStatus(List<Long> ids, Integer status) {
        if (ids == null || ids.isEmpty()) {
            log.warn("批量修改用户状态失败，用户ID列表不能为空");
            throw new IllegalArgumentException("用户ID列表不能为空");
        }

        if (status != 0 && status != 1) {
            log.warn("批量修改用户状态失败，状态值必须是0或1，当前值: {}", status);
            throw new IllegalArgumentException("状态值必须是0或1");
        }

        log.info("批量修改用户状态，ID列表: {}, 状态: {}", ids, status);

        try {
            // 检查所有用户是否存在
            List<String> notExistIds = new ArrayList<>();
            List<String> adminIds = new ArrayList<>();

            for (Long id : ids) {
                User user = userMapper.getUserById(id);
                if (user == null) {
                    notExistIds.add(String.valueOf(id));
                } else if (status == 0 && "ADMIN".equals(user.getRole())) {
                    adminIds.add(String.valueOf(id));
                }
            }

            if (!notExistIds.isEmpty()) {
                log.warn("批量修改用户状态失败，部分用户不存在: {}", notExistIds);
                throw new RuntimeException("以下用户不存在: " + String.join(",", notExistIds));
            }

            if (!adminIds.isEmpty()) {
                log.warn("批量修改用户状态失败，不能禁用管理员账户: {}", adminIds);
                throw new RuntimeException("不能禁用管理员账户: " + String.join(",", adminIds));
            }

            // 批量更新状态 - 由于Mapper没有批量更新方法，我们循环更新
            int updatedCount = 0;
            for (Long id : ids) {
                User user = userMapper.getUserById(id);
                if (user != null) {
                    user.setStatus(status);
                    user.setUpdateTime(LocalDateTime.now());
                    userMapper.updateUser(user);
                    updatedCount++;
                }
            }

            log.info("批量修改用户状态成功，更新数量: {}", updatedCount);
            return true;

        } catch (IllegalArgumentException e) {
            log.warn("批量修改用户状态参数错误: {}", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.warn("批量修改用户状态失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("批量修改用户状态异常: {}", e.getMessage(), e);
            throw new RuntimeException("批量修改用户状态失败");
        }
    }

    @Override
    public boolean isUsernameExists(String username) {
        try {
            boolean exists = userMapper.getUserByUsername(username) != null;
            log.debug("检查用户名是否存在，username: {}, 结果: {}", username, exists);
            return exists;
        } catch (Exception e) {
            log.error("检查用户名是否存在失败，username: {}", username, e);
            throw new RuntimeException("检查用户名是否存在失败");
        }
    }

    @Override
    public boolean isEmailExists(String email) {
        try {
            boolean exists = userMapper.getUserByEmail(email) != null;
            log.debug("检查邮箱是否存在，email: {}, 结果: {}", email, exists);
            return exists;
        } catch (Exception e) {
            log.error("检查邮箱是否存在失败，email: {}", email, e);
            throw new RuntimeException("检查邮箱是否存在失败");
        }
    }


    @Override
    @Transactional
    public boolean updateUserStatus(Long userId, Integer status) {
        log.info("修改用户状态，用户ID: {}, 状态: {}", userId, status);

        try {
            if (status != 0 && status != 1) {
                log.warn("修改用户状态失败，状态值必须是0或1，当前值: {}", status);
                throw new IllegalArgumentException("状态值必须是0或1");
            }

            User user = userMapper.getUserById(userId);
            if (user == null) {
                log.warn("修改用户状态失败，用户不存在，用户ID: {}", userId);
                throw new RuntimeException("用户不存在");
            }

            // 防止禁用管理员
            if (status == 0 && "ADMIN".equals(user.getRole())) {
                log.warn("修改用户状态失败，不能禁用管理员账户，用户ID: {}", userId);
                throw new RuntimeException("不能禁用管理员账户");
            }

            user.setStatus(status);
            user.setUpdateTime(LocalDateTime.now());

            userMapper.updateUser(user);
            log.info("修改用户状态成功，用户ID: {}", userId);

            return true;

        } catch (IllegalArgumentException e) {
            log.warn("修改用户状态参数错误: {}", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.warn("修改用户状态失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("修改用户状态异常: {}", e.getMessage(), e);
            throw new RuntimeException("修改用户状态失败");
        }
    }

    @Override
    @Transactional
    public boolean changePassword(Long userId, String oldPassword, String newPassword) {
        log.info("修改密码，用户ID: {}", userId);

        try {
            if (!StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
                log.warn("修改密码失败，密码不能为空");
                throw new IllegalArgumentException("密码不能为空");
            }

            User user = userMapper.getUserById(userId);
            if (user == null) {
                log.warn("修改密码失败，用户不存在，用户ID: {}", userId);
                throw new RuntimeException("用户不存在");
            }


            // 注意应该是加密后的密码来匹配
            if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                log.warn("修改密码失败，原密码错误，用户ID: {}", userId);
                throw new RuntimeException("原密码错误");
            }

            // 新密码不能与旧密码相同
            if (oldPassword.equals(newPassword)) {
                log.warn("修改密码失败，新密码不能与原密码相同，用户ID: {}", userId);
                throw new RuntimeException("新密码不能与原密码相同");
            }

            // 更新密码（需要加密新密码）
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setUpdateTime(LocalDateTime.now());

            userMapper.updateUser(user);
            log.info("修改密码成功，用户ID: {}", userId);

            return true;

        } catch (IllegalArgumentException e) {
            log.warn("修改密码参数错误: {}", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.warn("修改密码失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("修改密码异常: {}", e.getMessage(), e);
            throw new RuntimeException("修改密码失败");
        }
    }
}