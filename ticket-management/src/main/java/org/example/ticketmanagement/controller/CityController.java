package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckDisable;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.RoleConstant;
import org.example.ticketmanagement.dto.CityDTO;
import org.example.ticketmanagement.dto.CityQueryDTO;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.service.CityService;
import org.example.ticketmanagement.vo.CityVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/cities")
@Tag(name = "城市管理", description = "城市相关所有接口")
public class CityController {

    @Autowired
    private CityService cityService;

    // ==================== 公共接口（无需登录） ====================

    /**
     * 获取所有城市列表（公开接口）
     * 可选参数：status (0-禁用，1-启用)
     * 可选参数：hotLevel (0-普通，1-热门)
     */
    @Operation(summary = "获取城市列表", tags = {"城市-公共接口"})
    @GetMapping("/public")
    @SaCheckDisable
    public Result<List<CityVO>> getAllCities(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer hotLevel) {

        List<CityVO> cities;

        if (status != null && hotLevel != null) {
            log.debug("查询城市列表：status={}, hotLevel={}", status, hotLevel);
            cities = cityService.getCitiesByStatus(status);
        } else if (status != null) {
            log.debug("根据状态查询城市列表，status: {}", status);
            cities = cityService.getCitiesByStatus(status);
        } else if (hotLevel != null) {
            log.debug("根据热门等级查询城市列表，hotLevel: {}", hotLevel);
            cities = cityService.getCitiesByHotLevel(hotLevel);
        } else {
            log.debug("查询所有城市列表");
            cities = cityService.getAllCities();
        }

        return Result.success("查询成功", cities);
    }

    /**
     * 根据名称查询城市（模糊查询）- 公开接口
     */
    @Operation(summary = "根据名称模糊查询城市", tags = {"城市-公共接口"})
    @GetMapping("/public/search/name/{name}")
    @SaCheckDisable
    public Result<List<CityVO>> searchCitiesByName(@PathVariable String name) {
        log.debug("根据名称模糊查询城市: {}", name);

        //TODO 需要先扩展Service方法，这里暂时返回所有城市
        // 实际项目中需要实现模糊查询逻辑

        List<CityVO> cities = cityService.getAllCities();
        return Result.success("查询成功", cities);
    }

    // ==================== 用户端接口（需要登录） ====================

    /**
     * 根据ID获取城市详情（用户端）
     */
    @Operation(summary = "获取城市详情", tags = {"城市-用户端"})
    @GetMapping("/user/{id}")
    @SaCheckLogin
    public Result<CityVO> getCityById(@PathVariable Long id) {
        log.debug("查询城市详情，ID: {}", id);

        CityVO city = cityService.getCityById(id);
        if (city != null) {
            return Result.success("查询成功", city);
        } else {
            return Result.error("城市不存在");
        }
    }

    /**
     * 分页查询城市列表（用户端）
     */
    @Operation(summary = "分页查询城市列表", tags = {"城市-用户端"})
    @GetMapping("/user/page")
    @SaCheckLogin
    public Result<PageResult<CityVO>> getCitiesByPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer hotLevel) {

        log.debug("分页查询城市列表，page: {}, size: {}, status: {}, hotLevel: {}",
                page, size, status, hotLevel);

        // 构建查询参数
        org.example.ticketmanagement.dto.CityQueryDTO queryDTO = new org.example.ticketmanagement.dto.CityQueryDTO();
        queryDTO.setPage(page);
        queryDTO.setSize(size);
        queryDTO.setStatus(status);
        queryDTO.setHotLevel(hotLevel);

        // 调用统一的分页查询方法
        PageResult<CityVO> result = cityService.queryCities(queryDTO);
        return Result.success("查询成功", result);
    }

    /**
     * 根据状态查询城市列表（用户端）
     */
    @Operation(summary = "根据状态查询城市列表", tags = {"城市-用户端"})
    @GetMapping("/user/status/{status}")
    @SaCheckLogin
    public Result<List<CityVO>> getCitiesByStatus(@PathVariable Integer status) {
        log.debug("根据状态查询城市列表，status: {}", status);

        List<CityVO> cities = cityService.getCitiesByStatus(status);
        return Result.success("查询成功", cities);
    }

    /**
     * 根据热门等级查询城市列表（用户端）
     */
    @Operation(summary = "根据热门等级查询城市列表", tags = {"城市-用户端"})
    @GetMapping("/user/hot/{hotLevel}")
    @SaCheckLogin
    public Result<List<CityVO>> getCitiesByHotLevel(@PathVariable Integer hotLevel) {
        log.debug("根据热门等级查询城市列表，hotLevel: {}", hotLevel);

        List<CityVO> cities = cityService.getCitiesByHotLevel(hotLevel);
        return Result.success("查询成功", cities);
    }

    // ==================== 管理端接口（需要管理员权限） ====================

    /**
     * 1. 新增城市
     */
    @Operation(summary = "新增城市", tags = {"城市-管理端"})
    @PostMapping("/admin")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<Void> addCity(@Valid @RequestBody CityDTO cityDTO) {
        log.info("收到新增城市请求: {}", cityDTO);

        boolean success = cityService.addCity(cityDTO);
        if (success) {
            return Result.success("城市添加成功");
        } else {
            return Result.error("城市添加失败，可能名称或代码已存在");
        }
    }

    /**
     * 2. 根据ID删除城市
     */
    @Operation(summary = "删除城市", tags = {"城市-管理端"})
    @DeleteMapping("/admin/{id}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<Void> deleteCity(@PathVariable Long id) {
        log.info("收到删除城市请求，ID: {}", id);

        boolean success = cityService.deleteCity(id);
        if (success) {
            return Result.success("城市删除成功");
        } else {
            return Result.error("城市删除失败，可能城市不存在");
        }
    }

    /**
     * 3. 更新城市信息
     */
    @Operation(summary = "更新城市信息", tags = {"城市-管理端"})
    @PutMapping("/admin/{id}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<Void> updateCity(@PathVariable Long id,
                                   @Valid @RequestBody CityDTO cityDTO) {
        log.info("收到更新城市请求，ID: {}", id);

        boolean success = cityService.updateCity(id, cityDTO);
        if (success) {
            return Result.success("城市更新成功");
        } else {
            return Result.error("城市更新失败，可能城市不存在或名称/代码重复");
        }
    }

    /**
     * 4. 根据ID获取城市详情（管理端，可能包含更多字段）
     */
    @Operation(summary = "查询城市详情（管理端）", tags = {"城市-管理端"})
    @GetMapping("/admin/{id}")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<CityVO> adminGetCityById(@PathVariable Long id) {
        log.debug("查询城市详情，ID: {}", id);

        CityVO city = cityService.getCityById(id);
        if (city != null) {
            return Result.success("查询成功", city);
        } else {
            return Result.error("城市不存在");
        }
    }

    /**
     * 分页查询城市列表（支持多条件筛选）
     */
    @Operation(summary = "分页查询城市列表（多条件筛选）", tags = {"城市-管理端"})
    @GetMapping("/admin/query")
    @SaCheckRole(RoleConstant.ADMIN)
    public Result<PageResult<CityVO>> queryCities(@Valid CityQueryDTO queryDTO) {
        log.info("分页查询城市列表，参数: {}", queryDTO);

        try {
            PageResult<CityVO> result = cityService.queryCities(queryDTO);
            return Result.success("查询成功", result);
        } catch (Exception e) {
            log.error("分页查询城市列表失败", e);
            return Result.error("查询失败，请检查参数");
        }
    }



}
