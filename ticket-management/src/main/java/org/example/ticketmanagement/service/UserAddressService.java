package org.example.ticketmanagement.service;

import org.example.ticketmanagement.dto.UserAddressDTO;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.pojo.UserAddress;
import org.example.ticketmanagement.vo.UserAddressVO;

import java.util.List;

public interface UserAddressService {
    /**
     * 根据用户id查询所有收货地址列表
     */
    List<UserAddressVO> listUserAddressesByUserId(Long userId);

    /**
     * 根据用户id查询所有收货地址列表（返回实体对象）
     */
    List<UserAddress> listUserAddressesByUserIdEntity(Long userId);

    /**
     * 根据id查询收货地址
     */
    UserAddressVO getUserAddressById(Long id);

    /**
     * 新增收货地址
     */
    boolean addUserAddress(UserAddressDTO userAddressDTO);

    /**
     * 根据id修改收货地址
     */
    boolean updateUserAddressById(Long id, UserAddressDTO userAddressDTO);

    /**
     * 根据id删除收货地址
     */
    boolean deleteUserAddressById(Long id, Long userId);

    /**
     * 设置默认收货地址
     */
    boolean setDefaultAddress(Long userId, Long addressId);

    /**
     * 获取用户默认地址
     */
    UserAddressVO getDefaultAddress(Long userId);

    /**
     * 验证地址是否属于用户
     */
    boolean validateAddressOwnership(Long addressId, Long userId);
}