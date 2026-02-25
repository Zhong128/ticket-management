// org/example/ticketmanagement/service/PaymentService.java
package org.example.ticketmanagement.service;

import org.example.ticketmanagement.dto.CreatePaymentDTO;
import org.example.ticketmanagement.dto.PaymentResultDTO;
import org.example.ticketmanagement.vo.PaymentRecordVO;

import java.util.Map;

/**
 * 支付服务接口
 */
public interface PaymentService {

    /**
     * 创建支付订单（自动关联用户最新待支付订单）
     * @param userId 用户ID
     * @param payType 支付方式
     * @return 支付结果（包含二维码）
     */
    PaymentResultDTO createPaymentAuto(Long userId, Integer payType);

    /**
     * 创建支付订单
     * @param userId 用户ID
     * @param createPaymentDTO 创建支付参数
     * @return 支付结果（包含二维码）
     */
    PaymentResultDTO createPayment(Long userId, CreatePaymentDTO createPaymentDTO);

    /**
     * 处理支付宝异步回调
     * @param params 回调参数
     * @return 响应支付宝的字符串（success/failure）
     */
    String handleAlipayNotify(Map<String, String> params);

    /**
     * 查询支付状态
     * @param paymentNo 支付流水号
     * @return 支付记录
     */
    PaymentRecordVO getPaymentStatus(String paymentNo);

    /**
     * 主动关闭支付订单
     * @param paymentNo 支付流水号
     * @return 是否成功
     */
    boolean closePayment(String paymentNo);

    /**
     * 处理掉单补偿
     */
    void processCompensation();

    /**
     * 处理超时关单
     */
    void processTimeoutClose();
}