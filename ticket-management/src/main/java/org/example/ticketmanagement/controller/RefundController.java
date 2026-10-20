package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.*;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.trade.payment.RefundService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refunds;

    @GetMapping("/user/refunds")
    @SaCheckLogin
    public Result<List<Map<String, Object>>> mine() {
        return Result.success("查询成功", refunds.forUser(StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/admin/refunds")
    @SaCheckRole(value = { "ADMIN", "SUPER_ADMIN" }, mode = SaMode.OR)
    public Result<List<Map<String, Object>>> all() {
        return Result.success("查询成功", refunds.forAdmin());
    }

    @PostMapping("/admin/refunds/{refundNo}/retry")
    @SaCheckRole(value = { "ADMIN", "SUPER_ADMIN" }, mode = SaMode.OR)
    public Result<Boolean> retry(@PathVariable String refundNo) {
        return Result.success("已受理", refunds.retry(refundNo));
    }
}
