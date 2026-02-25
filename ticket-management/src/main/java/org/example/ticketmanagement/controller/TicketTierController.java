package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckDisable;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.RoleConstant;
import org.example.ticketmanagement.dto.TicketTierDTO;
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
     * 获取票档列表（公共接口）
     * 可选参数：
     * - status: 状态 (0-禁用，1-启用)
     * - eventId: 演出ID
     * - sessionId: 场次ID
     * - minPrice: 最低价格
     * - maxPrice: 最高价格
     */
    @Operation(summary = "获取票档列表", tags = {"票档-公共接口"})
    @GetMapping("/public/ticket-tiers")
    public Result<List<TicketTierVO>> getAllTicketTiers(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice) {

        List<TicketTierVO> ticketTiers;

        if (eventId != null && status != null && sessionId != null) {
            // 根据演出ID、场次ID和状态查询
            log.debug("根据演出ID、场次ID和状态查询票档，eventId: {}, sessionId: {}, status: {}", eventId, sessionId, status);
            ticketTiers = ticketTierService.getTicketTiersBySessionIdAndStatus(sessionId, status);
        } else if (eventId != null && status != null) {
            log.debug("根据演出ID和状态查询票档，eventId: {}, status: {}", eventId, status);
            ticketTiers = ticketTierService.getTicketTiersByEventIdAndStatus(eventId, status);
        } else if (sessionId != null && status != null) {
            log.debug("根据场次ID和状态查询票档，sessionId: {}, status: {}", sessionId, status);
            ticketTiers = ticketTierService.getTicketTiersBySessionIdAndStatus(sessionId, status);
        } else if (minPrice != null && maxPrice != null) {
            log.debug("根据价格范围查询票档，minPrice: {}, maxPrice: {}", minPrice, maxPrice);
            // 默认查询启用状态的票档
            Integer queryStatus = (status != null) ? status : 1;
            ticketTiers = ticketTierService.getTicketTiersByPriceRange(minPrice, maxPrice, queryStatus);
        } else if (eventId != null) {
            log.debug("根据演出ID查询票档，eventId: {}", eventId);
            ticketTiers = ticketTierService.getTicketTiersByEventId(eventId);
        } else if (sessionId != null) {
            log.debug("根据场次ID查询票档，sessionId: {}", sessionId);
            ticketTiers = ticketTierService.getTicketTiersBySessionId(sessionId);
        } else if (status != null) {
            log.debug("根据状态查询票档，status: {}", status);
            ticketTiers = ticketTierService.getTicketTiersByStatus(status);
        } else {
            log.debug("查询所有票档列表");
            ticketTiers = ticketTierService.getAllTicketTiers();
        }

        return Result.success("查询成功", ticketTiers);
    }

    /**
     * 根据价格范围查询票档列表（公共接口）
     * 参数：
     * - minPrice: 最低价格（必填）
     * - maxPrice: 最高价格（必填）
     * - status: 状态 (1-启用，默认1)
     */
    @Operation(summary = "根据价格范围查询票档列表", tags = {"票档-公共接口"})
    @GetMapping("/public/ticket-tiers/price-range")
    public Result<List<TicketTierVO>> getTicketTiersByPriceRange(
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice,
            @RequestParam(defaultValue = "1") Integer status) {
        log.debug("根据价格范围查询票档，minPrice: {}, maxPrice: {}, status: {}", minPrice, maxPrice, status);

        // 参数校验
        if (minPrice.compareTo(BigDecimal.ZERO) < 0 || maxPrice.compareTo(BigDecimal.ZERO) < 0) {
            return Result.error("价格不能为负数");
        }
        if (minPrice.compareTo(maxPrice) > 0) {
            return Result.error("最低价格不能大于最高价格");
        }

        List<TicketTierVO> ticketTiers = ticketTierService.getTicketTiersByPriceRange(minPrice, maxPrice, status);
        return Result.success("查询成功", ticketTiers);
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
     * 根据演出ID查询票档列表（用户端）
     */
    @Operation(summary = "根据演出ID查询票档列表", tags = {"票档-用户端"})
    @GetMapping("/user/ticket-tiers/event/{eventId}")
    @SaCheckLogin
    public Result<List<TicketTierVO>> getTicketTiersByEventId(@PathVariable Long eventId) {
        log.debug("根据演出ID查询票档，eventId: {}", eventId);

        List<TicketTierVO> ticketTiers = ticketTierService.getTicketTiersByEventId(eventId);
        return Result.success("查询成功", ticketTiers);
    }

    /**
     * 根据场次ID查询票档列表（用户端）
     */
    @Operation(summary = "根据场次ID查询票档列表", tags = {"票档-用户端"})
    @GetMapping("/user/ticket-tiers/session/{sessionId}")
    @SaCheckLogin
    public Result<List<TicketTierVO>> getTicketTiersBySessionId(@PathVariable Long sessionId) {
        log.debug("根据场次ID查询票档，sessionId: {}", sessionId);

        List<TicketTierVO> ticketTiers = ticketTierService.getTicketTiersBySessionId(sessionId);
        return Result.success("查询成功", ticketTiers);
    }

    /**
     * 根据状态查询票档列表（用户端）
     */
    @Operation(summary = "根据状态查询票档列表", tags = {"票档-用户端"})
    @GetMapping("/user/ticket-tiers/status/{status}")
    @SaCheckLogin
    public Result<List<TicketTierVO>> getTicketTiersByStatus(@PathVariable Integer status) {
        log.debug("根据状态查询票档，status: {}", status);

        List<TicketTierVO> ticketTiers = ticketTierService.getTicketTiersByStatus(status);
        return Result.success("查询成功", ticketTiers);
    }

    /**
     * 获取票档库存信息（客户端）
     * 参数：
     * - eventId: 演出ID
     * - sessionId: 场次ID
     */
    @Operation(summary = "获取票档库存信息", tags = {"票档-用户端"})
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
            return Result.error("请提供演出ID或场次ID");
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
     * 根据演出ID查询票档列表（管理端）
     */
    @Operation(summary = "根据演出ID查询票档列表", tags = {"票档-管理端"})
    @GetMapping("/admin/ticket-tiers/event/{eventId}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<List<TicketTierVO>> AdminGetTicketTiersByEventId(@PathVariable Long eventId) {
        log.debug("根据演出ID查询票档，eventId: {}", eventId);

        List<TicketTierVO> ticketTiers = ticketTierService.getTicketTiersByEventId(eventId);
        return Result.success("查询成功", ticketTiers);
    }

    /**
     * 根据场次ID查询票档列表（管理端）
     */
    @Operation(summary = "根据场次ID查询票档列表", tags = {"票档-管理端"})
    @GetMapping("/admin/ticket-tiers/session/{sessionId}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<List<TicketTierVO>> AdminGetTicketTiersBySessionId(@PathVariable Long sessionId) {
        log.debug("根据场次ID查询票档，sessionId: {}", sessionId);

        List<TicketTierVO> ticketTiers = ticketTierService.getTicketTiersBySessionId(sessionId);
        return Result.success("查询成功", ticketTiers);
    }

    /**
     * 根据演出ID和状态查询票档列表（管理端）
     */
    @Operation(summary = "根据演出ID和状态查询票档列表", tags = {"票档-管理端"})
    @GetMapping("/admin/ticket-tiers/event/{eventId}/status/{status}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<List<TicketTierVO>> getTicketTiersByEventIdAndStatus(
            @PathVariable Long eventId,
            @PathVariable Integer status) {
        log.debug("根据演出ID和状态查询票档，eventId: {}, status: {}", eventId, status);

        List<TicketTierVO> ticketTiers = ticketTierService.getTicketTiersByEventIdAndStatus(eventId, status);
        return Result.success("查询成功", ticketTiers);
    }

    /**
     * 根据场次ID和状态查询票档列表（管理端）
     */
    @Operation(summary = "根据场次ID和状态查询票档列表", tags = {"票档管理相关接口"})
    @GetMapping("/admin/ticket-tiers/session/{sessionId}/status/{status}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<List<TicketTierVO>> getTicketTiersBySessionIdAndStatus(
            @PathVariable Long sessionId,
            @PathVariable Integer status) {
        log.debug("根据场次ID和状态查询票档，sessionId: {}, status: {}", sessionId, status);

        List<TicketTierVO> ticketTiers = ticketTierService.getTicketTiersBySessionIdAndStatus(sessionId, status);
        return Result.success("查询成功", ticketTiers);
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

        // 筛选售罄票档（availableStock = 0 且 status = 1）
        List<StockStatisticsVO.TierStockDetail> soldOutTiers = statistics.getTierDetails().stream()
                .filter(tier -> tier.getAvailableStock() == 0 && tier.getStatus() == 1)
                .collect(java.util.stream.Collectors.toList());

        return Result.success("查询成功", soldOutTiers);
    }
}
