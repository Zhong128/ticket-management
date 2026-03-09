// org/example/ticketmanagement/service/EmailService.java
package org.example.ticketmanagement.service;

import org.example.ticketmanagement.pojo.Event;
import org.example.ticketmanagement.pojo.EventSession;
import org.example.ticketmanagement.pojo.TicketTier;
import org.example.ticketmanagement.pojo.User;
import org.example.ticketmanagement.dto.SeckillMessageDTO;

public interface EmailService {
    /**
     * 发送简单文本邮件
     */
    boolean sendSimpleEmail(String to, String subject, String content);

    /**
     * 发送 HTML 格式邮件
     */
    boolean sendHtmlEmail(String to, String subject, String htmlContent);

    /**
     * 发送验证码邮件
     */
    boolean sendVerificationCodeEmail(String to, String code);

    /**
     * 发送验证码邮件（HTML 格式）
     */
    boolean sendVerificationCodeHtmlEmail(String to, String code);

    /**
     * 发送抢票成功通知邮件
     * 
     * @param user 用户信息
     * @param message 抢票消息
     * @param event 演出信息
     * @param session 场次信息
     * @param ticketTier 票档信息
     * @return 是否发送成功
     */
    boolean sendSeckillSuccessEmail(User user, SeckillMessageDTO message, 
                                    Event event, EventSession session, TicketTier ticketTier);

    /**
     * 发送支付成功通知邮件
     * 
     * @param userEmail 用户邮箱
     * @param paymentNo 支付流水号
     * @param tradeNo 支付宝交易号
     * @param orderNo 订单号
     * @param totalAmount 总金额
     * @param payTime 支付时间
     * @param eventName 演出名称
     * @param sessionName 场次名称
     * @param tierName 票档名称
     * @param quantity 数量
     * @return 是否发送成功
     */
    boolean sendPaymentSuccessEmail(String userEmail, String paymentNo, String tradeNo,
                                   String orderNo, java.math.BigDecimal totalAmount,
                                   java.time.LocalDateTime payTime, String eventName,
                                   String sessionName, String tierName, Integer quantity);
}
