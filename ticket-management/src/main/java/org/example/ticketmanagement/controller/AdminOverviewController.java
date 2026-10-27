package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.constant.RoleConstant;
import org.example.ticketmanagement.dto.AdminSessionQuery;
import org.example.ticketmanagement.dto.AdminRefundQuery;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.service.AdminOverviewService;
import org.example.ticketmanagement.vo.EventSessionVO;
import org.example.ticketmanagement.vo.AdminDashboardVO;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@SaCheckRole(RoleConstant.ADMIN)
@RequiredArgsConstructor
public class AdminOverviewController {
    private final AdminOverviewService service;

    @GetMapping("/sessions/page")
    public Result<PageResult<EventSessionVO>> sessions(@Valid AdminSessionQuery query) {
        return Result.success(service.sessions(query));
    }

    @GetMapping("/refunds/page")
    public Result<PageResult<Map<String, Object>>> refunds(@Valid AdminRefundQuery query) {
        return Result.success(service.refunds(query));
    }

    @GetMapping("/dashboard/summary")
    public Result<AdminDashboardVO> summary() {
        return Result.success(service.summary());
    }
}
