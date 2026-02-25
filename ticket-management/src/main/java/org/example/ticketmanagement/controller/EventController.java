package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckDisable;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.RoleConstant;
import org.example.ticketmanagement.dto.EventDTO;
import org.example.ticketmanagement.dto.EventQueryDTO;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.service.EventService;
import org.example.ticketmanagement.vo.EventVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "演出管理", description = "演出相关接口")
public class EventController {

    @Autowired
    private EventService eventService;

    // ==================== 公共接口（无需登录） ====================

    /**
     * 获取演出列表（公共接口）
     * 可选参数：
     * - status: 演出状态
     * - categoryId: 分类ID
     * - cityId: 城市ID
     * - artistName: 艺人名称（模糊查询）
     * - name: 演出名称（模糊查询）
     */
    @Operation(summary = "获取演出列表", tags = {"演出-公共接口"})
    @GetMapping("/public/events")
    public Result<List<EventVO>> getAllEvents(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String artistName,
            @RequestParam(required = false) String name) {

        List<EventVO> events;

        if (artistName != null) {
            log.debug("根据艺人名称模糊查询演出，artistName: {}", artistName);
            events = eventService.searchEventsByArtistName(artistName);
        } else if (name != null) {
            log.debug("根据演出名称模糊查询演出，name: {}", name);
            events = eventService.searchEventsByName(name);
        } else if (categoryId != null && cityId != null) {
            log.debug("根据分类和城市查询演出，categoryId: {}, cityId: {}", categoryId, cityId);
            events = eventService.getEventsByCategoryAndCity(categoryId, cityId);
        } else if (categoryId != null) {
            log.debug("根据分类查询演出，categoryId: {}", categoryId);
            events = eventService.getEventsByCategoryId(categoryId);
        } else if (cityId != null) {
            log.debug("根据城市查询演出，cityId: {}", cityId);
            events = eventService.getEventsByCityId(cityId);
        } else if (status != null) {
            log.debug("根据状态查询演出，status: {}", status);
            events = eventService.getEventsByStatus(status);
        } else {
            log.debug("查询所有演出列表");
            events = eventService.getAllEvents();
        }

        return Result.success("查询成功", events);
    }

    /**
     * 获取热门演出列表（公共接口）
     * 参数：limit - 返回数量限制，默认10
     */
    @Operation(summary = "获取热门演出列表", tags = {"演出-公共接口"})
    @GetMapping("/public/events/hot")
    public Result<List<EventVO>> getHotEvents(
            @RequestParam(defaultValue = "10") Integer limit) {
        log.debug("获取热门演出，limit: {}", limit);

        List<EventVO> events = eventService.getHotEvents(limit);
        return Result.success("查询成功", events);
    }

    /**
     * 获取即将开售的演出列表（公共接口）
     * 参数：limit - 返回数量限制，默认10
     */
    @Operation(summary = "获取即将开售的演出列表", tags = {"演出-公共接口"})
    @GetMapping("/public/events/upcoming")
    public Result<List<EventVO>> getUpcomingEvents(
            @RequestParam(defaultValue = "10") Integer limit) {
        log.debug("获取即将开售的演出，limit: {}", limit);

        List<EventVO> events = eventService.getUpcomingEvents(limit);
        return Result.success("查询成功", events);
    }


    // ==================== 用户端接口（需要登录） ====================

    /**
     * 根据ID获取演出详情（用户端）
     */
    @Operation(summary = "获取演出详情", tags = {"演出-用户端"})
    @GetMapping("/user/events/{id}")
    @SaCheckLogin
    public Result<EventVO> getEventById(@PathVariable Long id) {
        log.debug("查询演出详情，ID: {}", id);

        EventVO event = eventService.getEventById(id);
        if (event != null) {
            return Result.success("查询成功", event);
        } else {
            return Result.error("演出不存在");
        }
    }

    /**
     * 分页查询演出列表（用户端）
     */
    @Operation(summary = "分页查询演出列表", tags = {"演出-用户端"})
    @GetMapping("/user/events/page")
    @SaCheckLogin
    public Result<PageResult<EventVO>> getEventsByPage(
            @Valid EventQueryDTO queryDTO) {  // 直接接收DTO，而不是单独的参数
        
        log.debug("分页查询演出列表，参数: {}", queryDTO);
        
        // 调用分页查询方法
        PageResult<EventVO> result = eventService.queryEvents(queryDTO);
        return Result.success("查询成功", result);
    }

    /**
     * 根据分类ID获取演出列表（用户端）
     */
    @Operation(summary = "根据分类获取演出列表", tags = {"演出-用户端"})
    @GetMapping("/user/events/category/{categoryId}")
    @SaCheckLogin
    public Result<List<EventVO>> getEventsByCategoryId(@PathVariable Long categoryId) {
        log.debug("根据分类ID查询演出列表，categoryId: {}", categoryId);

        List<EventVO> events = eventService.getEventsByCategoryId(categoryId);
        return Result.success("查询成功", events);
    }

    /**
     * 根据城市ID获取演出列表（用户端）
     */
    @Operation(summary = "根据城市获取演出列表", tags = {"演出-用户端"})
    @GetMapping("/user/events/city/{cityId}")
    @SaCheckLogin
    public Result<List<EventVO>> getEventsByCityId(@PathVariable Long cityId) {
        log.debug("根据城市ID查询演出列表，cityId: {}", cityId);

        List<EventVO> events = eventService.getEventsByCityId(cityId);
        return Result.success("查询成功", events);
    }

    /**
     * 根据状态获取演出列表（用户端）
     */
    @Operation(summary = "根据状态获取演出列表", tags = {"演出-用户端"})
    @GetMapping("/user/events/status/{status}")
    @SaCheckLogin
    public Result<List<EventVO>> getEventsByStatus(@PathVariable Integer status) {
        log.debug("根据状态查询演出列表，status: {}", status);

        List<EventVO> events = eventService.getEventsByStatus(status);
        return Result.success("查询成功", events);
    }

    // ==================== 管理端接口（需要管理员权限） ====================

    /**
     * 1. 新增演出
     */
    @Operation(summary = "新增演出", tags = {"演出-管理端"})
    @PostMapping("/admin/events")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<Void> addEvent(@Valid @RequestBody EventDTO eventDTO) {
        log.info("收到新增演出请求: {}", eventDTO.getName());

        boolean success = eventService.addEvent(eventDTO);
        if (success) {
            return Result.success("演出添加成功");
        } else {
            return Result.error("演出添加失败，请检查分类、城市是否存在，或时间逻辑是否正确");
        }
    }

    /**
     * 2. 根据ID删除演出
     */
    @Operation(summary = "删除演出", tags = {"演出-管理端"})
    @DeleteMapping("/admin/events/{id}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<Void> deleteEvent(@PathVariable Long id) {
        log.info("收到删除演出请求，ID: {}", id);

        boolean success = eventService.deleteEvent(id);
        if (success) {
            return Result.success("演出删除成功");
        } else {
            return Result.error("演出删除失败，可能演出不存在");
        }
    }

    /**
     * 3. 更新演出信息
     */
    @Operation(summary = "更新演出信息", tags = {"演出-管理端"})
    @PutMapping("/admin/events/{id}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<Void> updateEvent(@PathVariable Long id,
                                    @Valid @RequestBody EventDTO eventDTO) {
        log.info("收到更新演出请求，ID: {}", id);

        boolean success = eventService.updateEvent(id, eventDTO);
        if (success) {
            return Result.success("演出更新成功");
        } else {
            return Result.error("演出更新失败，可能演出不存在或数据不合法");
        }
    }

    /**
     * 4. 根据ID获取演出详情（管理端）
     */
    @Operation(summary = "查询演出详情（管理端）", tags = {"演出-管理端"})
    @GetMapping("/admin/events/{id}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<EventVO> AdminGetEventById(@PathVariable Long id) {
        log.debug("查询演出详情，ID: {}", id);

        EventVO event = eventService.getEventById(id);
        if (event != null) {
            return Result.success("查询成功", event);
        } else {
            return Result.error("演出不存在");
        }
    }

    /**
     * 5.分页查询演出列表（支持多条件筛选）
     */
    @Operation(summary = "分页查询演出列表（多条件筛选）", tags = {"演出-管理端"})
    @GetMapping("/admin/events/query")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<PageResult<EventVO>> queryEvents(@Valid EventQueryDTO queryDTO) {
        log.info("分页查询演出列表，参数: {}", queryDTO);

        try {
            PageResult<EventVO> result = eventService.queryEvents(queryDTO);
            return Result.success("查询成功", result);
        } catch (Exception e) {
            log.error("分页查询演出列表失败", e);
            return Result.error("查询失败，请检查参数");
        }
    }
}
