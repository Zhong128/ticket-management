// org/example/ticketmanagement/controller/PaymentController.java
package org.example.ticketmanagement.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.RoleConstant;
import org.example.ticketmanagement.dto.CreatePaymentDTO;
import org.example.ticketmanagement.dto.PayTypeDTO;
import org.example.ticketmanagement.dto.PaymentResultDTO;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.service.PaymentService;
import org.example.ticketmanagement.util.AlipaySignatureUtil;
import org.example.ticketmanagement.vo.PaymentRecordVO;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "支付管理", description = "支付相关所有接口")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final AlipaySignatureUtil alipaySignatureUtil;

    // ==================== 客户端接口（需要登录） ====================
    /**
     * 指定orderId创建支付；缺省时仅兼容恰有一笔可支付订单。
     */
    @Operation(summary = "开启订单支付（展示支付宝二维码）", tags = { "支付-客户端" })
    @PostMapping("/user/payments/create")
    @SaCheckLogin
    public Result<PaymentResultDTO> createPayment(@Valid @RequestBody PayTypeDTO payTypeDTO) {
        Long userId = StpUtil.getLoginIdAsLong();
        log.info("用户创建支付订单: userId={}, payType={}", userId, payTypeDTO.getPayType());
        try {
            PaymentResultDTO result = paymentService.createPayment(userId,
                    new CreatePaymentDTO(payTypeDTO.getOrderId(), payTypeDTO.getPayType()));
            return Result.success("创建支付订单成功", result);
        } catch (Exception e) {
            log.error("创建支付订单失败: userId={}, payType={}", userId, payTypeDTO.getPayType(), e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 2. 查询支付状态
     */
    @Operation(summary = "查询支付状态", tags = { "支付-客户端" })
    @GetMapping("/user/payments/status/{paymentNo}")
    @SaCheckLogin
    public Result<PaymentRecordVO> getPaymentStatus(@PathVariable String paymentNo) {
        Long userId = StpUtil.getLoginIdAsLong();
        log.debug("用户查询支付状态: userId={}, paymentNo={}", userId, paymentNo);
        try {
            PaymentRecordVO paymentRecord = paymentService.getPaymentStatus(paymentNo);
            return Result.success("查询成功", paymentRecord);
        } catch (Exception e) {
            log.error("查询支付状态失败: userId={}, paymentNo={}", userId, paymentNo, e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 3. 关闭支付订单（客户端）
     * 作用：仅关闭支付记录，不影响订单状态
     * 场景：用户关闭支付窗口、暂时不想支付
     */
    @Operation(summary = "用户关闭支付", tags = { "支付-客户端" })
    @PutMapping("/user/payments/close/{paymentNo}")
    @SaCheckLogin
    public Result<Void> closePayment(@PathVariable String paymentNo) {
        Long userId = StpUtil.getLoginIdAsLong();
        log.info("用户关闭支付订单: userId={}, paymentNo={}", userId, paymentNo);
        try {
            boolean success = paymentService.closePayment(paymentNo);
            if (success) {
                return Result.success("关闭支付订单成功");
            } else {
                return Result.error("关闭支付订单失败");
            }
        } catch (Exception e) {
            log.error("关闭支付订单异常: userId={}, paymentNo={}", userId, paymentNo, e);
            return Result.error(e.getMessage());
        }
    }

    // ==================== 支付宝回调接口（无需登录） ====================
    /**
     * 4. 支付宝异步回调通知
     */
    @Operation(summary = "支付宝异步回调", tags = { "支付-回调" }, hidden = true)
    @PostMapping("/public/payments/notify")
    public String alipayNotify(HttpServletRequest request) {
        log.info("收到支付宝异步回调请求");
        try {
            // 获取所有参数
            Map<String, String> params = new HashMap<>();
            Map<String, String[]> requestParams = request.getParameterMap();
            for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
                String name = entry.getKey();
                String[] values = entry.getValue();
                if (values != null && values.length > 0) {
                    params.put(name, values[0]);
                }
            }
            // 处理回调
            return paymentService.handleAlipayNotify(params);
        } catch (Exception e) {
            log.error("处理支付宝回调异常", e);
            return "failure";
        }
    }

    /**
     * 5. 支付宝同步回调（用户支付成功后跳转页面）
     * 注意：此接口是支付宝页面跳转，不能加登录拦截
     */
    @Operation(summary = "支付宝同步回调", tags = { "支付-回调" }, hidden = true)
    @GetMapping("/public/payments/return")
    public Result<String> alipayReturn(HttpServletRequest request) {
        log.info("收到支付宝同步回调请求");
        try {
            // 获取所有参数
            Map<String, String> params = new HashMap<>();
            Map<String, String[]> requestParams = request.getParameterMap();
            for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
                String name = entry.getKey();
                String[] values = entry.getValue();
                if (values != null && values.length > 0) {
                    params.put(name, values[0]);
                }
            }
            if (!alipaySignatureUtil.verifyAlipayReturnSignature(params)) {
                return Result.error("验签失败");
            }
            String outTradeNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            String totalAmount = params.get("total_amount");
            // 实际项目中可能需要验证签名
            // 这里简单返回成功信息
            return Result.success("支付结果待服务端确认", "请查询订单支付状态，流水号：" + outTradeNo);
        } catch (Exception e) {
            log.error("处理支付宝同步回调异常", e);
            return Result.error("支付处理异常");
        }
    }

    // ==================== 管理端接口（需要管理员权限） ====================
    /**
     * 6. 管理员手动触发补偿任务
     */
    @Operation(summary = "手动触发支付补偿", tags = { "支付 - 管理端" })
    @PostMapping("/admin/payments/compensation")
    @SaCheckRole(value = { RoleConstant.ADMIN, RoleConstant.SUPER_ADMIN }, mode = SaMode.OR)
    public Result<String> triggerCompensation(@RequestParam(defaultValue = "10") int waitingMinutes) {
        Long adminId = StpUtil.getLoginIdAsLong();
        log.info("管理员手动触发支付补偿：adminId={}, waitingMinutes={}", adminId, waitingMinutes);
        try {
            // 异步执行补偿任务，避免接口超时
            paymentService.processCompensation(waitingMinutes);
            return Result.success("补偿任务已触发，请查看日志");
        } catch (Exception e) {
            log.error("手动触发补偿任务失败：adminId={}", adminId, e);
            return Result.error("触发失败：" + e.getMessage());
        }
    }

    /**
     * 7. 管理员手动触发超时关单
     */
    @Operation(summary = "手动触发超时关单", tags = { "支付 - 管理端" })
    @PostMapping("/admin/payments/timeout-close")
    @SaCheckRole(value = { RoleConstant.ADMIN, RoleConstant.SUPER_ADMIN }, mode = SaMode.OR)
    public Result<String> triggerTimeoutClose(@RequestParam(defaultValue = "35") int timeoutMinutes) {
        Long adminId = StpUtil.getLoginIdAsLong();
        log.info("管理员手动触发超时关单：adminId={}, timeoutMinutes={}", adminId, timeoutMinutes);
        try {
            // 异步执行超时关单任务，避免接口超时
            paymentService.processTimeoutOrders(timeoutMinutes);
            return Result.success("超时关单任务已触发，请查看日志");
        } catch (Exception e) {
            log.error("手动触发超时关单失败：adminId={}", adminId, e);
            return Result.error("触发失败：" + e.getMessage());
        }
    }
}
