package org.example.ticketmanagement.trade.payment;

import com.alipay.api.AlipayClient;
import com.alipay.api.domain.*;
import com.alipay.api.request.*;
import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.config.AlipayProperties;
import org.example.ticketmanagement.pojo.PaymentRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class AlipayPaymentGateway implements PaymentGateway {

    private final AlipayClient client;
    private final AlipayProperties properties;

    public String create(PaymentRecord p) {
        try {
            // 将本地支付流水和订单截止时间传给支付宝。
            var model = new AlipayTradePagePayModel();
            model.setOutTradeNo(p.getPaymentNo());
            model.setTotalAmount(p.getTotalAmount().toPlainString());
            model.setSubject(p.getSubject());
            model.setProductCode("FAST_INSTANT_TRADE_PAY");
            model.setTimeExpire(p.getExpireTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            var request = new AlipayTradePagePayRequest();
            request.setBizModel(model);
            request.setNotifyUrl(properties.getNotifyUrl());
            request.setReturnUrl(properties.getReturnUrl());
            var response = client.pageExecute(request);
            if (!response.isSuccess()) {
                throw new IllegalStateException("支付页面暂不可用");
            }
            return response.getBody();
        } catch (Exception e) {
            throw new IllegalStateException("支付页面暂不可用，请使用原支付流水重试", e);
        }
    }

    public Query query(String no) {
        try {
            // 只把明确的“交易不存在”映射为未创建，其余异常保留未知状态。
            var model = new AlipayTradeQueryModel();
            model.setOutTradeNo(no);
            var request = new AlipayTradeQueryRequest();
            request.setBizModel(model);
            var r = client.execute(request);
            if (!r.isSuccess()) {
                return "ACQ.TRADE_NOT_EXIST".equals(r.getSubCode()) ? new Query(State.NOT_FOUND, no, null, null)
                        : Query.unknown();
            }
            if (!no.equals(r.getOutTradeNo())) {
                return Query.unknown();
            }

            // 渠道状态统一映射为本地关单和入账所需的状态。
            State state = switch (String.valueOf(r.getTradeStatus())) {
            case "TRADE_SUCCESS", "TRADE_FINISHED" -> State.PAID;
            case "WAIT_BUYER_PAY" -> State.WAITING;
            case "TRADE_CLOSED" -> State.CLOSED;
            default -> State.UNKNOWN;
            };
            return new Query(state, r.getOutTradeNo(), r.getTradeNo(),
                    r.getTotalAmount() == null ? null : new BigDecimal(r.getTotalAmount()));
        } catch (Exception e) {
            return Query.unknown();
        }
    }

    public boolean close(String no) {
        try {
            // 渠道确认关闭且流水号一致，才返回关单成功。
            var m = new AlipayTradeCloseModel();
            m.setOutTradeNo(no);
            var q = new AlipayTradeCloseRequest();
            q.setBizModel(m);
            var r = client.execute(q);
            return r.isSuccess() && no.equals(r.getOutTradeNo());
        } catch (Exception e) {
            return false;
        }
    }

    public RefundResult refund(String no, String refundNo, BigDecimal amount) {
        try {
            // 退款请求使用持久化退款单号，重试时保持幂等。
            var m = new AlipayTradeRefundModel();
            m.setOutTradeNo(no);
            m.setOutRequestNo(refundNo);
            m.setRefundAmount(amount.toPlainString());
            var q = new AlipayTradeRefundRequest();
            q.setBizModel(m);
            var r = client.execute(q);
            if (r.isSuccess() && no.equals(r.getOutTradeNo())
                    && amount.compareTo(new BigDecimal(r.getRefundFee())) == 0) {
                return new RefundResult(RefundState.SUCCESS, r.getTradeNo(), null);
            }
            return new RefundResult(RefundState.UNKNOWN, r.getTradeNo(), r.getSubCode());
        } catch (Exception e) {
            return new RefundResult(RefundState.UNKNOWN, null, "CHANNEL_UNCERTAIN");
        }
    }

    public RefundResult queryRefund(String no, String refundNo, BigDecimal amount) {
        try {
            // 先核对渠道退款结果，避免进程恢复后重复发起退款。
            var m = new AlipayTradeFastpayRefundQueryModel();
            m.setOutTradeNo(no);
            m.setOutRequestNo(refundNo);
            var q = new AlipayTradeFastpayRefundQueryRequest();
            q.setBizModel(m);
            var r = client.execute(q);
            if (r.isSuccess() && "REFUND_SUCCESS".equals(r.getRefundStatus()) && no.equals(r.getOutTradeNo())
                    && amount.compareTo(new BigDecimal(r.getRefundAmount())) == 0) {
                return new RefundResult(RefundState.SUCCESS, r.getTradeNo(), null);
            }
            if ("ACQ.TRADE_NOT_EXIST".equals(r.getSubCode()) || "ACQ.REFUND_NOT_EXIST".equals(r.getSubCode())) {
                return new RefundResult(RefundState.NOT_FOUND, null, r.getSubCode());
            }
            return new RefundResult(RefundState.UNKNOWN, r.getTradeNo(), r.getSubCode());
        } catch (Exception e) {
            return new RefundResult(RefundState.UNKNOWN, null, "CHANNEL_UNCERTAIN");
        }
    }
}
