package org.example.ticketmanagement.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.constant.OrderConstant;
import org.example.ticketmanagement.constant.PaymentConstant;
import org.example.ticketmanagement.service.PaymentService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付补偿与超时关单定时任务
 * 
 * 三层保障体系：
 * 1. 第一层：RabbitMQ 延迟队列（30 分钟后触发）- 主力
 * 2. 第二层：定时任务兜底（每 5 分钟轮询）- 兜底
 * 3. 第三层：支付宝状态查询（每 10 分钟）- 最终核查
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class PaymentCompensationSchedule {

    private final PaymentService paymentService;

    /**
     * 【核心】超时订单检查与关单任务：每 5 分钟执行一次
     * 
     * 作用：
     * 1. 检查超过 30 分钟未支付的订单
     * 2. 查询支付宝交易状态进行二次确认
     * 3. 关闭本地订单并恢复库存
     * 
     * 兜底场景：
     * - RabbitMQ 延迟队列失效（消息丢失、Consumer 故障）
     * - 支付宝回调通知丢失
     * - 网络故障导致状态不一致
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void processTimeoutOrders() {
        log.info("启动超时订单检查任务（延迟队列兜底）");

        try {
            long startTime = System.currentTimeMillis();

            // 查询超时订单（30 分钟 + 5 分钟容错）
            int timeoutMinutes = OrderConstant.ORDER_TIMEOUT_MINUTES + 5;
            
            // 执行超时关单处理
            int processedCount = paymentService.processTimeoutOrders(timeoutMinutes);

            long costTime = System.currentTimeMillis() - startTime;
            
            if (processedCount > 0) {
                log.info("✅ 超时订单检查完成：处理了{}个超时订单，耗时：{}ms", 
                        processedCount, costTime);
            } else {
                log.debug("超时订单检查完成：没有超时订单，耗时：{}ms", costTime);
            }

        } catch (Exception e) {
            log.error("❌ 超时订单检查任务执行异常", e);
        }
    }

    /**
     * 【补偿】支付状态核查任务：每 10 分钟执行一次
     * 
     * 作用：
     * 1. 查询长时间处于"待支付"状态的订单
     * 2. 主动调用支付宝接口查询真实交易状态
     * 3. 根据查询结果更新本地状态
     * 
     * 补偿场景：
     * - 用户已支付但支付宝回调失败
     * - 网络延迟导致状态不同步
     * - 系统故障导致数据不一致
     */
    @Scheduled(cron = "0 */10 * * * *")
    public void processPaymentCompensation() {
        log.info("启动支付状态核查任务（支付宝回调兜底）");

        try {
            long startTime = System.currentTimeMillis();

            // 查询待支付超过 10 分钟的订单（避免频繁查询刚创建的订单）
            int waitingMinutes = 10;
            
            // 执行补偿处理
            int compensatedCount = paymentService.processCompensation(waitingMinutes);

            long costTime = System.currentTimeMillis() - startTime;
            
            if (compensatedCount > 0) {
                log.info("✅ 支付状态核查完成：补偿了{}个订单，耗时：{}ms", 
                        compensatedCount, costTime);
            } else {
                log.debug("支付状态核查完成：无需补偿，耗时：{}ms", costTime);
            }

        } catch (Exception e) {
            log.error("❌ 支付状态核查任务执行异常", e);
        }
    }

    /**
     * 【TODO:监控】支付异常情况统计：每 30 分钟执行一次
     * 
     * 作用：
     * 1. 统计各种异常状态的订单数量
     * 2. 发送告警通知（可选）
     * 3. 记录到日志用于分析
     */
    @Scheduled(cron = "0 0 */1 * * *")
    public void monitorPaymentAbnormalities() {
        log.info("启动支付异常情况统计任务");

        try {
            long startTime = System.currentTimeMillis();

            // TODO: 实现监控逻辑
            // 1. 统计超时未支付订单数
            // int timeoutCount = paymentService.countTimeoutOrders(PaymentConstant.PAYMENT_TIMEOUT_MINUTES);
            
            // 2. 统计待支付超过 1 小时的订单数
            // int longWaitingCount = paymentService.countWaitingOrders(60);
            
            // 3. 统计本地状态与支付宝不一致的订单数
            // int inconsistentCount = paymentService.countInconsistentOrders();
            
            // 4. 如果有异常数据，记录告警
            // if (timeoutCount > 0 || longWaitingCount > 0 || inconsistentCount > 0) {
            //     log.warn("⚠️ 支付异常统计 - 超时：{}, 长等待：{}, 不一致：{}", 
            //             timeoutCount, longWaitingCount, inconsistentCount);
            // }

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("支付异常统计完成，耗时：{}ms", costTime);

        } catch (Exception e) {
            log.error("❌ 支付异常统计任务执行异常", e);
        }
    }

    /**
     * 【清理】过期支付记录清理：每天凌晨 3 点执行
     * 
     * 作用：
     * 1. 清理已关闭超过 7 天的支付记录
     * 2. 优化数据库性能
     * 3. 释放存储空间
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredPaymentRecords() {
        log.info("启动过期支付记录清理任务");

        try {
            long startTime = System.currentTimeMillis();

            // 清理 7 天前已关闭的支付记录
            int daysToKeep = 7;
            int cleanedCount = paymentService.cleanExpiredPayments(daysToKeep);

            long costTime = System.currentTimeMillis() - startTime;
            
            log.info("过期支付记录清理完成：清理了{}条记录，耗时：{}ms", 
                    cleanedCount, costTime);

        } catch (Exception e) {
            log.error("❌ 过期支付记录清理任务执行异常", e);
        }
    }
}