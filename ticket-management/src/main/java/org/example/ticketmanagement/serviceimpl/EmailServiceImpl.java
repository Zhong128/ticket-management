package org.example.ticketmanagement.serviceimpl;

import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

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
}
