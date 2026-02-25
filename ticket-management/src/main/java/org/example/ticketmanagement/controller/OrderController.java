// org/example/ticketmanagement/controller/OrderController.java
package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.RoleConstant;
import org.example.ticketmanagement.dto.OrderQueryDTO;
import org.example.ticketmanagement.dto.OrderUpdateDTO;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.service.OrderService;
import org.example.ticketmanagement.vo.OrderInfoVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "订单管理", description = "订单相关所有接口")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // ==================== 客户端接口（需要登录） ====================

    /**
     * 1. 分页查询当前用户的订单列表
     */
    @Operation(summary = "分页查询我的订单", tags = {"订单-客户端"})
    @GetMapping("/user/orders/page")
    @SaCheckLogin
    public Result<PageResult<OrderInfoVO>> getUserOrders(
            @Valid OrderQueryDTO queryDTO) {

        Long userId = StpUtil.getLoginIdAsLong();
        log.debug("用户分页查询订单列表: userId={}, page={}, size={}",
                userId, queryDTO.getPage(), queryDTO.getSize());

        PageResult<OrderInfoVO> pageResult = orderService.getUserOrders(queryDTO, userId);
        return Result.success("查询成功", pageResult);
    }

    /**
     * 2. 获取订单详情（客户端）
     */
    @Operation(summary = "获取订单详情", tags = {"订单-客户端"})
    @GetMapping("/user/orders/{orderId}")
    @SaCheckLogin
    public Result<OrderInfoVO> getOrderDetail(@PathVariable Long orderId) {

        Long userId = StpUtil.getLoginIdAsLong();
        log.debug("用户查询订单详情: orderId={}, userId={}", orderId, userId);

        OrderInfoVO orderVO = orderService.getOrderDetail(orderId, userId);
        return Result.success("查询成功", orderVO);
    }

    /**
     * 3. 取消订单（客户端）
     */
    @Operation(summary = "取消订单", tags = {"订单-客户端"})
    @PutMapping("/user/orders/{orderId}/cancel")
    @SaCheckLogin
    public Result<Void> cancelOrder(@PathVariable Long orderId) {

        Long userId = StpUtil.getLoginIdAsLong();
        log.info("用户取消订单: orderId={}, userId={}", orderId, userId);

        boolean success = orderService.userCancelOrder(orderId, userId);
        if (success) {
            return Result.success("订单取消成功");
        } else {
            return Result.error("订单取消失败");
        }
    }

    // ==================== 管理端接口（需要管理员权限） ====================

    /**
     * 1. 管理端分页查询订单列表
     */
    @Operation(summary = "管理端分页查询订单列表", tags = {"订单-管理端"})
    @GetMapping("/admin/orders/page")
    @SaCheckRole({RoleConstant.ADMIN, RoleConstant.SUPER_ADMIN})
    public Result<PageResult<OrderInfoVO>> adminQueryOrders(
            @Valid OrderQueryDTO queryDTO) {

        log.info("管理员分页查询订单列表: {}", queryDTO);

        PageResult<OrderInfoVO> pageResult = orderService.adminQueryOrders(queryDTO);
        return Result.success("查询成功", pageResult);
    }

    /**
     * 2. 管理端获取订单详情
     */
    @Operation(summary = "管理端获取订单详情", tags = {"订单-管理端"})
    @GetMapping("/admin/orders/{orderId}")
    @SaCheckRole({RoleConstant.ADMIN, RoleConstant.SUPER_ADMIN})
    public Result<OrderInfoVO> adminGetOrderDetail(@PathVariable Long orderId) {

        log.debug("管理员查询订单详情: orderId={}", orderId);

        OrderInfoVO orderVO = orderService.adminGetOrderDetail(orderId);
        return Result.success("查询成功", orderVO);
    }

    /**
     * 3. 管理端修改订单信息
     */
    @Operation(summary = "修改订单信息", tags = {"订单-管理端"})
    @PutMapping("/admin/orders/{orderId}")
    @SaCheckRole({RoleConstant.ADMIN, RoleConstant.SUPER_ADMIN})
    public Result<Void> adminUpdateOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderUpdateDTO updateDTO) {

        Long adminId = StpUtil.getLoginIdAsLong();
        log.info("管理员修改订单: orderId={}, adminId={}, updateDTO={}",
                orderId, adminId, updateDTO);

        boolean success = orderService.adminUpdateOrder(orderId, updateDTO, adminId);
        if (success) {
            return Result.success("订单更新成功");
        } else {
            return Result.error("订单更新失败");
        }
    }

    /**
     * 4. 批量取消订单（管理端）
     */
    @Operation(summary = "批量取消订单", tags = {"订单-管理端"})
    @PutMapping("/admin/orders/batch-cancel")
    @SaCheckRole({RoleConstant.ADMIN, RoleConstant.SUPER_ADMIN})
    public Result<Integer> batchCancelOrders(@RequestBody List<Long> orderIds) {

        log.info("管理员批量取消订单: count={}", orderIds.size());

        int successCount = orderService.batchCancelOrders(orderIds);
        return Result.success("成功取消" + successCount + "个订单", successCount);
    }

    /**
     * 5. 根据用户邮箱查询订单列表（管理端）
     */
    @Operation(summary = "根据用户邮箱查询订单", tags = {"订单-管理端"})
    @GetMapping("/admin/orders/user-orders")
    @SaCheckRole({RoleConstant.ADMIN, RoleConstant.SUPER_ADMIN})
    public Result<PageResult<OrderInfoVO>> getOrdersByUserEmail(
            @RequestParam String email,
            @Valid OrderQueryDTO queryDTO) {

        log.info("管理员根据邮箱查询用户订单: email={}, queryDTO={}", email, queryDTO);

        try {
            PageResult<OrderInfoVO> pageResult = orderService.getOrdersByUserEmail(email, queryDTO);
            return Result.success("查询成功", pageResult);
        } catch (IllegalArgumentException e) {
            log.warn("参数错误: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("根据邮箱查询订单失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("根据邮箱查询订单异常: {}", e.getMessage(), e);
            return Result.error("查询失败，请稍后重试");
        }
    }
}