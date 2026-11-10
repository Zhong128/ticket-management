package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.dto.AgentActionView;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.serviceimpl.AgentActionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user/ai-customer-service")
@SaCheckLogin
@RequiredArgsConstructor
public class AgentActionController {

    private final AgentActionService service;

    @GetMapping("/conversations/{id}/actions")
    public Result<List<AgentActionView>> list(@PathVariable String id) {
        return Result.success(service.list(StpUtil.getLoginIdAsLong(), id));
    }

    @GetMapping("/actions/{id}")
    public Result<AgentActionView> get(@PathVariable String id) {
        return Result.success(service.get(StpUtil.getLoginIdAsLong(), id));
    }

    @PostMapping("/actions/{id}/confirm")
    public Result<AgentActionView> confirm(@PathVariable String id) {
        return Result.success(service.confirm(StpUtil.getLoginIdAsLong(), id));
    }

    @PostMapping("/actions/{id}/dismiss")
    public Result<AgentActionView> dismiss(@PathVariable String id) {
        return Result.success(service.dismiss(StpUtil.getLoginIdAsLong(), id));
    }
}
