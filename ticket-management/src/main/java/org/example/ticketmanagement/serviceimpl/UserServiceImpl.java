package org.example.ticketmanagement.serviceimpl;

import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.AdminUserDTO;
import org.example.ticketmanagement.constant.OrderConstant;
import org.example.ticketmanagement.mapper.OrderInfoMapper;
import org.example.ticketmanagement.mapper.UserAddressMapper;
import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.pojo.OrderInfo;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.pojo.User;
import org.example.ticketmanagement.pojo.UserAddress;
import org.example.ticketmanagement.pojo.UserQuery;
import org.example.ticketmanagement.service.OrderService;
import org.example.ticketmanagement.service.UserService;
import org.example.ticketmanagement.dto.UserDTO;
import org.example.ticketmanagement.dto.UserProfileDTO;
import org.example.ticketmanagement.vo.UserVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private UserAddressMapper userAddressMapper;

    @Autowired
    private OrderService orderService;

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
            if (isAdmin(existingUser)) {
                log.warn("删除用户失败，不能删除管理员账户，ID: {}", id);
                throw new RuntimeException("不能删除管理员账户");
            }

            // 检查用户是否有未完成的订单等业务约束
            checkUserBusinessConstraints(id);

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
    public boolean updateProfile(Long userId, UserProfileDTO profile) {
        User user = userMapper.getUserById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 复用原有唯一性约束，资料写入只包含提交的字段。
        if (StringUtils.hasText(profile.getUsername())
                && !profile.getUsername().equals(user.getUsername())) {
            User conflict = userMapper.getUserByUsername(profile.getUsername());
            if (conflict != null && !conflict.getId().equals(userId)) {
                throw new RuntimeException("用户名已存在");
            }
        }
        if (StringUtils.hasText(profile.getEmail())
                && !profile.getEmail().equals(user.getEmail())) {
            User conflict = userMapper.getUserByEmail(profile.getEmail());
            if (conflict != null && !conflict.getId().equals(userId)) {
                throw new RuntimeException("邮箱已存在");
            }
        }
        if (StringUtils.hasText(profile.getPhone())
                && !profile.getPhone().equals(user.getPhone())) {
            User conflict = userMapper.getUserByPhone(profile.getPhone());
            if (conflict != null && !conflict.getId().equals(userId)) {
                throw new RuntimeException("手机号已存在");
            }
        }

        // 具名Mapper只更新资料列，不能把旧密码或账户状态快照写回。
        return userMapper.updateProfile(userId, profile, LocalDateTime.now()) == 1;
    }

    private boolean isAdmin(User user) {
        return "ADMIN".equals(user.getRole()) || "SUPER_ADMIN".equals(user.getRole());
    }

    @Override
    @Transactional
    public boolean updateAdminProfile(Long userId, AdminUserDTO profile) {
        // 管理员账户不能通过普通用户管理入口修改。
        User target = userMapper.getUserById(userId);
        if (target == null || isAdmin(target)) {
            throw new IllegalArgumentException("用户不存在或不能修改管理员账户");
        }
        validateProfileUnique(userId, profile, target);
        return userMapper.updateAdminProfile(userId, profile, LocalDateTime.now()) == 1;
    }

    private void validateProfileUnique(Long id, UserProfileDTO profile, User user) {
        if (StringUtils.hasText(profile.getUsername()) && !profile.getUsername().equals(user.getUsername())) {
            User conflict = userMapper.getUserByUsername(profile.getUsername());
            if (conflict != null && !id.equals(conflict.getId())) {
                throw new IllegalArgumentException("用户名已存在");
            }
        }
        if (StringUtils.hasText(profile.getEmail()) && !profile.getEmail().equals(user.getEmail())) {
            User conflict = userMapper.getUserByEmail(profile.getEmail());
            if (conflict != null && !id.equals(conflict.getId())) {
                throw new IllegalArgumentException("邮箱已存在");
            }
        }
        if (StringUtils.hasText(profile.getPhone()) && !profile.getPhone().equals(user.getPhone())) {
            User conflict = userMapper.getUserByPhone(profile.getPhone());
            if (conflict != null && !id.equals(conflict.getId())) {
                throw new IllegalArgumentException("手机号已存在");
            }
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
    public PageResult<UserVO> listUsers(UserQuery query) {
        log.debug("分页查询用户，参数：{}", query);

        try {
            // 验证分页参数
            if (!query.validate()) {
                log.warn("分页查询用户失败，分页参数不合法：{}", query);
                throw new IllegalArgumentException("分页参数不合法");
            }

            // 查询数据
            List<User> users = userMapper.selectUsersByPage(query);

            // 查询总数
            Long total = userMapper.countUsers(query);

            // 计算有效页码
            int maxPage = (int) Math.ceil((double) total / query.getSize());
            if (query.getPage() > maxPage && maxPage > 0) {
                query.setPage(maxPage);
                users = userMapper.selectUsersByPage(query);
            }

            log.debug("查询到用户总数：{}, 当前页数量：{}", total, users.size());

            List<UserVO> voList = users.stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());

            return PageResult.success(voList, total, query);

        } catch (IllegalArgumentException e) {
            log.warn("分页查询用户参数错误：{}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("分页查询用户失败：{}", e.getMessage(), e);
            throw new RuntimeException("查询用户列表失败");
        }
    }

    /**
     * 将 User 实体转换为 UserVO
     */
    private UserVO convertToVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
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
                } else if (isAdmin(user)) {
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

            // 检查用户是否有未完成的订单等业务约束
            for (Long userId : ids) {
                checkUserBusinessConstraints(userId);
            }

            // 批量删除 - 采用for循环
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

        if (status == null || (status != 0 && status != 1)) {
            log.warn("批量修改用户状态失败，状态值必须是0或1，当前值: {}", status);
            throw new IllegalArgumentException("状态值必须是0或1");
        }

        log.info("批量修改用户状态，ID列表: {}, 状态: {}", ids, status);

        try {
            // 检查所有用户是否存在或者否是管理员账户，如果是给出警告，不能修改
            List<String> notExistIds = new ArrayList<>();
            List<String> adminIds = new ArrayList<>();

            for (Long id : ids) {
                User user = userMapper.getUserById(id);
                if (user == null) {
                    notExistIds.add(String.valueOf(id));
                } else if (isAdmin(user)) {
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

            // 批量更新状态 -采用增强for循环
            int updatedCount = 0;
            for (Long id : ids) {
                User user = userMapper.getUserById(id);
                if (user != null) {
                    user.setStatus(status);
                    user.setUpdateTime(LocalDateTime.now());
                    userMapper.updateOrdinaryUserStatus(id, status, LocalDateTime.now());
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
    @Transactional
    public boolean updateUserStatus(Long userId, Integer status) {
        log.info("修改用户状态，用户ID: {}, 状态: {}", userId, status);

        try {
            if (status == null || (status != 0 && status != 1)) {
                log.warn("修改用户状态失败，状态值必须是0或1，当前值: {}", status);
                throw new IllegalArgumentException("状态值必须是0或1");
            }

            User user = userMapper.getUserById(userId);
            if (user == null) {
                log.warn("修改用户状态失败，用户不存在，用户ID: {}", userId);
                throw new RuntimeException("用户不存在");
            }

            // 防止禁用管理员
            if (isAdmin(user)) {
                log.warn("修改用户状态失败，不能禁用管理员账户，用户ID: {}", userId);
                throw new RuntimeException("不能禁用管理员账户");
            }

            user.setStatus(status);
            user.setUpdateTime(LocalDateTime.now());

            userMapper.updateOrdinaryUserStatus(userId, status, LocalDateTime.now());
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

            // 新密码长度必须与登录接口保持一致，避免改密后无法登录。
            if (newPassword.length() < 6 || newPassword.length() > 20) {
                throw new IllegalArgumentException("新密码长度必须在6-20位之间");
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

            // 只写入加密密码，不把校验时读取的旧状态和资料快照写回。
            String encodedPassword = passwordEncoder.encode(newPassword);
            if (userMapper.updatePassword(userId, encodedPassword, LocalDateTime.now()) != 1) {
                throw new RuntimeException("修改密码失败");
            }
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

    /**
     * 检查用户业务约束（删除前检查）
     * @param userId 用户ID
     * @throws RuntimeException 如果存在业务约束则抛出异常
     */
    private void checkUserBusinessConstraints(Long userId) {
        log.debug("检查用户业务约束，用户ID: {}", userId);
        
        try {
            // 1. 检查未完成的订单
            List<OrderInfo> unfinishedOrders = orderInfoMapper.selectByUserId(userId);
            if (unfinishedOrders != null && !unfinishedOrders.isEmpty()) {
                // 过滤出未完成的订单状态（待支付、已支付等）
                long unfinishedCount = unfinishedOrders.stream()
                        .filter(order -> order.getOrderStatus() != null && 
                                (order.getOrderStatus().equals(OrderConstant.ORDER_STATUS_PENDING) || 
                                 order.getOrderStatus().equals(OrderConstant.ORDER_STATUS_PAID)))
                        .count();
                
                if (unfinishedCount > 0) {
                    log.warn("删除用户失败，用户有未完成的订单，用户ID: {}, 未完成订单数: {}", userId, unfinishedCount);
                    throw new RuntimeException(String.format("用户有%d个未完成的订单，请先处理完订单再删除用户", unfinishedCount));
                }
            }

            // 3. 检查用户收货地址
            List<UserAddress> userAddresses = userAddressMapper.listUserAddressesByUserId(userId);
            if (userAddresses != null && !userAddresses.isEmpty()) {
                log.debug("用户有{}个收货地址，将一并删除", userAddresses.size());
                // 收货地址可以随用户一起删除，这里只是记录日志
            }
            
            // 4. 其他业务约束检查可以在这里添加
            // 例如：检查是否有未完成的售后服务、退款申请等
            
            log.debug("用户业务约束检查通过，用户ID: {}", userId);
            
        } catch (RuntimeException e) {
            // 重新抛出业务异常
            throw e;
        } catch (Exception e) {
            log.error("检查用户业务约束异常，用户ID: {}", userId, e);
            throw new RuntimeException("检查用户业务约束失败");
        }
    }

}