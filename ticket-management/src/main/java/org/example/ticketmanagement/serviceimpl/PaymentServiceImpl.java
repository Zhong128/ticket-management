// org/example/ticketmanagement/service/impl/PaymentServiceImpl.java
package org.example.ticketmanagement.serviceimpl;

import cn.dev33.satoken.stp.StpUtil;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConstants;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeCloseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.config.AlipayProperties;
import org.example.ticketmanagement.constant.OrderConstant;
import org.example.ticketmanagement.constant.PaymentConstant;
import org.example.ticketmanagement.dto.CreatePaymentDTO;
import org.example.ticketmanagement.dto.PaymentResultDTO;
import org.example.ticketmanagement.dto.PaymentSuccessMessageDTO;
import org.example.ticketmanagement.dto.PaymentTimeoutMessageDTO;
import org.example.ticketmanagement.mapper.PaymentCompensationMapper;
import org.example.ticketmanagement.mapper.PaymentNotifyLogMapper;
import org.example.ticketmanagement.mapper.PaymentRecordMapper;
import org.example.ticketmanagement.mapper.OrderInfoMapper;
import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.mapper.EventMapper;
import org.example.ticketmanagement.mapper.EventSessionMapper;
import org.example.ticketmanagement.mapper.TicketTierMapper;
import org.example.ticketmanagement.pojo.*;
import org.example.ticketmanagement.service.PaymentService;
import org.example.ticketmanagement.service.OrderService;
import org.example.ticketmanagement.util.AlipaySignatureUtil;
import org.example.ticketmanagement.util.PaymentNoGenerator;
import org.example.ticketmanagement.util.RabbitMQUtil;
import org.example.ticketmanagement.vo.PaymentRecordVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRecordMapper paymentRecordMapper;
    private final PaymentNotifyLogMapper paymentNotifyLogMapper;
    private final PaymentCompensationMapper paymentCompensationMapper;
    private final OrderInfoMapper orderInfoMapper;
    private final UserMapper userMapper;
    private final EventMapper eventMapper;
    private final EventSessionMapper eventSessionMapper;
    private final TicketTierMapper ticketTierMapper;
    private final OrderService orderService;
    private final AlipayClient alipayClient;
    private final AlipayProperties alipayProperties;
    private final PaymentNoGenerator paymentNoGenerator;
    private final RabbitMQUtil rabbitMQUtil;

    private final AlipaySignatureUtil alipaySignatureUtil;


    @Override
    @Transactional
    public PaymentResultDTO createPayment(Long userId, CreatePaymentDTO createPaymentDTO) {
        log.info("开始创建支付订单: userId={}, orderId={}, payType={}",
                userId, createPaymentDTO.getOrderId(), createPaymentDTO.getPayType());

        // 1. 查询订单信息
        OrderInfo orderInfo = orderInfoMapper.selectById(createPaymentDTO.getOrderId());
        if (orderInfo == null) {
            throw new RuntimeException("订单不存在");
        }

        // 2. 验证订单所属用户
        if (!orderInfo.getUserId().equals(userId)) {
            log.warn("用户越权操作支付: userId={}, orderUserId={}", userId, orderInfo.getUserId());
            throw new RuntimeException("无权操作此订单");
        }

        // 3. 验证订单状态
        if (!orderInfo.getOrderStatus().equals(OrderConstant.ORDER_STATUS_PENDING)) {
            throw new RuntimeException("订单状态不是待支付，无法发起支付");
        }

        // 4. 检查是否已有支付记录
        PaymentRecord existingRecord = paymentRecordMapper.selectByOrderId(orderInfo.getId());
        if (existingRecord != null) {
            // 如果已有支付记录且未过期，返回已有二维码
            if (!existingRecord.isExpired() &&
                    existingRecord.getPayStatus().equals(PaymentConstant.PAY_STATUS_WAITING)) {
                log.info("已有未过期的支付记录，返回已有二维码: paymentNo={}", existingRecord.getPaymentNo());
                return convertToResultDTO(existingRecord);
            }
            // 如果已过期，关闭原有支付记录
            if (existingRecord.isExpired()) {
                existingRecord.setPayStatus(PaymentConstant.PAY_STATUS_CLOSED);
                existingRecord.setUpdateTime(LocalDateTime.now());
                paymentRecordMapper.updateStatus(existingRecord);
            }
        }

        try {
            // 5. 生成支付流水号
            String paymentNo = paymentNoGenerator.generatePaymentNo();

            // 6. 获取订单关联的演出信息
            Event event = eventMapper.selectById(orderInfo.getEventId());
            EventSession session = eventSessionMapper.selectById(orderInfo.getSessionId());
            TicketTier tier = ticketTierMapper.selectById(orderInfo.getTierId());

            // 7. 构建支付标题和描述
            String subject = String.format("【抢票】%s-%s-%s",
                    event != null ? event.getName() : "演出",
                    session != null ? session.getSessionName() : "场次",
                    tier != null ? tier.getTierName() : "票档");
            String body = String.format("%s 共%d张 总金额%.2f元",
                    subject, orderInfo.getQuantity(), orderInfo.getTotalAmount());

            // 8. 计算过期时间（30分钟）
            LocalDateTime expireTime = LocalDateTime.now().plusMinutes(alipayProperties.getTimeoutMinutes());

            // 9. 创建支付记录
            PaymentRecord paymentRecord = new PaymentRecord();
            paymentRecord.setOrderId(orderInfo.getId());
            paymentRecord.setOrderNo(orderInfo.getOrderNo());
            paymentRecord.setUserId(userId);
            paymentRecord.setPaymentNo(paymentNo);
            paymentRecord.setTotalAmount(orderInfo.getTotalAmount());
            paymentRecord.setPayStatus(PaymentConstant.PAY_STATUS_WAITING);
            paymentRecord.setPayType(createPaymentDTO.getPayType());
            paymentRecord.setSubject(subject);
            paymentRecord.setBody(body);
            paymentRecord.setExpireTime(expireTime);
            paymentRecord.setCreateTime(LocalDateTime.now());
            paymentRecord.setUpdateTime(LocalDateTime.now());

            int result = paymentRecordMapper.insert(paymentRecord);
            if (result <= 0) {
                throw new RuntimeException("创建支付记录失败");
            }

            // 10. 调用支付宝生成支付二维码
            String qrCode = callAlipayPreCreate(paymentRecord);

            // 11. 发送超时检查消息到延迟队列（30分钟后检查）
            sendPaymentTimeoutMessage(paymentRecord);

            log.info("创建支付订单成功: paymentNo={}, orderNo={}", paymentNo, orderInfo.getOrderNo());

            // 12. 返回结果
            PaymentResultDTO resultDTO = convertToResultDTO(paymentRecord);
            resultDTO.setQrCode(qrCode);
            return resultDTO;

        } catch (Exception e) {
            log.error("创建支付订单失败: orderId={}", createPaymentDTO.getOrderId(), e);
            throw new RuntimeException("创建支付订单失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public PaymentResultDTO createPaymentAuto(Long userId, Integer payType) {
        log.info("开始自动创建支付订单: userId={}, payType={}", userId, payType);

        // 1. 获取用户最新的待支付订单
        OrderInfo orderInfo = orderService.getLatestPendingOrderByUser(userId);
        if (orderInfo == null) {
            throw new RuntimeException("没有待支付的订单");
        }

        // 2. 构造CreatePaymentDTO
        CreatePaymentDTO createPaymentDTO = new CreatePaymentDTO();
        createPaymentDTO.setOrderId(orderInfo.getId());
        createPaymentDTO.setPayType(payType);

        // 3. 调用原有的创建支付方法
        return createPayment(userId, createPaymentDTO);
    }

    @Override
    @Transactional
    public String handleAlipayNotify(Map<String, String> params) {
        log.info("收到支付宝异步回调: params={}", params);

        String paymentNo = null;
        String tradeNo = null;
        String tradeStatus = null;

        try {
            // 1. 验证支付宝签名（安全验证，防止伪造回调）
            boolean signVerified = alipaySignatureUtil.verifyAlipaySignature(params);
            if (!signVerified) {
                log.error("支付宝回调签名验证失败，可能存在安全风险");
                return "failure";
            }
            log.debug("支付宝签名验证通过");

            // 2. 获取关键参数
            paymentNo = params.get("out_trade_no");      // 商户支付流水号
            tradeNo = params.get("trade_no");            // 支付宝交易号
            tradeStatus = params.get("trade_status");    // 交易状态
            String totalAmount = params.get("total_amount");

            log.info("回调参数解析: paymentNo={}, tradeNo={}, tradeStatus={}",
                    paymentNo, tradeNo, tradeStatus);

            // 3. 参数基本验证
            if (paymentNo == null || paymentNo.isEmpty()) {
                log.error("回调参数缺失: out_trade_no为空");
                return "failure";
            }

            // 4. 记录回调日志
            saveNotifyLog(params);

            // 5. 查询支付记录
            PaymentRecord paymentRecord = paymentRecordMapper.selectByPaymentNo(paymentNo);
            if (paymentRecord == null) {
                log.error("支付记录不存在: paymentNo={}", paymentNo);
                return "failure";
            }

            // 6. 幂等性处理：如果已经支付成功，直接返回success
            if (paymentRecord.getPayStatus().equals(PaymentConstant.PAY_STATUS_SUCCESS)) {
                log.info("支付记录已处理，跳过重复回调: paymentNo={}", paymentNo);
                return "success";
            }

            // 7. 根据交易状态处理
            if (PaymentConstant.ALIPAY_TRADE_SUCCESS.equals(tradeStatus) ||
                    PaymentConstant.ALIPAY_TRADE_FINISHED.equals(tradeStatus)) {

                // 7.1 支付成功
                paymentRecord.setTradeNo(tradeNo);
                paymentRecord.setPayStatus(PaymentConstant.PAY_STATUS_SUCCESS);
                paymentRecord.setNotifyData(params.toString());
                paymentRecord.setNotifyTime(LocalDateTime.now());
                paymentRecord.setPayTime(LocalDateTime.now());
                paymentRecord.setUpdateTime(LocalDateTime.now());

                paymentRecordMapper.updateStatus(paymentRecord);

                // 7.2 更新订单状态为已支付
                orderService.updateOrderStatus(paymentRecord.getOrderId(), OrderConstant.ORDER_STATUS_PAID);

                // 7.3 发送支付成功消息（用于邮件通知）
                sendPaymentSuccessMessage(paymentRecord);

                log.info("支付成功处理完成: paymentNo={}, tradeNo={}", paymentNo, tradeNo);

            } else if (PaymentConstant.ALIPAY_TRADE_CLOSED.equals(tradeStatus)) {
                // 7.4 交易关闭
                paymentRecord.setTradeNo(tradeNo);
                paymentRecord.setPayStatus(PaymentConstant.PAY_STATUS_CLOSED);
                paymentRecord.setNotifyData(params.toString());
                paymentRecord.setNotifyTime(LocalDateTime.now());
                paymentRecord.setUpdateTime(LocalDateTime.now());

                paymentRecordMapper.updateStatus(paymentRecord);

                log.info("交易关闭处理完成: paymentNo={}", paymentNo);
            }

            return "success";

        } catch (Exception e) {
            log.error("处理支付宝回调异常: paymentNo={}, tradeNo={}", paymentNo, tradeNo, e);

            // 记录异常日志
            PaymentNotifyLog log = new PaymentNotifyLog();
            log.setPaymentNo(paymentNo);
            log.setTradeNo(tradeNo);
            log.setNotifyData(params.toString());
            log.setNotifyStatus(tradeStatus);
            log.setProcessStatus(2); // 处理失败
            log.setErrorMsg(e.getMessage());
            log.setCreateTime(LocalDateTime.now());
            paymentNotifyLogMapper.insert(log);

            return "failure";
        }
    }


    @Override
    public PaymentRecordVO getPaymentStatus(String paymentNo) {
        log.debug("查询支付状态: paymentNo={}", paymentNo);

        PaymentRecord paymentRecord = paymentRecordMapper.selectByPaymentNo(paymentNo);
        if (paymentRecord == null) {
            throw new RuntimeException("支付记录不存在");
        }

        // 验证权限：只能查看自己的支付记录
        Long currentUserId = StpUtil.getLoginIdAsLong();
        if (!paymentRecord.getUserId().equals(currentUserId)) {
            log.warn("用户越权查询支付记录: userId={}, recordUserId={}",
                    currentUserId, paymentRecord.getUserId());
            throw new RuntimeException("无权查看此支付记录");
        }

        return convertToVO(paymentRecord);
    }

    @Override
    @Transactional
    public boolean closePayment(String paymentNo) {
        log.info("关闭支付订单: paymentNo={}", paymentNo);

        PaymentRecord paymentRecord = paymentRecordMapper.selectByPaymentNo(paymentNo);
        if (paymentRecord == null) {
            log.warn("支付记录不存在: paymentNo={}", paymentNo);
            return false;
        }

        // 只能关闭待支付状态的订单
        if (!paymentRecord.getPayStatus().equals(PaymentConstant.PAY_STATUS_WAITING)) {
            log.warn("支付记录状态不是待支付，无法关闭: paymentNo={}, status={}",
                    paymentNo, paymentRecord.getPayStatus());
            return false;
        }

        try {
            // 调用支付宝关闭订单接口
            AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
            AlipayTradeCloseModel model = new AlipayTradeCloseModel();
            model.setOutTradeNo(paymentNo);
            model.setTradeNo(paymentRecord.getTradeNo());
            request.setBizModel(model);

            AlipayTradeCloseResponse response = alipayClient.execute(request);

            if (!response.isSuccess()) {
                log.error("支付宝关闭订单失败: paymentNo={}, code={}, msg={}",
                        paymentNo, response.getCode(), response.getMsg());
            }

            // 更新本地支付状态
            paymentRecord.setPayStatus(PaymentConstant.PAY_STATUS_CLOSED);
            paymentRecord.setUpdateTime(LocalDateTime.now());
            paymentRecordMapper.updateStatus(paymentRecord);

            log.info("关闭支付订单成功: paymentNo={}", paymentNo);
            return true;

        } catch (Exception e) {
            log.error("关闭支付订单异常: paymentNo={}", paymentNo, e);
            throw new RuntimeException("关闭支付订单失败", e);
        }
    }

    @Override
    @Transactional
    public void processCompensation() {
        log.info("开始处理支付补偿任务");

        // 1. 查询需要补偿的支付记录（待支付但已过期，且没有补偿任务）
        List<PaymentRecord> needCompensation = paymentRecordMapper.selectNeedCompensation(
                PaymentConstant.COMPENSATION_BATCH_SIZE);

        if (needCompensation.isEmpty()) {
            log.info("没有需要补偿的支付记录");
            return;
        }

        log.info("找到{}条需要补偿的支付记录", needCompensation.size());

        for (PaymentRecord record : needCompensation) {
            try {
                // 2. 创建补偿任务
                PaymentCompensation compensation = new PaymentCompensation();
                compensation.setPaymentNo(record.getPaymentNo());
                compensation.setOrderId(record.getOrderId());
                compensation.setOrderNo(record.getOrderNo());
                compensation.setUserId(record.getUserId());
                compensation.setTaskStatus(PaymentConstant.COMPENSATION_STATUS_PENDING);
                compensation.setRetryCount(0);
                compensation.setMaxRetry(PaymentConstant.MAX_COMPENSATION_RETRY);
                compensation.setNextExecuteTime(LocalDateTime.now().plusMinutes(1));
                compensation.setCreateTime(LocalDateTime.now());
                compensation.setUpdateTime(LocalDateTime.now());

                paymentCompensationMapper.insert(compensation);

                // 3. 立即执行一次查询
                boolean success = checkAndUpdatePaymentStatus(record);

                if (success) {
                    log.info("补偿任务立即执行成功: paymentNo={}", record.getPaymentNo());

                    // 更新补偿任务状态
                    compensation.setTaskStatus(PaymentConstant.COMPENSATION_STATUS_SUCCESS);
                    compensation.setLastExecuteTime(LocalDateTime.now());
                    compensation.setUpdateTime(LocalDateTime.now());
                    paymentCompensationMapper.updateStatus(compensation);
                }

            } catch (Exception e) {
                log.error("创建补偿任务失败: paymentNo={}", record.getPaymentNo(), e);
            }
        }
    }

    @Override
    @Transactional
    public void processTimeoutClose() {
        log.info("开始处理超时关单");

        // 1. 查询待处理的补偿任务
        List<PaymentCompensation> pendingTasks = paymentCompensationMapper.selectPendingTasks(100);

        if (pendingTasks.isEmpty()) {
            log.info("没有待处理的补偿任务");
            return;
        }

        log.info("找到{}个待处理的补偿任务", pendingTasks.size());

        for (PaymentCompensation task : pendingTasks) {
            try {
                // 2. 更新任务状态为处理中
                task.setTaskStatus(PaymentConstant.COMPENSATION_STATUS_PROCESSING);
                task.setLastExecuteTime(LocalDateTime.now());
                task.setUpdateTime(LocalDateTime.now());
                paymentCompensationMapper.updateStatus(task);

                // 3. 查询支付记录
                PaymentRecord record = paymentRecordMapper.selectByPaymentNo(task.getPaymentNo());
                if (record == null) {
                    log.warn("支付记录不存在，关闭补偿任务: paymentNo={}", task.getPaymentNo());
                    task.setTaskStatus(PaymentConstant.COMPENSATION_STATUS_FAILED);
                    task.setErrorMsg("支付记录不存在");
                    task.setUpdateTime(LocalDateTime.now());
                    paymentCompensationMapper.updateStatus(task);
                    continue;
                }

                // 4. 查询支付宝状态
                boolean success = checkAndUpdatePaymentStatus(record);

                if (success) {
                    // 5. 处理成功，更新任务状态
                    task.setTaskStatus(PaymentConstant.COMPENSATION_STATUS_SUCCESS);
                    task.setUpdateTime(LocalDateTime.now());
                    paymentCompensationMapper.updateStatus(task);

                } else if (task.canRetry()) {
                    // 6. 可以重试，计算下次执行时间
                    task.setRetryCount(task.getRetryCount() + 1);
                    task.setLastExecuteTime(LocalDateTime.now());
                    task.calculateNextExecuteTime();
                    task.setTaskStatus(PaymentConstant.COMPENSATION_STATUS_PENDING);
                    task.setUpdateTime(LocalDateTime.now());
                    paymentCompensationMapper.updateStatus(task);

                    log.info("补偿任务将重试: paymentNo={}, retryCount={}, nextExecuteTime={}",
                            task.getPaymentNo(), task.getRetryCount(), task.getNextExecuteTime());
                } else {
                    // 7. 超过最大重试次数，标记为失败
                    task.setTaskStatus(PaymentConstant.COMPENSATION_STATUS_FAILED);
                    task.setErrorMsg("超过最大重试次数");
                    task.setUpdateTime(LocalDateTime.now());
                    paymentCompensationMapper.updateStatus(task);

                    log.error("补偿任务超过最大重试次数: paymentNo={}", task.getPaymentNo());
                }

            } catch (Exception e) {
                log.error("处理补偿任务异常: paymentNo={}", task.getPaymentNo(), e);

                task.setErrorMsg(e.getMessage());
                task.setUpdateTime(LocalDateTime.now());

                if (task.canRetry()) {
                    task.setRetryCount(task.getRetryCount() + 1);
                    task.setLastExecuteTime(LocalDateTime.now());
                    task.calculateNextExecuteTime();
                    task.setTaskStatus(PaymentConstant.COMPENSATION_STATUS_PENDING);
                } else {
                    task.setTaskStatus(PaymentConstant.COMPENSATION_STATUS_FAILED);
                }

                paymentCompensationMapper.updateStatus(task);
            }
        }
    }



    // ==================== 私有辅助方法 ====================

    /**
     * 调用支付宝预创建接口（生成支付二维码）
     */
    private String callAlipayPreCreate(PaymentRecord paymentRecord) {
        try {
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();

            // 设置回调地址
            request.setNotifyUrl(alipayProperties.getNotifyUrl());
            request.setReturnUrl(alipayProperties.getReturnUrl());

            // 设置业务参数
            AlipayTradePagePayModel model = new AlipayTradePagePayModel();
            model.setOutTradeNo(paymentRecord.getPaymentNo());
            model.setTotalAmount(paymentRecord.getTotalAmount().toString());
            model.setSubject(paymentRecord.getSubject());
            model.setBody(paymentRecord.getBody());
            model.setProductCode("FAST_INSTANT_TRADE_PAY");
            model.setTimeoutExpress(alipayProperties.getFormattedTimeout());

            request.setBizModel(model);

            // 调用支付宝接口
            AlipayTradePagePayResponse response = alipayClient.pageExecute(request);

            if (response.isSuccess()) {
                log.info("支付宝预创建成功: paymentNo={}, tradeNo={}",
                        paymentRecord.getPaymentNo(), response.getTradeNo());
                return response.getBody(); // 返回HTML表单或二维码
            } else {
                log.error("支付宝预创建失败: paymentNo={}, code={}, msg={}",
                        paymentRecord.getPaymentNo(), response.getCode(), response.getMsg());
                throw new RuntimeException("支付宝预创建失败: " + response.getMsg());
            }

        } catch (Exception e) {
            log.error("调用支付宝预创建异常: paymentNo={}", paymentRecord.getPaymentNo(), e);
            throw new RuntimeException("调用支付宝接口失败", e);
        }
    }

    /**
     * 发送支付超时检查消息
     */
    private void sendPaymentTimeoutMessage(PaymentRecord paymentRecord) {
        PaymentTimeoutMessageDTO message = new PaymentTimeoutMessageDTO();
        message.setPaymentNo(paymentRecord.getPaymentNo());
        message.setOrderNo(paymentRecord.getOrderNo());
        message.setOrderId(paymentRecord.getOrderId());
        message.setUserId(paymentRecord.getUserId());
        message.setTimestamp(System.currentTimeMillis());

        // 直接发送到超时队列，队列已设置TTL，消息无需单独设置TTL
        rabbitMQUtil.convertAndSend(
                PaymentConstant.PAYMENT_TIMEOUT_EXCHANGE,
                PaymentConstant.PAYMENT_TIMEOUT_ROUTING_KEY,
                message
        );

        log.debug("发送支付超时检查消息: paymentNo={}, delay={}ms",
                paymentRecord.getPaymentNo(), PaymentConstant.PAYMENT_TIMEOUT_DELAY);
    }

    /**
     * 发送支付成功消息（用于邮件通知）
     */
    private void sendPaymentSuccessMessage(PaymentRecord paymentRecord) {
        try {
            // 查询订单关联信息
            OrderInfo orderInfo = orderInfoMapper.selectById(paymentRecord.getOrderId());
            User user = userMapper.getUserById(paymentRecord.getUserId());
            Event event = eventMapper.selectById(orderInfo.getEventId());
            EventSession session = eventSessionMapper.selectById(orderInfo.getSessionId());
            TicketTier tier = ticketTierMapper.selectById(orderInfo.getTierId());

            PaymentSuccessMessageDTO message = new PaymentSuccessMessageDTO();
            message.setPaymentNo(paymentRecord.getPaymentNo());
            message.setTradeNo(paymentRecord.getTradeNo());
            message.setOrderNo(paymentRecord.getOrderNo());
            message.setOrderId(paymentRecord.getOrderId());
            message.setUserId(paymentRecord.getUserId());
            message.setUserEmail(user != null ? user.getEmail() : null);
            message.setTotalAmount(paymentRecord.getTotalAmount());
            message.setPayTime(paymentRecord.getPayTime());
            message.setEventName(event != null ? event.getName() : null);
            message.setSessionName(session != null ? session.getSessionName() : null);
            message.setTierName(tier != null ? tier.getTierName() : null);
            message.setQuantity(orderInfo != null ? orderInfo.getQuantity() : 1);

            // 发送到RabbitMQ
            rabbitMQUtil.convertAndSend(
                    PaymentConstant.PAYMENT_SUCCESS_EXCHANGE,
                    PaymentConstant.PAYMENT_SUCCESS_ROUTING_KEY,
                    message
            );

            log.info("发送支付成功消息: paymentNo={}", paymentRecord.getPaymentNo());

        } catch (Exception e) {
            log.error("发送支付成功消息失败: paymentNo={}", paymentRecord.getPaymentNo(), e);
        }
    }

    /**
     * 保存回调日志
     */
    private void saveNotifyLog(Map<String, String> params) {
        PaymentNotifyLog log = new PaymentNotifyLog();
        log.setPaymentNo(params.get("out_trade_no"));
        log.setTradeNo(params.get("trade_no"));
        log.setNotifyData(params.toString());
        log.setNotifyStatus(params.get("trade_status"));
        log.setProcessStatus(0); // 待处理
        log.setIpAddress(getCurrentIp()); // 需要获取真实IP
        log.setCreateTime(LocalDateTime.now());

        paymentNotifyLogMapper.insert(log);
    }

    /**
     * 获取当前请求IP（简化，实际需从Request中获取）
     */
    private String getCurrentIp() {
        return "0:0:0:0:0:0:0:1";
    }

    /**
     * 检查并更新支付状态
     */
    private boolean checkAndUpdatePaymentStatus(PaymentRecord record) {
        try {
            // 1. 调用支付宝查询接口
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            AlipayTradeQueryModel model = new AlipayTradeQueryModel();
            model.setOutTradeNo(record.getPaymentNo());
            model.setTradeNo(record.getTradeNo());
            request.setBizModel(model);

            AlipayTradeQueryResponse response = alipayClient.execute(request);

            if (!response.isSuccess()) {
                log.warn("查询支付宝订单失败: paymentNo={}, code={}, msg={}",
                        record.getPaymentNo(), response.getCode(), response.getMsg());
                return false;
            }

            String tradeStatus = response.getTradeStatus();
            log.debug("查询支付宝订单状态: paymentNo={}, tradeStatus={}",
                    record.getPaymentNo(), tradeStatus);

            // 2. 根据支付宝状态更新本地
            if (PaymentConstant.ALIPAY_TRADE_SUCCESS.equals(tradeStatus) ||
                    PaymentConstant.ALIPAY_TRADE_FINISHED.equals(tradeStatus)) {

                // 支付成功
                record.setTradeNo(response.getTradeNo());
                record.setPayStatus(PaymentConstant.PAY_STATUS_SUCCESS);
                record.setPayTime(LocalDateTime.now());
                record.setUpdateTime(LocalDateTime.now());
                paymentRecordMapper.updateStatus(record);

                // 更新订单状态
                orderService.updateOrderStatus(record.getOrderId(), OrderConstant.ORDER_STATUS_PAID);

                // 发送支付成功消息
                sendPaymentSuccessMessage(record);

                log.info("补偿任务发现支付成功: paymentNo={}", record.getPaymentNo());
                return true;

            } else if (PaymentConstant.ALIPAY_TRADE_CLOSED.equals(tradeStatus)) {
                // 交易关闭
                record.setPayStatus(PaymentConstant.PAY_STATUS_CLOSED);
                record.setUpdateTime(LocalDateTime.now());
                paymentRecordMapper.updateStatus(record);

                log.info("补偿任务发现交易关闭: paymentNo={}", record.getPaymentNo());
                return true;

            } else if (record.isExpired()) {
                // 已过期但支付宝可能还是待支付状态，尝试关闭
                closePayment(record.getPaymentNo());
                return true;
            }

            return false;

        } catch (Exception e) {
            log.error("检查支付宝订单状态异常: paymentNo={}", record.getPaymentNo(), e);
            return false;
        }
    }

    /**
     * 转换为ResultDTO
     */
    private PaymentResultDTO convertToResultDTO(PaymentRecord record) {
        PaymentResultDTO dto = new PaymentResultDTO();
        dto.setPaymentNo(record.getPaymentNo());
        dto.setOrderNo(record.getOrderNo());
        dto.setTotalAmount(record.getTotalAmount());
        dto.setPayStatus(record.getPayStatus());
        dto.setPayStatusDesc(record.getPayStatusDesc());
        dto.setExpireTime(record.getExpireTime() != null ?
                record.getExpireTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
        return dto;
    }

    /**
     * 转换为VO
     */
    private PaymentRecordVO convertToVO(PaymentRecord record) {
        if (record == null) return null;

        PaymentRecordVO vo = new PaymentRecordVO();
        vo.setId(record.getId());
        vo.setPaymentNo(record.getPaymentNo());
        vo.setTradeNo(record.getTradeNo());
        vo.setOrderNo(record.getOrderNo());
        vo.setOrderId(record.getOrderId());
        vo.setTotalAmount(record.getTotalAmount());
        vo.setPayStatus(record.getPayStatus());
        vo.setPayType(record.getPayType());
        vo.setSubject(record.getSubject());
        vo.setPayTime(record.getPayTime());
        vo.setExpireTime(record.getExpireTime());
        vo.setCreateTime(record.getCreateTime());

        // 查询订单信息补充演出名称等
        OrderInfo orderInfo = orderInfoMapper.selectById(record.getOrderId());
        if (orderInfo != null) {
            Event event = eventMapper.selectById(orderInfo.getEventId());
            if (event != null) {
                vo.setSubject(event.getName()); // 简化，实际可能需要更详细的信息
            }
        }

        return vo;
    }
}