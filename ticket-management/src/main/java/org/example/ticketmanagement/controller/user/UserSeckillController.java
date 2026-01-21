// org/example/ticketmanagement/controller/user/UserSeckillController.java
package org.example.ticketmanagement.controller.user;

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
@Tag(name = "客户端/抢票", description = "抢票相关接口")
public class UserSeckillController {

    private final SeckillService seckillService;

    /**
     * 抢票接口
     * POST /api/user/seckill
     */
    @Operation(summary = "抢票", tags = {"客户端/抢票"})
    @PostMapping
    public Result<SeckillResultVO> seckill(@Valid @RequestBody SeckillOrderDTO seckillOrderDTO,
                                           @RequestAttribute Long userId) {
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
     * 检查抢票资格
     * GET /api/user/seckill/check
     */
    @Operation(summary = "检查抢票资格", tags = {"客户端/抢票"})
    @GetMapping("/check")
    public Result<Boolean> checkSeckillQualification(
            @RequestParam Long tierId,
            @RequestParam Integer quantity,
            @RequestAttribute Long userId) {
        log.debug("检查抢票资格: userId={}, tierId={}, quantity={}", userId, tierId, quantity);

        try {
            boolean qualified = seckillService.checkSeckillQualification(userId, tierId, quantity);
            return Result.success("查询成功", qualified);
        } catch (Exception e) {
            log.error("检查抢票资格异常: userId={}, tierId={}", userId, tierId, e);
            return Result.error("查询失败");
        }
    }

    /**
     * 获取实时库存
     * GET /api/user/seckill/stock/{tierId}
     */
    @Operation(summary = "获取实时库存", tags = {"客户端/抢票"})
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