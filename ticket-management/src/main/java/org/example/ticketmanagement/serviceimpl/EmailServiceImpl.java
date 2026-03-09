package org.example.ticketmanagement.serviceimpl;

import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.SeckillConstant;
import org.example.ticketmanagement.dto.SeckillMessageDTO;
import org.example.ticketmanagement.pojo.Event;
import org.example.ticketmanagement.pojo.EventSession;
import org.example.ticketmanagement.pojo.TicketTier;
import org.example.ticketmanagement.pojo.User;
import org.example.ticketmanagement.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    


    @Override
    public boolean sendSimpleEmail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);

            mailSender.send(message);
            log.info("简单邮件发送成功，收件人: {}", to);
            return true;
        } catch (Exception e) {
            log.error("简单邮件发送失败，收件人: {}，原因: {}", to, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true表示发送HTML格式

            mailSender.send(message);
            log.info("HTML邮件发送成功，收件人: {}", to);
            return true;
        } catch (MessagingException e) {
            log.error("HTML邮件发送失败，收件人: {}，原因: {}", to, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean sendVerificationCodeEmail(String to, String code) {
        String subject = "您的验证码 - 票务管理系统";
        String content = buildVerificationCodeContent(code);

        return sendSimpleEmail(to, subject, content);
    }

    @Override
    public boolean sendVerificationCodeHtmlEmail(String to, String code) {
        String subject = "您的验证码 - 票务管理系统";
        String htmlContent = buildVerificationCodeHtmlContent(code);

        return sendHtmlEmail(to, subject, htmlContent);
    }

    /**
     * 构建验证码邮件内容（纯文本）
     */
    private String buildVerificationCodeContent(String code) {
        return String.format(
                """
                        尊敬的用户：
                        
                        您的验证码是：%s
                        
                        此验证码将在15分钟后失效，请勿泄露给他人。
                        
                        如果不是您本人操作，请忽略此邮件。
                        
                        感谢使用我们的服务！
                        票务管理系统团队""",
                code
        );
    }

    /**
     * 构建验证码邮件内容（HTML格式）
     */
    private String buildVerificationCodeHtmlContent(String code) {
        return String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <title>验证码邮件</title>
                            <style>
                                .container {
                                    max-width: 600px;
                                    margin: 0 auto;
                                    padding: 20px;
                                    font-family: Arial, sans-serif;
                                }
                                .code {
                                    font-size: 24px;
                                    font-weight: bold;
                                    color: #1890ff;
                                    padding: 10px 20px;
                                    background-color: #f5f5f5;
                                    border-radius: 4px;
                                    display: inline-block;
                                    margin: 10px 0;
                                }
                                .footer {
                                    margin-top: 20px;
                                    color: #999;
                                    font-size: 12px;
                                }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <h2>验证码邮件</h2>
                                <p>尊敬的用户：</p>
                                <p>您正在使用邮箱进行注册/登录操作，验证码如下：</p>
                                <div class="code">%s</div>
                                <p>此验证码将在15分钟后失效，请勿泄露给他人。</p>
                                <p>如果不是您本人操作，请忽略此邮件。</p>
                                <div class="footer">
                                    <p>感谢使用我们的服务！</p>
                                    <p>票务管理系统团队</p>
                                </div>
                            </div>
                        </body>
                        </html>""",
                code
        );
    }

    @Override
    public boolean sendSeckillSuccessEmail(User user, SeckillMessageDTO message, 
                                           Event event, EventSession session, TicketTier ticketTier) {
        if (user == null || user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            log.warn("用户邮箱不存在，无法发送邮件：userId={}", user != null ? user.getId() : "null");
            return false;
        }

        try {
            String userEmail = user.getEmail();
            String subject = "🎉 抢票成功！请按时完成支付 - " + (event.getName() != null ? event.getName() : "未知演出");
            String htmlContent = buildSeckillSuccessEmailHtml(user, message, event, session, ticketTier);

            boolean sent = sendHtmlEmail(userEmail, subject, htmlContent);
            if (sent) {
                log.info("抢票成功邮件发送成功：userId={}, email={}", user.getId(), userEmail);
            } else {
                log.error("抢票成功邮件发送失败：userId={}, email={}", user.getId(), userEmail);
            }
            return sent;
        } catch (Exception e) {
            log.error("发送抢票成功邮件异常：userId={}", user.getId(), e);
            return false;
        }
    }

    @Override
    public boolean sendPaymentSuccessEmail(String userEmail, String paymentNo, String tradeNo,
                                          String orderNo, java.math.BigDecimal totalAmount,
                                          java.time.LocalDateTime payTime, String eventName,
                                          String sessionName, String tierName, Integer quantity) {
        if (userEmail == null || userEmail.trim().isEmpty()) {
            log.warn("用户邮箱为空，无法发送邮件：orderNo={}", orderNo);
            return false;
        }

        try {
            String subject = "🎉 支付成功！订单已完成 - " + (eventName != null ? eventName : "演出");
            String htmlContent = buildPaymentSuccessEmailHtml(
                paymentNo, tradeNo, orderNo, totalAmount, payTime, 
                eventName, sessionName, tierName, quantity
            );

            boolean sent = sendHtmlEmail(userEmail, subject, htmlContent);
            if (sent) {
                log.info("支付成功邮件发送成功：orderNo={}, email={}", orderNo, userEmail);
            } else {
                log.error("支付成功邮件发送失败：orderNo={}", orderNo);
            }
            return sent;
        } catch (Exception e) {
            log.error("发送支付成功邮件异常：orderNo={}", orderNo, e);
            return false;
        }
    }

    /**
     * 构建支付成功邮件 HTML 内容
     */
    private String buildPaymentSuccessEmailHtml(String paymentNo, String tradeNo, String orderNo,
                                                java.math.BigDecimal totalAmount,
                                                java.time.LocalDateTime payTime,
                                                String eventName, String sessionName,
                                                String tierName, Integer quantity) {
        String payTimeStr = payTime != null ? 
                payTime.format(DATE_TIME_FORMATTER) : "未知";
        
        return String.format("""
                <!DOCTYPE html>
                <html lang='zh-CN'>
                <head>
                    <meta charset='UTF-8'>
                    <meta name='viewport' content='width=device-width, initial-scale=1.0'>
                    <title>支付成功通知</title>
                    <style>
                        body { font-family: 'Microsoft YaHei', Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                        .header h1 { margin: 0; font-size: 28px; }
                        .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                        .success-icon { font-size: 60px; text-align: center; display: block; margin-bottom: 20px; }
                        .info-box { background: white; border-left: 4px solid #667eea; padding: 15px; margin: 15px 0; border-radius: 5px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }
                        .info-item { margin: 10px 0; }
                        .info-label { font-weight: bold; color: #667eea; }
                        .price-box { background: linear-gradient(135deg, #f093fb 0%%, #f5576c 100%%); color: white; padding: 20px; text-align: center; border-radius: 10px; margin: 20px 0; }
                        .price-amount { font-size: 36px; font-weight: bold; }
                        .footer { text-align: center; margin-top: 30px; color: #999; font-size: 12px; }
                    </style>
                </head>
                <body>
                    <div class='header'>
                        <span class='success-icon'>✅</span>
                        <h1>支付成功！</h1>
                        <p style='margin: 10px 0 0 0; font-size: 16px;'>恭喜您，订单已支付完成！</p>
                    </div>
                    <div class='content'>
                        <div class='info-box'>
                            <h3 style='margin-top: 0;'>📋 订单信息</h3>
                            <div class='info-item'><span class='info-label'>🔢 订单编号：</span>%s</div>
                            <div class='info-item'><span class='info-label'>💳 支付流水号：</span>%s</div>
                            <div class='info-item'><span class='info-label'>🏦 支付宝交易号：</span>%s</div>
                            <div class='info-item'><span class='info-label'>⏰ 支付时间：</span>%s</div>
                        </div>
                        
                        <div class='info-box'>
                            <h3 style='margin-top: 0;'>🎫 演出信息</h3>
                            <div class='info-item'><span class='info-label'>📺 演出名称：</span>%s</div>
                            <div class='info-item'><span class='info-label'>🎭 场次信息：</span>%s</div>
                            <div class='info-item'><span class='info-label'>🎟️ 票档类型：</span>%s</div>
                            <div class='info-item'><span class='info-label'>📦 购买数量：</span>%s 张</div>
                        </div>
                        
                        <div class='price-box'>
                            <div style='font-size: 14px; margin-bottom: 10px;'>订单总金额</div>
                            <div class='price-amount'>¥%s</div>
                        </div>
                        
                        <div class='info-box' style='background: #e8f5e9; border-left-color: #4caf50;'>
                            <h3 style='margin-top: 0; color: #2e7d32;'>💡 温馨提示</h3>
                            <ul style='margin: 10px 0; padding-left: 20px; color: #1b5e20;'>
                                <li>请妥善保管此邮件，入场时需出示订单信息</li>
                                <li>如有疑问，请联系客服或在系统中查看订单详情</li>
                                <li>本演出实行一票一证制，请携带有效身份证件</li>
                                <li>门票一经售出，不支持退换，请合理安排时间</li>
                            </ul>
                        </div>
                        
                        <div class='footer'>
                            <p>票务管理系统 敬上</p>
                            <p>本邮件为系统自动发送，请勿回复</p>
                            <p>© 2026 票务管理系统。All rights reserved.</p>
                        </div>
                    </div>
                </body>
                </html>""",
                orderNo,
                paymentNo,
                tradeNo,
                payTimeStr,
                eventName,
                sessionName,
                tierName,
                quantity,
                totalAmount
        );
    }

    /**
     * 构建抢票成功邮件 HTML 内容
     */
    private String buildSeckillSuccessEmailHtml(User user, SeckillMessageDTO message, 
                                                 Event event, EventSession session, TicketTier ticketTier) {
        String username = user.getUsername() != null ? user.getUsername() : "尊敬的用户";
        String eventName = event.getName() != null ? event.getName() : "未知演出";
        String sessionName = session.getSessionName() != null ? session.getSessionName() : "待定场次";
        String sessionTime = session.getSessionTime() != null ? 
                session.getSessionTime().format(DATE_TIME_FORMATTER) : "待定";
        String venueName = event.getVenue() != null ? event.getVenue() : "待定场馆";
        String tierName = ticketTier.getTierName() != null ? ticketTier.getTierName() : "未知票档";
        
        return String.format("""
                <!DOCTYPE html>
                <html lang='zh-CN'>
                <head>
                    <meta charset='UTF-8'>
                    <meta name='viewport' content='width=device-width, initial-scale=1.0'>
                    <title>抢票成功通知</title>
                    <style>
                        body { font-family: 'Microsoft YaHei', Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                        .header h1 { margin: 0; font-size: 28px; }
                        .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                        .success-icon { font-size: 60px; text-align: center; display: block; margin-bottom: 20px; }
                        .info-box { background: white; border-left: 4px solid #667eea; padding: 15px; margin: 15px 0; border-radius: 5px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }
                        .info-item { margin: 10px 0; }
                        .info-label { font-weight: bold; color: #667eea; }
                        .price-box { background: linear-gradient(135deg, #f093fb 0%%, #f5576c 100%%); color: white; padding: 20px; text-align: center; border-radius: 10px; margin: 20px 0; }
                        .price-amount { font-size: 36px; font-weight: bold; }
                        .warning-box { background: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 15px 0; border-radius: 5px; }
                        .warning-text { color: #856404; font-weight: bold; }
                        .timer-box { background: #d1ecf1; border-left: 4px solid #17a2b8; padding: 15px; margin: 15px 0; border-radius: 5px; text-align: center; }
                        .timer-text { color: #0c5460; font-size: 16px; font-weight: bold; }
                        .footer { text-align: center; margin-top: 30px; color: #999; font-size: 12px; }
                    </style>
                </head>
                <body>
                    <div class='header'>
                        <span class='success-icon'>✅</span>
                        <h1>抢票成功！</h1>
                        <p style='margin: 10px 0 0 0; font-size: 16px;'>恭喜您，%s！</p>
                    </div>
                    <div class='content'>
                        <div class='info-box'>
                            <div class='info-item'><span class='info-label'>📺 演出名称：</span>%s</div>
                            <div class='info-item'><span class='info-label'>🎫 场次信息：</span>%s</div>
                            <div class='info-item'><span class='info-label'>⏰ 演出时间：</span>%s</div>
                            <div class='info-item'><span class='info-label'>📍 演出地点：</span>%s</div>
                            <div class='info-item'><span class='info-label'>🎟️ 票档类型：</span>%s</div>
                            <div class='info-item'><span class='info-label'>🔢 订单编号：</span>%s</div>
                            <div class='info-item'><span class='info-label'>📦 购买数量：</span>%s 张</div>
                        </div>
                        <div class='price-box'>
                            <div style='font-size: 14px; margin-bottom: 10px;'>订单总金额</div>
                            <div class='price-amount'>¥%s</div>
                            <div style='font-size: 12px; margin-top: 10px; opacity: 0.9;'>单价：¥%s × %s 张</div>
                        </div>
                        <div class='timer-box'>
                            <div class='timer-text'>⏳ 请在 %d 分钟内完成支付</div>
                            <div style='color: #0c5460; font-size: 14px; margin-top: 8px;'>超时未支付订单将自动取消，库存将回退</div>
                        </div>
                        <div class='warning-box'>
                            <div class='warning-text'>⚠️ 重要提示：</div>
                            <ul style='margin: 10px 0; padding-left: 20px; color: #856404;'>
                                <li>请尽快登录系统完成支付，逾期订单将自动取消</li>
                                <li>本演出实行一票一证制，请确保观演人信息准确</li>
                                <li>门票一经售出，不支持退换，请合理安排时间</li>
                                <li>如有疑问，请联系客服或在系统中查看订单详情</li>
                            </ul>
                        </div>
                        <div class='footer'>
                            <p>票务管理系统 敬上</p>
                            <p>本邮件为系统自动发送，请勿回复</p>
                            <p>© 2026 票务管理系统。All rights reserved.</p>
                        </div>
                    </div>
                </body>
                </html>""",
                username,
                eventName,
                sessionName,
                sessionTime,
                venueName,
                tierName,
                message.getOrderNo(),
                message.getQuantity(),
                message.getTotalAmount(),
                message.getPrice(),
                message.getQuantity(),
                SeckillConstant.PAY_TIMEOUT_MINUTES
        );
    }
}
