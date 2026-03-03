
package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.SeckillOrderDTO;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.service.SeckillService;
import org.example.ticketmanagement.vo.SeckillResultVO;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/user/seckill")
@RequiredArgsConstructor
@SaCheckLogin
@Tag(name = "抢票", description = "抢票相关接口")
public class SeckillController {

    private final SeckillService seckillService;

    /**
     * 抢票接口
     */
    @Operation(summary = "抢票", tags = {"抢票"})
    @PostMapping
    public Result<SeckillResultVO> seckill(@Valid @RequestBody SeckillOrderDTO seckillOrderDTO) {
        Long userId = StpUtil.getLoginIdAsLong();
        log.info("用户抢票请求: userId={}, tierId={}, quantity={}",
                userId, seckillOrderDTO.getTierId(), seckillOrderDTO.getQuantity());

        try {
            SeckillResultVO result = seckillService.seckill(userId, seckillOrderDTO);

            if (result.getSuccess()) {
                return Result.success("抢票成功", result);
            } else {
                return Result.error(result.getMessage());
            }
        } catch (Exception e) {
            log.error("抢票异常: userId={}, tierId={}",
                    userId, seckillOrderDTO.getTierId(), e);
            return Result.error("系统繁忙，请稍后重试");
        }
    }

    /**
     * 检查当前能否抢票
     */
    @Operation(summary = "检查当前能否抢票", tags = {"抢票"})
    @GetMapping("/check")
    public Result<Boolean> checkSeckillQualification(
            @RequestParam Long tierId,
            @RequestParam Integer quantity) {
        Long userId = StpUtil.getLoginIdAsLong();
        log.debug("检查抢票资格: userId={}, tierId={}, quantity={}", userId, tierId, quantity);

        try {
            boolean qualified = seckillService.checkSeckillQualification(userId, tierId, quantity);
            if (!qualified) {
                return Result.error("无抢票资格，请勿重复抢票或检查票档是否合法");
            }
            return Result.success("当前可以抢票");
        } catch (Exception e) {
            log.error("检查抢票资格异常: userId={}, tierId={}", userId, tierId, e);
            return Result.error("查询失败");
        }
    }

    /**
     * 获取实时库存（自己测试用，实现上线可删除）
     */
    @Operation(summary = "获取实时库存", tags = {"抢票"})
    @GetMapping("/stock/{tierId}")
    public Result<Integer> getRealTimeStock(@PathVariable Long tierId) {
        log.debug("获取实时库存: tierId={}", tierId);

        try {
            Integer stock = seckillService.getRealTimeStock(tierId);
            if (stock != null) {
                return Result.success("查询成功", stock);
            } else {
                return Result.error("库存信息不存在");
            }
        } catch (Exception e) {
            log.error("获取实时库存异常: tierId={}", tierId, e);
            return Result.error("查询失败");
        }
    }
}