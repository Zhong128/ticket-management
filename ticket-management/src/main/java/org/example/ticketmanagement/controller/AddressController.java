package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.UserAddressDTO;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.service.UserAddressService;
import org.example.ticketmanagement.vo.UserAddressVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api/address/user")
@Tag(name = "收货管理", description = "用户收货地址管理相关接口")
public class AddressController {

    @Autowired
    private UserAddressService userAddressService;

    /**
     * 获取当前用户的所有收货地址
     */
    @Operation(summary = "获取收货地址列表", tags = {"收货管理"})
    @GetMapping
    @SaCheckLogin
    public Result<List<UserAddressVO>> getUserAddresses(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        log.info("获取用户收货地址，用户ID: {}", userId);

        try {
            List<UserAddressVO> addresses = userAddressService.listUserAddressesByUserId(userId);
            return Result.success(addresses);
        } catch (IllegalArgumentException e) {
            log.warn("参数错误: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("获取收货地址列表失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取收货地址列表失败: {}", e.getMessage(), e);
            return Result.error("获取地址列表失败");
        }
    }

    /**
     * 获取默认收货地址
     */
    @Operation(summary = "获取默认地址", tags = {"收货管理"})
    @GetMapping("/default")
    @SaCheckLogin
    public Result<UserAddressVO> getDefaultAddress(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        log.info("获取默认收货地址，用户ID: {}", userId);

        try {
            UserAddressVO defaultAddress = userAddressService.getDefaultAddress(userId);
            return Result.success(defaultAddress);
        } catch (RuntimeException e) {
            log.warn("获取默认地址失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取默认地址失败: {}", e.getMessage(), e);
            return Result.error("获取默认地址失败");
        }
    }

    /**
     * 根据ID获取收货地址详情
     */
    @Operation(summary = "根据ID获取收货地址", tags = {"收货管理"})
    @GetMapping("/{id}")
    @SaCheckLogin
    public Result<UserAddressVO> getUserAddressById(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        log.info("获取收货地址详情，地址ID: {}, 用户ID: {}", id, userId);

        try {
            // 验证地址所有权
            if (!userAddressService.validateAddressOwnership(id, userId)) {
                return Result.error("收货地址不存在或无权访问");
            }

            UserAddressVO address = userAddressService.getUserAddressById(id);
            if (address == null) {
                return Result.error("收货地址不存在");
            }

            return Result.success(address);
        } catch (IllegalArgumentException e) {
            log.warn("参数错误: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("获取收货地址详情失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取收货地址详情失败: {}", e.getMessage(), e);
            return Result.error("获取地址详情失败");
        }
    }

    /**
     * 新增收货地址
     */
    @Operation(summary = "新增收货地址", tags = {"收货管理"})
    @PostMapping
    @SaCheckLogin
    public Result<Void> addUserAddress(@Valid @RequestBody UserAddressDTO userAddressDTO, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        log.info("新增收货地址，用户ID: {}", userId);

        try {
            // 设置当前用户ID
            userAddressDTO.setUserId(userId);

            boolean success = userAddressService.addUserAddress(userAddressDTO);
            if (success) {
                return Result.success("地址添加成功");
            } else {
                return Result.error("地址添加失败");
            }
        } catch (IllegalArgumentException e) {
            log.warn("参数错误: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("新增收货地址失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("新增收货地址失败: {}", e.getMessage(), e);
            return Result.error("地址添加失败");
        }
    }

    /**
     * 修改收货地址
     */
    @Operation(summary = "修改收货地址", tags = {"收货管理"})
    @PutMapping("/{id}")
    @SaCheckLogin
    public Result<Void> updateUserAddress(@PathVariable Long id,
                                          @Valid @RequestBody UserAddressDTO userAddressDTO,
                                          HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        log.info("修改收货地址，地址ID: {}, 用户ID: {}", id, userId);

        try {
            // 验证地址所有权
            if (!userAddressService.validateAddressOwnership(id, userId)) {
                return Result.error("收货地址不存在或无权操作");
            }

            boolean success = userAddressService.updateUserAddressById(id, userAddressDTO);
            if (success) {
                return Result.success("地址修改成功");
            } else {
                return Result.error("地址修改失败");
            }
        } catch (IllegalArgumentException e) {
            log.warn("参数错误: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("修改收货地址失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("修改收货地址失败: {}", e.getMessage(), e);
            return Result.error("地址修改失败");
        }
    }

    /**
     * 设置默认收货地址
     */
    @Operation(summary = "设置默认收货地址", tags = {"收货管理"})
    @PutMapping("/{id}/default")
    @SaCheckLogin
    public Result<Void> setDefaultAddress(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        log.info("设置默认收货地址，地址ID: {}, 用户ID: {}", id, userId);

        try {
            boolean success = userAddressService.setDefaultAddress(userId, id);
            if (success) {
                return Result.success("设置默认地址成功");
            } else {
                return Result.error("设置默认地址失败");
            }
        } catch (IllegalArgumentException e) {
            log.warn("参数错误: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("设置默认地址失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("设置默认地址失败: {}", e.getMessage(), e);
            return Result.error("设置默认地址失败");
        }
    }

    /**
     * 删除收货地址
     */
    @Operation(summary = "删除收货地址", tags = {"收货管理"})
    @DeleteMapping("/{id}")
    @SaCheckLogin
    public Result<Void> deleteUserAddressById(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        log.info("删除收货地址，地址ID: {}, 用户ID: {}", id, userId);

        try {
            boolean success = userAddressService.deleteUserAddressById(id, userId);
            if (success) {
                return Result.success("地址删除成功");
            } else {
                return Result.error("地址删除失败");
            }
        } catch (IllegalArgumentException e) {
            log.warn("参数错误: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("删除收货地址失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("删除收货地址失败: {}", e.getMessage(), e);
            return Result.error("地址删除失败");
        }
    }
}
