// org/example/ticketmanagement/schedule/OrderTimeoutSchedule.java
package org.example.ticketmanagement.schedule;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.SeckillConstant;
import org.example.ticketmanagement.service.OrderService;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@Data
public class OrderTimeoutSchedule {

    private final OrderService orderService;
    private final RabbitAdmin rabbitAdmin;

    // 死信队列监控阈值
    private static final int DEAD_LETTER_QUEUE_CRITICAL_THRESHOLD = 500;
    private static final int DEAD_LETTER_QUEUE_WARNING_THRESHOLD = 100;

    /**
     * 主定时任务：每5分钟执行一次，作为死信队列的备份
     * 执行时间：每5分钟的第0秒（0,5,10,15...）
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void processTimeoutOrdersBackup() {
        log.debug("启动定时任务备份检查");

        try {
            // 1. 检查死信队列健康状况
            DeadLetterQueueHealthStatus healthStatus = checkDeadLetterQueueHealth();

            if (healthStatus.isHealthy()) {
                log.debug("死信队列健康，消息积压数：{}，跳过定时任务处理", healthStatus.getMessageCount());
                return;
            }

            log.warn("死信队列可能异常，当前消息积压数：{}，启动定时任务备份处理",
                    healthStatus.getMessageCount());

            // 2. 执行超时订单处理（设置11分钟，让死信队列优先处理16分钟的订单）
            int timeoutMinutes = SeckillConstant.PAY_TIMEOUT_MINUTES + 1;
            orderService.processTimeoutOrders(timeoutMinutes, 100);

        } catch (Exception e) {
            log.error("定时任务备份处理异常", e);
        }
    }

    /**
     * 死信队列健康监控：每分钟检查一次（只监控，不处理）
     */
    @Scheduled(fixedDelay = 60000)
    public void monitorDeadLetterQueue() {
        try {
            DeadLetterQueueHealthStatus healthStatus = checkDeadLetterQueueHealth();

            // 根据积压情况记录不同级别的日志
            if (healthStatus.getMessageCount() >= DEAD_LETTER_QUEUE_CRITICAL_THRESHOLD) {
                log.error("⚠️ 死信队列严重积压！消息数：{}，请立即检查！",
                        healthStatus.getMessageCount());
                // 这里可以发送告警邮件/短信
                sendAlert("死信队列严重积压", healthStatus.getMessageCount());

            } else if (healthStatus.getMessageCount() >= DEAD_LETTER_QUEUE_WARNING_THRESHOLD) {
                log.warn("警告：死信队列中度积压，消息数：{}", healthStatus.getMessageCount());

            } else if (healthStatus.getMessageCount() > 0) {
                log.info("死信队列正常，待处理消息：{}", healthStatus.getMessageCount());
            }

        } catch (Exception e) {
            log.error("死信队列监控异常", e);
        }
    }

    /**
     * 检查死信队列健康状态
     */
    private DeadLetterQueueHealthStatus checkDeadLetterQueueHealth() {
        try {
            // 获取死信队列属性
            Properties queueProps = rabbitAdmin.getQueueProperties("seckill.order.dead.queue");

            DeadLetterQueueHealthStatus status = new DeadLetterQueueHealthStatus();

            if (queueProps == null) {
                log.error("无法获取死信队列信息，队列可能不存在");
                status.setHealthy(false);
                status.setMessageCount(-1);
                status.setError("队列不存在或无法访问");
                return status;
            }

            // 获取队列中的消息数量
            int messageCount = 0;
            Object countObj = queueProps.get("QUEUE_MESSAGE_COUNT");
            if (countObj instanceof Integer) {
                messageCount = (Integer) countObj;
            } else if (countObj instanceof String) {
                messageCount = Integer.parseInt((String) countObj);
            }

            status.setMessageCount(messageCount);

            // 健康判断逻辑：
            // 1. 消息数超过500：不健康，需要立即处理
            // 2. 消息数超过100：警告状态
            // 3. 其他：健康
            if (messageCount >= DEAD_LETTER_QUEUE_CRITICAL_THRESHOLD) {
                status.setHealthy(false);
            } else if (messageCount >= DEAD_LETTER_QUEUE_WARNING_THRESHOLD) {
                status.setHealthy(true); // 仍算健康，但需要关注
            } else {
                status.setHealthy(true);
            }

            // 记录队列消费者数量（如果有）
            Object consumerCount = queueProps.get("CONSUMER_COUNT");
            if (consumerCount != null) {
                status.setConsumerCount(Integer.parseInt(consumerCount.toString()));
            }

            return status;

        } catch (Exception e) {
            log.error("检查死信队列健康状态失败", e);
            DeadLetterQueueHealthStatus status = new DeadLetterQueueHealthStatus();
            status.setHealthy(false);
            status.setError(e.getMessage());
            return status;
        }
    }

    /**
     * 发送告警（模拟，实际项目中集成邮件/短信/钉钉等）
     */
    private void sendAlert(String title, int messageCount) {
        // 实际项目中可以：
        // 1. 发送邮件
        // 2. 发送钉钉/企业微信消息
        // 3. 调用告警接口
        log.warn("发送告警：{}，当前消息积压：{}条", title, messageCount);

        // 示例：记录到数据库告警表
        // alertService.saveAlert("DEAD_LETTER_QUEUE_ALERT", title, messageCount);
    }

    /**
     * 死信队列健康状态内部类
     */
    @Setter
    @Getter
    private static class DeadLetterQueueHealthStatus {
        // getter和setter
        private boolean healthy;
        private int messageCount;
        private int consumerCount;
        private String error;

    }
}