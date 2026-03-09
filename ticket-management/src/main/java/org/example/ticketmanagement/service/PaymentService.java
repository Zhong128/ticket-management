// org/example/ticketmanagement/service/PaymentService.java
package org.example.ticketmanagement.service;

import org.example.ticketmanagement.dto.CreatePaymentDTO;
import org.example.ticketmanagement.dto.PaymentResultDTO;
import org.example.ticketmanagement.pojo.PaymentRecord;
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
     * 【核心】处理超时订单（延迟队列兜底）
     * @param timeoutMinutes 超时时间（分钟）
     * @return 处理的订单数量
     */
    int processTimeoutOrders(int timeoutMinutes);

    /**
     * 【补偿】处理支付状态不一致的订单（支付宝回调兜底）
     * @param waitingMinutes 等待时间（分钟）
     * @return 补偿的订单数量
     */
    int processCompensation(int waitingMinutes);

    /**
     * 【清理】清理过期的支付记录
     * @param daysToKeep 保留天数
     * @return 清理的记录数
     */
    int cleanExpiredPayments(int daysToKeep);

}