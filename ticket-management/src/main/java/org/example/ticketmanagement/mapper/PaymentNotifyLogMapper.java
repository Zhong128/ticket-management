// org/example/ticketmanagement/mapper/PaymentNotifyLogMapper.java
package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.ticketmanagement.pojo.PaymentNotifyLog;

@Mapper
public interface PaymentNotifyLogMapper {

    /**
     * 记录每次已验签且处理成功的支付回调送达。
     */
    @Insert("INSERT INTO payment_notify_log "
            + "(payment_no, trade_no, notify_data, notify_status, "
            + "process_status, error_msg, ip_address, create_time) "
            + "VALUES (#{paymentNo}, #{tradeNo}, #{notifyData}, #{notifyStatus}, "
            + "#{processStatus}, #{errorMsg}, #{ipAddress}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PaymentNotifyLog log);
}
