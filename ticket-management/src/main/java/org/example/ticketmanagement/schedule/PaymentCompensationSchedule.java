// org/example/ticketmanagement/schedule/PaymentCompensationSchedule.java
package org.example.ticketmanagement.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.PaymentConstant;
import org.example.ticketmanagement.service.PaymentService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class PaymentCompensationSchedule {

    private final PaymentService paymentService;

    /**
     * 支付补偿任务：每5分钟执行一次
     * 用于处理支付宝回调通知丢失的情况
     */
    @Scheduled(cron = "0 0  * * * *")
    public void processPaymentCompensation() {
        log.info("启动支付补偿定时任务");

        try {
            long startTime = System.currentTimeMillis();

            // 执行补偿处理
            paymentService.processCompensation();

            long costTime = System.currentTimeMillis() - startTime;
            log.info("支付补偿定时任务执行完成，耗时：{}ms", costTime);

        } catch (Exception e) {
            log.error("支付补偿定时任务执行异常", e);
        }
    }

    /**
     * 超时关单任务：每1分钟执行一次
     * 用于处理30分钟内未支付且支付宝状态不是成功的订单
     */
    @Scheduled(cron = "0 0  * * * *")
    public void processTimeoutClose() {
        log.debug("启动超时关单定时任务");

        try {
            long startTime = System.currentTimeMillis();

            // 执行超时关单处理
            paymentService.processTimeoutClose();

            long costTime = System.currentTimeMillis() - startTime;

            if (costTime > 1000) {
                log.info("超时关单任务执行完成，耗时：{}ms", costTime);
            } else {
                log.debug("超时关单任务执行完成，耗时：{}ms", costTime);
            }

        } catch (Exception e) {
            log.error("超时关单定时任务执行异常", e);
        }
    }

    /**
     * 支付状态监控：每10分钟执行一次
     * 用于统计支付异常情况
     */
    @Scheduled(cron = "0 0  * * * *")
    public void monitorPaymentStatus() {
        log.info("启动支付状态监控任务");

        try {
            // TODO: 可以查询支付异常情况并发送告警
            // 例如：长时间处于待支付状态的订单数、补偿任务失败数等
            log.debug("支付状态监控任务执行完成");

        } catch (Exception e) {
            log.error("支付状态监控任务执行异常", e);
        }
    }
}