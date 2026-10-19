package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckDisable;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.RoleConstant;
import org.example.ticketmanagement.dto.TicketTierDTO;
import org.example.ticketmanagement.dto.TicketTierQueryDTO;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.service.TicketTierService;
import org.example.ticketmanagement.vo.StockStatisticsVO;
import org.example.ticketmanagement.vo.TicketStockVO;
import org.example.ticketmanagement.vo.TicketTierVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "票档管理", description = "票档管理相关接口")
public class TicketTierController {

    @Autowired
    private TicketTierService ticketTierService;


    // ==================== 公共接口（无需登录） ====================
    /**
     * 分页查询票档列表
     * 默认查询 status=1 的启用状态票档
     * 可选参数：
     * - eventId: 演出 ID
     * - sessionId: 场次 ID
     * - minPrice: 最低价格
     * - maxPrice: 最高价格
     */
    @Operation(summary = "分页查询票档列表", tags = {"票档 - 公共接口"})
    @GetMapping("/public/ticket-tiers/page")
    @SaIgnore
    public Result<PageResult<TicketTierVO>> getTicketTiersByPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice) {

        log.debug("分页查询票档，page: {}, size: {}, eventId: {}, sessionId: {}, minPrice: {}, maxPrice: {}",
                page, size, eventId, sessionId, minPrice, maxPrice);

        // 构建查询参数
        TicketTierQueryDTO queryDTO = new TicketTierQueryDTO();
        queryDTO.setPage(page);
        queryDTO.setSize(size);
        queryDTO.setEventId(eventId);
        queryDTO.setSessionId(sessionId);
        queryDTO.setStatus(1);  // 固定查询启用状态
        queryDTO.setMinPrice(minPrice);
        queryDTO.setMaxPrice(maxPrice);

        // 调用统一的分页查询方法
        PageResult<TicketTierVO> result = ticketTierService.queryTicketTiers(queryDTO);
        return Result.success("查询成功", result);
    }


    // ==================== 用户端接口（需要登录） ====================

    /**
     * 根据ID查询票档详情（用户端）
     */
    @Operation(summary = "获取票档详情", tags = {"票档-用户端"})
    @GetMapping("/user/ticket-tiers/{id}")
    @SaCheckLogin
    public Result<TicketTierVO> getTicketTierById(@PathVariable Long id) {
        log.debug("查询票档详情，ID: {}", id);

        TicketTierVO ticketTier = ticketTierService.getTicketTierById(id);
        if (ticketTier != null) {
            return Result.success("查询成功", ticketTier);
        } else {
            return Result.error("票档不存在");
        }
    }


    /**
     * 获取票档库存信息（客户端）
     * 参数：
     * - eventId: 演出 ID
     * - sessionId: 场次 ID
     */
    @Operation(summary = "获取票档库存信息", tags = {"票档 - 用户端"})
    @GetMapping("/user/ticket-tiers/stock")
    @SaCheckLogin
    public Result<TicketStockVO> getTicketStock(
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) Long sessionId) {

        if (sessionId != null) {
            log.debug("获取场次票档库存信息，sessionId: {}", sessionId);
            TicketStockVO stockInfo = ticketTierService.getStockInfoBySessionId(sessionId);
            if (stockInfo != null) {
                return Result.success("查询成功", stockInfo);
            } else {
                return Result.error("场次不存在");
            }
        } else if (eventId != null) {
            log.debug("获取演出票档库存信息，eventId: {}", eventId);
            TicketStockVO stockInfo = ticketTierService.getStockInfoByEventId(eventId);
            if (stockInfo != null) {
                return Result.success("查询成功", stockInfo);
            } else {
                return Result.error("演出不存在");
            }
        } else {
            return Result.error("请提供演出 ID 或场次 ID");
        }
    }

    /**
     * 检查是否已开票（客户端）
     * 参数：
     * - eventId: 演出ID
     * - sessionId: 场次ID
     */
    @Operation(summary = "检查是否已开票", tags = {"票档-用户端"})
    @GetMapping("/user/ticket-tiers/on-sale")
    @SaCheckLogin
    public Result<Boolean> checkOnSale(
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) Long sessionId) {

        if (sessionId != null) {
            log.debug("检查场次是否已开票，sessionId: {}", sessionId);
            boolean onSale = ticketTierService.isSessionOnSale(sessionId);
            return Result.success("查询成功", onSale);
        } else if (eventId != null) {
            log.debug("检查演出是否已开票，eventId: {}", eventId);
            boolean onSale = ticketTierService.isEventOnSale(eventId);
            return Result.success("查询成功", onSale);
        } else {
            return Result.error("请提供演出ID或场次ID");
        }
    }

    // ==================== 管理端接口（需要管理员权限） ====================

    /**
     *新增票档
     */
    @Operation(summary = "新增票档", tags = {"票档-管理端"})
    @PostMapping("/admin/ticket-tiers")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<Void> addTicketTier(@Valid @RequestBody TicketTierDTO ticketTierDTO) {
        log.info("收到新增票档请求: {}", ticketTierDTO.getTierName());

        boolean success = ticketTierService.addTicketTier(ticketTierDTO);
        if (success) {
            return Result.success("票档添加成功");
        } else {
            return Result.error("票档添加失败，请检查演出、场次是否存在，或价格/库存逻辑是否合理");
        }
    }

    /**
     * 根据ID删除票档
     */
    @Operation(summary = "删除票档", tags = {"票档-管理端"})
    @DeleteMapping("/admin/ticket-tiers/{id}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<Void> deleteTicketTier(@PathVariable Long id) {
        log.info("收到删除票档请求，ID: {}", id);

        boolean success = ticketTierService.deleteTicketTier(id);
        if (success) {
            return Result.success("票档删除成功");
        } else {
            return Result.error("票档删除失败，可能票档不存在");
        }
    }

    /**
     * 更新票档信息
     */
    @Operation(summary = "更新票档信息", tags = {"票档-管理端"})
    @PutMapping("/admin/ticket-tiers/{id}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<Void> updateTicketTier(@PathVariable Long id,
                                         @Valid @RequestBody TicketTierDTO ticketTierDTO) {
        log.info("收到更新票档请求，ID: {}", id);

        boolean success = ticketTierService.updateTicketTier(id, ticketTierDTO);
        if (success) {
            return Result.success("票档更新成功");
        } else {
            return Result.error("票档更新失败，可能票档不存在或数据不合法");
        }
    }

    /**
     * 根据ID查询票档详情（管理端）
     */
    @Operation(summary = "查询票档详情（管理端）", tags = {"票档-管理端"})
    @GetMapping("/admin/ticket-tiers/{id}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<TicketTierVO> AdminGetTicketTierById(@PathVariable Long id) {
        log.debug("查询票档详情，ID: {}", id);

        TicketTierVO ticketTier = ticketTierService.getTicketTierById(id);
        if (ticketTier != null) {
            return Result.success("查询成功", ticketTier);
        } else {
            return Result.error("票档不存在");
        }
    }

    /**
     * 分页查询票档列表（管理端）
     * 可选参数：
     * - eventId: 演出 ID
     * - sessionId: 场次 ID
     * - status: 状态 (0-禁用，1-启用)
     * - minPrice: 最低价格
     * - maxPrice: 最高价格
     */
    @Operation(summary = "分页查询票档列表(管理端)", tags = {"票档 - 管理端"})
    @GetMapping("/admin/ticket-tiers/page")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<PageResult<TicketTierVO>> getTicketTiersByPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice) {

        log.debug("管理端分页查询票档，page: {}, size: {}, eventId: {}, sessionId: {}, status: {}, minPrice: {}, maxPrice: {}",
                page, size, eventId, sessionId, status, minPrice, maxPrice);

        // 构建查询参数
        TicketTierQueryDTO queryDTO = new TicketTierQueryDTO();
        queryDTO.setPage(page);
        queryDTO.setSize(size);
        queryDTO.setEventId(eventId);
        queryDTO.setSessionId(sessionId);
        queryDTO.setStatus(status);
        queryDTO.setMinPrice(minPrice);
        queryDTO.setMaxPrice(maxPrice);

        // 调用统一的分页查询方法
        PageResult<TicketTierVO> result = ticketTierService.queryTicketTiers(queryDTO);
        return Result.success("查询成功", result);
    }

    /**
     * 获取库存统计信息（管理端）
     * 可选参数：
     * - eventId: 演出ID
     * - sessionId: 场次ID
     * - tierId: 票档ID
     */
    @Operation(summary = "获取库存统计信息", tags = {"票档-管理端"})
    @GetMapping("/admin/ticket-tiers/stock-statistics")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<StockStatisticsVO> getStockStatistics(
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Long tierId) {
        log.debug("获取库存统计，eventId: {}, sessionId: {}, tierId: {}", eventId, sessionId, tierId);

        StockStatisticsVO statistics = ticketTierService.getStockStatistics(eventId, sessionId, tierId);
        return Result.success("查询成功", statistics);
    }

    /**
     * 获取售罄票档列表（管理端）
     * 可选参数：
     * - eventId: 演出ID
     * - sessionId: 场次ID
     */
    @Operation(summary = "获取售罄票档列表", tags = {"票档-管理端"})
    @GetMapping("/admin/ticket-tiers/sold-out")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<List<StockStatisticsVO.TierStockDetail>> getSoldOutTiers(
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) Long sessionId) {
        log.debug("获取售罄票档列表，eventId: {}, sessionId: {}", eventId, sessionId);

        // 获取统计信息
        StockStatisticsVO statistics = ticketTierService.getStockStatistics(eventId, sessionId, null);

        // 筛选售罄票档
        List<StockStatisticsVO.TierStockDetail> soldOutTiers = statistics.getTierDetails().stream()
                .filter(tier -> tier.getAvailableStock() == 0 && tier.getStatus() == 1)
                .collect(java.util.stream.Collectors.toList());

        return Result.success("查询成功", soldOutTiers);
    }
}
