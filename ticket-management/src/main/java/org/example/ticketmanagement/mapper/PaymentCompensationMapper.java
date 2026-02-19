// org/example/ticketmanagement/mapper/PaymentCompensationMapper.java
package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.ticketmanagement.pojo.PaymentCompensation;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PaymentCompensationMapper {

    /**
     * 插入补偿任务
     */
    @Insert("INSERT INTO payment_compensation(payment_no, order_id, order_no, user_id, " +
            "task_status, retry_count, max_retry, next_execute_time, create_time, update_time) " +
            "VALUES(#{paymentNo}, #{orderId}, #{orderNo}, #{userId}, " +
            "#{taskStatus}, #{retryCount}, #{maxRetry}, #{nextExecuteTime}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PaymentCompensation compensation);

    /**
     * 根据支付流水号查询
     */
    @Select("SELECT * FROM payment_compensation WHERE payment_no = #{paymentNo}")
    @Results(id = "compensationResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "payment_no", property = "paymentNo"),
            @Result(column = "order_id", property = "orderId"),
            @Result(column = "order_no", property = "orderNo"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "task_status", property = "taskStatus"),
            @Result(column = "retry_count", property = "retryCount"),
            @Result(column = "max_retry", property = "maxRetry"),
            @Result(column = "last_execute_time", property = "lastExecuteTime"),
            @Result(column = "next_execute_time", property = "nextExecuteTime"),
            @Result(column = "error_msg", property = "errorMsg"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    PaymentCompensation selectByPaymentNo(@Param("paymentNo") String paymentNo);

    /**
     * 查询待处理的补偿任务
     */
    @Select("SELECT * FROM payment_compensation WHERE task_status = 0 " +
            "AND next_execute_time <= NOW() ORDER BY next_execute_time ASC LIMIT #{limit}")
    @ResultMap("compensationResultMap")
    List<PaymentCompensation> selectPendingTasks(@Param("limit") Integer limit);

    /**
     * 更新补偿任务状态
     */
    @Update("UPDATE payment_compensation SET task_status = #{taskStatus}, " +
            "retry_count = #{retryCount}, last_execute_time = #{lastExecuteTime}, " +
            "next_execute_time = #{nextExecuteTime}, error_msg = #{errorMsg}, " +
            "update_time = #{updateTime} WHERE id = #{id}")
    int updateStatus(PaymentCompensation compensation);

    /**
     * 增加重试次数
     */
    @Update("UPDATE payment_compensation SET retry_count = retry_count + 1, " +
            "last_execute_time = #{lastExecuteTime}, " +
            "next_execute_time = #{nextExecuteTime}, " +
            "update_time = #{updateTime} WHERE id = #{id}")
    int incrementRetry(@Param("id") Long id,
                       @Param("lastExecuteTime") LocalDateTime lastExecuteTime,
                       @Param("nextExecuteTime") LocalDateTime nextExecuteTime,
                       @Param("updateTime") LocalDateTime updateTime);
}