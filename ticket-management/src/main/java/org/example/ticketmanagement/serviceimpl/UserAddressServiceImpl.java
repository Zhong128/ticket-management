package org.example.ticketmanagement.serviceimpl;

import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.UserAddressDTO;
import org.example.ticketmanagement.mapper.UserAddressMapper;
import org.example.ticketmanagement.pojo.UserAddress;
import org.example.ticketmanagement.service.UserAddressService;
import org.example.ticketmanagement.vo.UserAddressVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserAddressServiceImpl implements UserAddressService {

    @Autowired
    private UserAddressMapper userAddressMapper;

    @Override
    public List<UserAddressVO> listUserAddressesByUserId(Long userId) {
        log.debug("查询用户所有收货地址，用户ID: {}", userId);
        try {
            List<UserAddress> addresses = userAddressMapper.listUserAddressesByUserId(userId);
            return addresses.stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("查询用户收货地址失败，用户ID: {}", userId, e);
            throw new RuntimeException("查询收货地址失败");
        }
    }

    @Override
    public List<UserAddress> listUserAddressesByUserIdEntity(Long userId) {
        log.debug("查询用户所有收货地址（实体），用户ID: {}", userId);
        try {
            return userAddressMapper.listUserAddressesByUserId(userId);
        } catch (Exception e) {
            log.error("查询用户收货地址失败，用户ID: {}", userId, e);
            throw new RuntimeException("查询收货地址失败");
        }
    }

    @Override
    public UserAddressVO getUserAddressById(Long id) {
        log.debug("根据ID查询收货地址，地址ID: {}", id);
        try {
            UserAddress address = userAddressMapper.getUserAddressById(id);
            if (address == null) {
                log.debug("未找到收货地址，ID: {}", id);
                return null;
            }
            return convertToVO(address);
        } catch (Exception e) {
            log.error("根据ID查询收货地址失败，地址ID: {}", id, e);
            throw new RuntimeException("查询收货地址失败");
        }
    }


    @Override
    @Transactional
    public boolean addUserAddress(UserAddressDTO userAddressDTO) {
        log.info("新增收货地址，用户ID: {}", userAddressDTO.getUserId());

        try {
            // 设置默认值
            if (userAddressDTO.getIsDefault() == null) {
                userAddressDTO.setIsDefault(0); // 新增的地址默认不为默认地址
            }

            // 验证必要字段
            if (!validateAddressRequiredFields(userAddressDTO)) {
                log.warn("新增收货地址失败，必要字段缺失");
                throw new RuntimeException("收货地址必要字段缺失");
            }

            // 如果用户没有地址，则设为默认
            List<UserAddressVO> existingAddresses = listUserAddressesByUserId(userAddressDTO.getUserId());
            if (existingAddresses.isEmpty()) {
                userAddressDTO.setIsDefault(1);
                log.debug("用户首次添加地址，设为默认");
            } else {
                // 如果设置为默认地址，需要先清除其他默认地址
                if (userAddressDTO.getIsDefault() != null && userAddressDTO.getIsDefault() == 1) {
                    userAddressMapper.clearDefaultAddress(userAddressDTO.getUserId());
                    log.debug("清除原有默认地址标记");
                }
            }

            // DTO转Entity
            UserAddress userAddress = convertToEntity(userAddressDTO);
            userAddress.setCreateTime(LocalDateTime.now());
            userAddress.setUpdateTime(LocalDateTime.now());

            // 插入数据库
            userAddressMapper.addUserAddress(userAddress);
            log.info("新增收货地址成功，地址ID: {}", userAddress.getId());

            return true;

        } catch (RuntimeException e) {
            log.warn("新增收货地址失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("新增收货地址异常: {}", e.getMessage(), e);
            throw new RuntimeException("新增收货地址失败");
        }
    }

    @Override
    @Transactional
    public boolean updateUserAddressById(Long id, UserAddressDTO userAddressDTO) {
        log.info("修改收货地址，地址ID: {}", id);

        try {
            // 检查地址是否存在
            UserAddress existingAddress = userAddressMapper.getUserAddressById(id);
            if (existingAddress == null) {
                log.warn("修改收货地址失败，地址不存在，地址ID: {}", id);
                throw new RuntimeException("收货地址不存在");
            }

            // 设置默认值
            if (userAddressDTO.getIsDefault() == null) {
                userAddressDTO.setIsDefault(existingAddress.getIsDefault()); // 编辑时保留已有默认标记
            }

            // 验证必要字段
            if (!validateAddressRequiredFields(userAddressDTO)) {
                log.warn("修改收货地址失败，必要字段缺失");
                throw new RuntimeException("收货地址必要字段缺失");
            }

            // 如果设置为默认地址，需要先清除其他默认地址
            if (userAddressDTO.getIsDefault() != null && userAddressDTO.getIsDefault() == 1
                    && existingAddress.getIsDefault() != 1) {
                userAddressMapper.clearDefaultAddress(existingAddress.getUserId());
                log.debug("清除原有默认地址标记");
            }

            // DTO转Entity
            UserAddress userAddress = convertToEntity(userAddressDTO);
            userAddress.setId(id);
            userAddress.setUserId(existingAddress.getUserId());
            userAddress.setCreateTime(existingAddress.getCreateTime());
            userAddress.setUpdateTime(LocalDateTime.now());

            // 更新数据库
            userAddressMapper.updateUserAddressById(userAddress);
            log.info("修改收货地址成功，地址ID: {}", id);

            return true;

        } catch (RuntimeException e) {
            log.warn("修改收货地址失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("修改收货地址异常: {}", e.getMessage(), e);
            throw new RuntimeException("修改收货地址失败");
        }
    }

    @Override
    @Transactional
    public boolean deleteUserAddressById(Long id, Long userId) {
        log.info("删除收货地址，地址ID: {}, 用户ID: {}", id, userId);

        try {
            // 检查地址是否存在
            UserAddress existingAddress = userAddressMapper.getUserAddressById(id);
            if (existingAddress == null) {
                log.warn("删除收货地址失败，地址不存在，地址ID: {}", id);
                throw new RuntimeException("收货地址不存在");
            }

            // 验证地址所有权
            if (!existingAddress.getUserId().equals(userId)) {
                log.warn("删除收货地址失败，无权删除该地址，地址ID: {}, 用户ID: {}", id, userId);
                throw new RuntimeException("无权删除该地址");
            }

            // 检查是否为默认地址
            if (existingAddress.getIsDefault() != null && existingAddress.getIsDefault() == 1) {
                log.warn("删除收货地址失败，不能删除默认地址，地址ID: {}", id);
                throw new RuntimeException("不能删除默认地址");
            }

            // 删除地址
            userAddressMapper.deleteUserAddressById(id);
            log.info("删除收货地址成功，地址ID: {}", id);

            return true;

        } catch (RuntimeException e) {
            log.warn("删除收货地址失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("删除收货地址异常: {}", e.getMessage(), e);
            throw new RuntimeException("删除收货地址失败");
        }
    }

    @Override
    @Transactional
    public boolean setDefaultAddress(Long userId, Long addressId) {
        log.info("设置默认收货地址，用户ID: {}, 地址ID: {}", userId, addressId);

        try {
            // 检查地址是否存在且属于用户
            UserAddress existingAddress = userAddressMapper.getUserAddressById(addressId);
            if (existingAddress == null) {
                log.warn("设置默认收货地址失败，地址不存在，地址ID: {}", addressId);
                throw new RuntimeException("收货地址不存在");
            }

            if (!existingAddress.getUserId().equals(userId)) {
                log.warn("设置默认收货地址失败，无权操作该地址，地址ID: {}, 用户ID: {}", addressId, userId);
                throw new RuntimeException("无权操作该地址");
            }

            // 先清除该用户的所有默认地址标记
            userAddressMapper.clearDefaultAddress(userId);
            log.debug("清除默认地址标记");

            // 设置新的默认地址
            userAddressMapper.setDefaultAddress(addressId);
            log.info("设置默认地址成功，地址ID: {}", addressId);

            return true;

        } catch (RuntimeException e) {
            log.warn("设置默认收货地址失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("设置默认收货地址异常: {}", e.getMessage(), e);
            throw new RuntimeException("设置默认收货地址失败");
        }
    }

    @Override
    public UserAddressVO getDefaultAddress(Long userId) {
        log.debug("获取用户默认地址，用户ID: {}", userId);

        try {
            List<UserAddress> addresses = userAddressMapper.listUserAddressesByUserId(userId);
            UserAddress defaultAddress = addresses.stream()
                    .filter(address -> address.getIsDefault() != null && address.getIsDefault() == 1)
                    .findFirst()
                    .orElse(null);

            return defaultAddress != null ? convertToVO(defaultAddress) : null;
        } catch (Exception e) {
            log.error("获取默认地址失败，用户ID: {}", userId, e);
            throw new RuntimeException("获取默认地址失败");
        }
    }

    @Override
    public boolean validateAddressOwnership(Long addressId, Long userId) {
        log.debug("验证地址所有权，地址ID: {}, 用户ID: {}", addressId, userId);

        try {
            UserAddress address = userAddressMapper.getUserAddressById(addressId);
            return address != null && address.getUserId().equals(userId);
        } catch (Exception e) {
            log.error("验证地址所有权失败，地址ID: {}, 用户ID: {}", addressId, userId, e);
            throw new RuntimeException("验证地址所有权失败");
        }
    }

    /**
     * 验证地址必要字段
     */
    private boolean validateAddressRequiredFields(UserAddressDTO addressDTO) {
        return StringUtils.hasText(addressDTO.getReceiverName()) &&
                StringUtils.hasText(addressDTO.getReceiverPhone()) &&
                StringUtils.hasText(addressDTO.getProvince()) &&
                StringUtils.hasText(addressDTO.getCity()) &&
                StringUtils.hasText(addressDTO.getDetailAddress());
    }

    /**
     * DTO转Entity
     */
    private UserAddress convertToEntity(UserAddressDTO dto) {
        UserAddress entity = new UserAddress();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    /**
     * Entity转VO
     */
    private UserAddressVO convertToVO(UserAddress entity) {
        if (entity == null) {
            return null;
        }
        
        UserAddressVO vo = new UserAddressVO();
        
        //复制基本属性
        BeanUtils.copyProperties(entity, vo);
        
        //拼接完整地址
        String fullAddress = buildFullAddress(
            entity.getProvince(),
            entity.getCity(),
            entity.getDistrict(),
            entity.getDetailAddress()
        );
        vo.setFullAddress(fullAddress);
        
        //格式化时间（如果 VO 中有这个字段）
        if (entity.getCreateTime() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            vo.setFormattedCreateTime(entity.getCreateTime().format(formatter));
        }
        
        return vo;
    }

    /**
     * 拼接完整地址
     */
    private String buildFullAddress(String province, String city, 
                                    String district, String detailAddress) {
        StringBuilder sb = new StringBuilder();
        if (province != null) sb.append(province);
        if (city != null) sb.append(city);
        if (district != null) sb.append(district);
        if (detailAddress != null) sb.append(detailAddress);
        return sb.toString();
    }

}