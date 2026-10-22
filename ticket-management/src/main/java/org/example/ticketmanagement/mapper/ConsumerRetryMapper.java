package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Map;

/** 消费失败复用持久补偿表，次数不依赖Broker重投标记或进程内消息头。 */
@Mapper
public interface ConsumerRetryMapper {

    /** 同一原始事件只创建一份消费恢复记录。 */
    @Insert("""
            INSERT IGNORE INTO payment_compensation(task_type,business_key,payload,task_status,
                retry_count,max_retry,next_execute_time,create_time,update_time)
            VALUES('MESSAGE_RETRY',#{root},#{payload},0,0,10,#{now},#{now},#{now})
            """)
    int init(@Param("root") String root, @Param("payload") String payload, @Param("now") LocalDateTime now);

    /** 计数和下一次Outbox写入共用事务，重复并发失败不能覆盖次数。 */
    @Select("""
            SELECT retry_count,task_status FROM payment_compensation
            WHERE task_type='MESSAGE_RETRY' AND business_key=#{root} FOR UPDATE
            """)
    Map<String, Object> lock(@Param("root") String root);

    /** 更新退避时间；耗尽后转人工，保留原始载荷。 */
    @Update("""
            UPDATE payment_compensation SET retry_count=#{attempt},task_status=#{state},
                next_execute_time=#{next},update_time=#{now}
            WHERE task_type='MESSAGE_RETRY' AND business_key=#{root}
            """)
    int failed(@Param("root") String root, @Param("attempt") int attempt, @Param("state") int state,
            @Param("next") LocalDateTime next, @Param("now") LocalDateTime now);

    /** 重试事件成功后结束恢复记录，不触碰邮件和关单任务。 */
    @Update("""
            UPDATE payment_compensation SET task_status=2,update_time=#{now}
            WHERE task_type='MESSAGE_RETRY' AND business_key=#{root} AND task_status IN(0,1)
            """)
    int completed(@Param("root") String root, @Param("now") LocalDateTime now);
}
