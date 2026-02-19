package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckDisable;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.RoleConstant;
import org.example.ticketmanagement.dto.EventSessionDTO;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.service.CityService;
import org.example.ticketmanagement.service.EventSessionService;
import org.example.ticketmanagement.vo.EventSessionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/sessions")
@Tag(name = "演出场次管理", description = "演出场次管理接口")
public class EventSessionController {

    @Autowired
    private EventSessionService eventSessionService;

    // ==================== 公共接口（无需登录） ====================
    /**
     * 获取所有场次列表（公共接口）
     * 可选参数：
     * - status: 状态 (0-禁用，1-启用)
     * - eventId: 演出ID
     */
    @Operation(summary = "获取场次列表", tags = {"演出场次-公共接口"})
    @GetMapping("/public")
    @SaCheckDisable
    public Result<List<EventSessionVO>> getAllSessions(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long eventId) {

        List<EventSessionVO> sessions;

        if (eventId != null && status != null) {
            log.debug("根据演出ID和状态查询场次，eventId: {}, status: {}", eventId, status);
            sessions = eventSessionService.getSessionsByEventIdAndStatus(eventId, status);
        } else if (eventId != null) {
            log.debug("根据演出ID查询场次，eventId: {}", eventId);
            sessions = eventSessionService.getSessionsByEventId(eventId);
        } else if (status != null) {
            log.debug("根据状态查询场次，status: {}", status);
            sessions = eventSessionService.getSessionsByStatus(status);
        } else {
            log.debug("查询所有场次列表");
            sessions = eventSessionService.getAllSessions();
        }

        return Result.success("查询成功", sessions);
    }

    /**
     * 获取即将开始的场次（公共接口）
     * 可选参数：limit (默认10条)
     */
    @Operation(summary = "获取即将开始的场次", tags = {"演出场次-公共接口"})
    @GetMapping("/public/upcoming")
    @SaCheckDisable
    public Result<List<EventSessionVO>> getUpcomingSessions(
            @RequestParam(defaultValue = "10") Integer limit) {
        log.debug("获取即将开始的场次，limit: {}", limit);

        List<EventSessionVO> sessions = eventSessionService.getUpcomingSessions(limit);
        return Result.success("查询成功", sessions);
    }


    // ==================== 用户端接口（需要登录） ====================
    /**
     * 根据ID获取场次详情（用户端）
     */
    @Operation(summary = "获取场次详情", tags = {"演出场次-用户端"})
    @GetMapping("/user/{id}")
    @SaCheckLogin
    public Result<EventSessionVO> getSessionById(@PathVariable Long id) {
        log.debug("查询场次详情，ID: {}", id);

        EventSessionVO session = eventSessionService.getSessionById(id);
        if (session != null) {
            return Result.success("查询成功", session);
        } else {
            return Result.error("场次不存在");
        }
    }

    /**
     * 根据演出ID获取场次列表（用户端）
     */
    @Operation(summary = "根据演出ID获取场次列表", tags = {"演出场次-用户端"})
    @GetMapping("/user/event/{eventId}")
    @SaCheckLogin
    public Result<List<EventSessionVO>> getSessionsByEventId(@PathVariable Long eventId) {
        log.debug("根据演出ID查询场次，eventId: {}", eventId);

        List<EventSessionVO> sessions = eventSessionService.getSessionsByEventId(eventId);
        return Result.success("查询成功", sessions);
    }

    /**
     * 根据状态获取场次列表（用户端）
     */
    @Operation(summary = "根据状态获取场次列表", tags = {"演出场次-用户端"})
    @GetMapping("/user/status/{status}")
    @SaCheckLogin
    public Result<List<EventSessionVO>> getSessionsByStatus(@PathVariable Integer status) {
        log.debug("根据状态查询场次，status: {}", status);

        List<EventSessionVO> sessions = eventSessionService.getSessionsByStatus(status);
        return Result.success("查询成功", sessions);
    }


    // ==================== 管理端接口（需要管理员权限） ====================

    /**
     * 1. 新增场次
     */
    @Operation(summary = "新增场次", tags = {"演出场次-管理端"})
    @PostMapping("/admin")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<Void> addSession(@Valid @RequestBody EventSessionDTO eventSessionDTO) {
        log.info("收到新增场次请求: {}", eventSessionDTO.getSessionName());

        boolean success = eventSessionService.addSession(eventSessionDTO);
        if (success) {
            return Result.success("场次添加成功");
        } else {
            return Result.error("场次添加失败，请检查演出是否存在、时间是否冲突或是否在演出时间范围内");
        }
    }

    /**
     * 2. 根据ID删除场次
     */
    @Operation(summary = "删除场次", tags = {"演出场次-管理端"})
    @DeleteMapping("/admin/{id}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<Void> deleteSession(@PathVariable Long id) {
        log.info("收到删除场次请求，ID: {}", id);

        boolean success = eventSessionService.deleteSession(id);
        if (success) {
            return Result.success("场次删除成功");
        } else {
            return Result.error("场次删除失败，可能场次不存在");
        }
    }

    /**
     * 3. 更新场次信息
     */
    @Operation(summary = "更新场次信息", tags = {"演出场次-管理端"})
    @PutMapping("/admin/{id}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<Void> updateSession(@PathVariable Long id,
                                      @Valid @RequestBody EventSessionDTO eventSessionDTO) {
        log.info("收到更新场次请求，ID: {}", id);

        boolean success = eventSessionService.updateSession(id, eventSessionDTO);
        if (success) {
            return Result.success("场次更新成功");
        } else {
            return Result.error("场次更新失败，可能场次不存在或数据不合法");
        }
    }

    /**
     * 4. 根据ID获取场次详情（管理端）
     */
    @Operation(summary = "查询场次详情（管理端）", tags = {"演出场次-管理端"})
    @GetMapping("/admin/{id}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<EventSessionVO> AdminGetSessionById(@PathVariable Long id) {
        log.debug("查询场次详情，ID: {}", id);

        EventSessionVO session = eventSessionService.getSessionById(id);
        if (session != null) {
            return Result.success("查询成功", session);
        } else {
            return Result.error("场次不存在");
        }
    }
}
