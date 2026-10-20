package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.RoleConstant;
import org.example.ticketmanagement.dto.EventSessionDTO;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.service.EventSessionService;
import org.example.ticketmanagement.vo.EventSessionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "演出场次管理", description = "演出场次管理接口")
public class EventSessionController {

    @Autowired
    private EventSessionService eventSessionService;

    // ==================== 公共接口（无需登录） ====================
    /**
     * 获取所有启用场次列表（公共接口）
     * 可选参数：eventId (演出 ID)
     * 默认查询 status=1 的启用状态场次
     */
    @Operation(summary = "获取场次列表", tags = { "演出场次 - 公共接口" })
    @GetMapping("/public/sessions")
    public Result<List<EventSessionVO>> getAllSessions(@RequestParam(required = false) Long eventId) {
        List<EventSessionVO> sessions;
        if (eventId != null) {
            log.debug("根据演出 ID 查询启用的场次，eventId: {}", eventId);
            // 查询指定演出的启用场次
            sessions = eventSessionService.getSessionsByEventIdAndStatus(eventId, 1);
        } else {
            log.debug("查询所有启用的场次");
            // 查询所有启用的场次
            sessions = eventSessionService.getSessionsByStatus(1);
        }
        return Result.success("查询成功", sessions);
    }

    /**
     * 获取即将开始的场次（公共接口）
     * 可选参数：limit (默认10条)
     */
    @Operation(summary = "获取即将开始的场次", tags = { "演出场次-公共接口" })
    @GetMapping("/public/sessions/upcoming")
    public Result<List<EventSessionVO>> getUpcomingSessions(@RequestParam(defaultValue = "10") Integer limit) {
        log.debug("获取即将开始的场次，limit: {}", limit);
        List<EventSessionVO> sessions = eventSessionService.getUpcomingSessions(limit);
        return Result.success("查询成功", sessions);
    }

    // ==================== 用户端接口（需要登录） ====================
    /**
     * 根据ID获取场次详情（用户端）
     */
    @Operation(summary = "获取场次详情", tags = { "演出场次-用户端" })
    @GetMapping("/user/sessions/{id}")
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

    // ==================== 管理端接口（需要管理员权限） ====================
    /**
     * 1. 新增场次
     */
    @Operation(summary = "新增场次", tags = { "演出场次-管理端" })
    @PostMapping("/admin/sessions")
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
    @Operation(summary = "下架场次并保留历史引用", tags = { "演出场次-管理端" })
    @DeleteMapping("/admin/sessions/{id}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<Void> deleteSession(@PathVariable Long id) {
        log.info("收到删除场次请求，ID: {}", id);
        boolean success = eventSessionService.deleteSession(id);
        if (success) {
            return Result.success("场次已下架，票档和历史记录已保留");
        } else {
            return Result.error("场次删除失败，可能场次不存在");
        }
    }

    /**
     * 3. 更新场次信息
     */
    @Operation(summary = "更新场次信息", tags = { "演出场次-管理端" })
    @PutMapping("/admin/sessions/{id}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<Void> updateSession(@PathVariable Long id, @Valid @RequestBody EventSessionDTO eventSessionDTO) {
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
    @Operation(summary = "查询场次详情（管理端）", tags = { "演出场次-管理端" })
    @GetMapping("/admin/sessions/{id}")
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
