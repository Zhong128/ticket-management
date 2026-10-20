package org.example.ticketmanagement.serviceimpl;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.config.AlipayProperties;
import org.example.ticketmanagement.dto.*;
import org.example.ticketmanagement.service.PaymentService;
import org.example.ticketmanagement.trade.payment.PaymentCoordinator;
import org.example.ticketmanagement.util.AlipaySignatureUtil;
import org.example.ticketmanagement.vo.PaymentRecordVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentCoordinator payments;
    private final AlipaySignatureUtil signature;
    private final AlipayProperties properties;

    public PaymentResultDTO createPayment(Long user, CreatePaymentDTO dto) {
        // 创建或复用支付流水，再组装给前端展示的支付信息。
        var created = payments.create(user, dto);
        var p = created.payment();

        var result = new PaymentResultDTO();
        result.setPaymentNo(p.getPaymentNo());
        result.setOrderNo(p.getOrderNo());
        result.setPayStatus(p.getPayStatus());
        result.setPayStatusDesc(p.getPayStatusDesc());
        result.setTotalAmount(p.getTotalAmount());
        result.setExpireTime(p.getExpireTime().toString());
        result.setQrCode(created.page());
        return result;
    }

    public PaymentResultDTO createPaymentAuto(Long user, Integer type) {
        return createPayment(user, new CreatePaymentDTO(null, type));
    }

    public String handleAlipayNotify(Map<String, String> params) {
        try {
            // 1. 先验签并核对应用 ID，拒绝来源不明的回调。
            if (!signature.verifyAlipaySignature(params) || properties.getAppId() == null
                    || !properties.getAppId().equals(params.get("app_id"))) {
                return "failure";
            }

            // 2. 核对支付流水、渠道交易号和金额。
            String no = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            BigDecimal amount = new BigDecimal(params.get("total_amount"));
            payments.validateNotification(no, tradeNo, amount);

            // 3. 支付成功则入账；其他状态主动查询渠道后同步。
            String status = params.get("trade_status");
            if ("TRADE_SUCCESS".equals(status) || "TRADE_FINISHED".equals(status)) {
                payments.settle(no, tradeNo, amount);
            } else {
                payments.synchronize(no);
            }
            return "success";
        } catch (RuntimeException e) {
            return "failure";
        }
    }

    public PaymentRecordVO getPaymentStatus(String no) {
        long user = StpUtil.getLoginIdAsLong();

        // 先确认归属，再查询渠道并返回最新支付状态。
        payments.owned(no, user);
        payments.synchronize(no);

        var vo = new PaymentRecordVO();
        BeanUtils.copyProperties(payments.owned(no, user), vo);
        return vo;
    }

    public boolean closePayment(String no) {
        return payments.closeOwned(no, StpUtil.getLoginIdAsLong());
    }

    public int processTimeoutOrders(int minutes) {
        return payments.closeExpired();
    }

    public int processCompensation(int minutes) {
        return payments.compensate();
    }

    /**
     * 支付流水作为财务记录保留，不执行过期删除。
     */
    public int cleanExpiredPayments(int days) {
        return 0;
    }
}
