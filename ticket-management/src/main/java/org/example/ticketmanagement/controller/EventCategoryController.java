package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckDisable;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.RoleConstant;
import org.example.ticketmanagement.dto.EventCategoryDTO;
import org.example.ticketmanagement.dto.EventCategoryQueryDTO;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.service.EventCategoryService;
import org.example.ticketmanagement.vo.EventCategoryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@Validated
@Tag(name = "演出分类管理", description = "演出分类管理相关接口")
public class EventCategoryController {
    @Autowired
    private EventCategoryService eventCategoryService;

    // ==================== 公共接口（无需登录） ====================

    /**
     * 获取全部启用分类列表（公共接口）
     * 默认返回 status=1 的启用状态分类
     */
    @Operation(summary = "获取分类列表", tags = {"演出分类 - 公共接口"})
    @GetMapping("/public/categories")
    public Result<List<EventCategoryVO>> getAllCategories() {
        log.debug("查询全部启用的分类列表");
        
        // 默认查询启用状态的分类
        List<EventCategoryVO> categories = eventCategoryService.getCategoriesByStatus(1);
        
        return Result.success("查询成功", categories);
    }


    // ==================== 用户端接口（需要登录） ====================

    /**
     * 根据ID获取分类详情（用户端）
     */
    @Operation(summary = "获取分类详情", tags = {"演出分类-用户端"})
    @GetMapping("/user/categories/{id}")
    @SaCheckLogin
    public Result<EventCategoryVO> getCategoryById(@PathVariable Long id) {
        log.debug("查询分类详情，ID: {}", id);

        EventCategoryVO category = eventCategoryService.getCategoryById(id);
        if (category != null) {
            return Result.success("查询成功", category);
        } else {
            return Result.error("分类不存在");
        }
    }

    /**
     * 分页查询分类列表（用户端）
     * 默认查询启用状态（status=1）的分类
     */
    @Operation(summary = "分页查询分类列表", tags = {"演出分类 - 用户端"})
    @GetMapping("/user/categories/page")
    @SaCheckLogin
    public Result<PageResult<EventCategoryVO>> getCategoriesByPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name) {

        log.debug("分页查询分类，page: {}, size: {}, name: {}", page, size, name);

        // 构建查询参数
        EventCategoryQueryDTO queryDTO = new EventCategoryQueryDTO();
        queryDTO.setPage(page);
        queryDTO.setSize(size);
        queryDTO.setStatus(1);  // 默认查询启用状态
        queryDTO.setName(name);  // 支持模糊匹配

        // 调用统一的分页查询方法
        PageResult<EventCategoryVO> result = eventCategoryService.queryCategories(queryDTO);
        return Result.success("查询成功", result);
    }


    // ==================== 管理端接口（需要管理员权限） ====================

    /**
     * 新增演出分类
     */
    @Operation(summary = "新增演出分类", tags = {"演出分类-管理端"})
    @PostMapping("/admin/categories")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<Void> addCategory(@Valid @RequestBody EventCategoryDTO eventCategoryDTO) {
        log.info("收到新增分类请求: {}", eventCategoryDTO);

        boolean success = eventCategoryService.addCategory(eventCategoryDTO);
        if (success) {
            return Result.success("分类添加成功");
        } else {
            return Result.error("分类添加失败，可能分类名称重复");
        }
    }

    /**
     * 根据ID删除分类
     */
    @Operation(summary = "删除演出分类", tags = {"演出分类-管理端"})
    @DeleteMapping("/admin/categories/{id}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<Void> deleteCategory(@PathVariable Long id) {
        log.info("收到删除分类请求，ID: {}", id);

        boolean success = eventCategoryService.deleteCategory(id);
        if (success) {
            return Result.success("分类删除成功");
        } else {
            return Result.error("分类删除失败，可能分类不存在");
        }
    }

    /**
     * 更新分类信息
     */
    @Operation(summary = "更新演出分类", tags = {"演出分类-管理端"})
    @PutMapping("/admin/categories/{id}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<Void> updateCategory(@PathVariable Long id,
                                       @Valid @RequestBody EventCategoryDTO eventCategoryDTO) {
        log.info("收到更新分类请求，ID: {}", id);

        boolean success = eventCategoryService.updateCategory(id, eventCategoryDTO);
        if (success) {
            return Result.success("分类更新成功");
        } else {
            return Result.error("分类更新失败，可能分类不存在");
        }
    }

    /**
     * 根据ID获取分类详情（管理端）
     */
    @Operation(summary = "查询分类详情（管理端）", tags = {"演出分类-管理端"})
    @GetMapping("/admin/categories/{id}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<EventCategoryVO> AdminGetCategoryById(@PathVariable Long id) {
        log.debug("查询分类详情，ID: {}", id);

        EventCategoryVO category = eventCategoryService.getCategoryById(id);
        if (category != null) {
            return Result.success("查询成功", category);
        } else {
            return Result.error("分类不存在");
        }
    }

    /**
     * 分页查询分类列表
     */
    @Operation(summary = "分页查询分类列表（管理端）", tags = {"演出分类-管理端"})
    @GetMapping("/admin/categories/query")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<PageResult<EventCategoryVO>> queryCategories(@Valid EventCategoryQueryDTO queryDTO) {
        log.info("分页查询分类列表，参数: {}", queryDTO);

        try {
            PageResult<EventCategoryVO> result = eventCategoryService.queryCategories(queryDTO);
            return Result.success("查询成功", result);
        } catch (Exception e) {
            log.error("分页查询分类列表失败", e);
            return Result.error("查询失败，请检查参数");
        }
    }
}
