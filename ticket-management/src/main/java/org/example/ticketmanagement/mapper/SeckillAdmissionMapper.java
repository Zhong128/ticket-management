package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.ticketmanagement.dto.RedisReservation;
import org.example.ticketmanagement.pojo.SeckillAdmission;
import org.example.ticketmanagement.pojo.StockEpoch;
import org.example.ticketmanagement.pojo.StockReservation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Redis受理日志与缓存代次屏障；所有锁由调用方Spring事务持有。
 */
@Mapper
public interface SeckillAdmissionMapper {

    /** 无锁取得订单所属票档，用于先取得代次屏障再锁订单。 */
    @Select("SELECT * FROM order_info WHERE id=#{id}")
    Map<String, Object> order(@Param("id") long id);

    /** 创建票档缓存状态，已有记录不能重置代次。 */
    @Insert("""
            INSERT IGNORE INTO seckill_stock_epoch(tier_id,epoch,cache_status,updated_at)
            VALUES(#{tierId},0,'PAUSED',#{now})
            """)
    int initEpoch(@Param("tierId") long tierId, @Param("now") LocalDateTime now);

    /** 查询代次状态，不作为事务内的并发屏障。 */
    @Select("SELECT * FROM seckill_stock_epoch WHERE tier_id=#{tierId}")
    StockEpoch findEpoch(@Param("tierId") long tierId);

    /** 共享屏障允许不同用户并发受理，重建时由排他锁阻断。 */
    @Select("SELECT * FROM seckill_stock_epoch WHERE tier_id=#{tierId} FOR SHARE")
    StockEpoch shareEpoch(@Param("tierId") long tierId);

    /** 排他锁串行化暂停与缓存代次切换。 */
    @Select("SELECT * FROM seckill_stock_epoch WHERE tier_id=#{tierId} FOR UPDATE")
    StockEpoch lockEpoch(@Param("tierId") long tierId);

    /** 暂停票档并记录本次重建持有者。 */
    @Update("""
            UPDATE seckill_stock_epoch
            SET epoch=epoch+1,cache_status='PAUSED',build_token=#{token},updated_at=#{now}
            WHERE tier_id=#{tierId}
            """)
    int pauseEpoch(@Param("tierId") long tierId, @Param("token") String token, @Param("now") LocalDateTime now);

    /** 只有仍持有当前重建令牌的任务才能开放数据库代次。 */
    @Update("""
            UPDATE seckill_stock_epoch SET cache_status='READY',build_token=NULL,updated_at=#{now}
            WHERE tier_id=#{tierId} AND epoch=#{epoch} AND build_token=#{token} AND cache_status='PAUSED'
            """)
    int readyEpoch(@Param("tierId") long tierId, @Param("epoch") long epoch,
            @Param("token") String token, @Param("now") LocalDateTime now);

    /** 无票档库存锁读取售卖信息；可靠受理和消费均重新核验。 */
    @Select("""
            SELECT t.*,e.status AS event_status,e.sale_start_time,e.sale_end_time,s.status AS session_status
            FROM ticket_tier t JOIN event e ON e.id=t.event_id
            JOIN event_session s ON s.id=t.session_id WHERE t.id=#{tierId}
            """)
    Map<String, Object> sale(@Param("tierId") long tierId);

    /** 按同用户同请求键占位，受理与孤立清理必须争用同一记录。 */
    @Insert("""
            INSERT IGNORE INTO seckill_admission(user_id,request_key,tier_id,quantity,redis_token,stock_epoch,
                admission_status,created_at,updated_at)
            VALUES(#{p.userId},#{p.requestKey},#{p.tierId},#{p.quantity},#{p.token},#{p.epoch},'NEW',#{now},#{now})
            """)
    int initAdmission(@Param("p") RedisReservation reservation, @Param("now") LocalDateTime now);

    /** 锁定受理决策，禁止清理已经拒绝的请求又被迟到事务接受。 */
    @Select("SELECT * FROM seckill_admission WHERE user_id=#{userId} AND request_key=#{key} FOR UPDATE")
    SeckillAdmission lockAdmission(@Param("userId") long userId, @Param("key") String key);

    /** 提交结果未知时按稳定请求键查询，不盲目回补。 */
    @Select("SELECT * FROM seckill_admission WHERE user_id=#{userId} AND request_key=#{key}")
    SeckillAdmission findAdmission(@Param("userId") long userId, @Param("key") String key);

    /** 新请求与持久化申请绑定，状态只能由NEW决定为ACCEPTED。 */
    @Update("""
            UPDATE seckill_admission SET admission_status='ACCEPTED',reservation_id=#{id},updated_at=#{now}
            WHERE user_id=#{userId} AND request_key=#{key} AND admission_status='NEW'
            """)
    int accepted(@Param("userId") long userId, @Param("key") String key, @Param("id") long reservationId,
            @Param("now") LocalDateTime now);

    /** 原子封存未接受请求；终态拒绝后才允许释放孤立预扣。 */
    @Update("""
            UPDATE seckill_admission SET admission_status='REJECTED',updated_at=#{now}
            WHERE user_id=#{userId} AND request_key=#{key} AND admission_status='NEW'
            """)
    int rejected(@Param("userId") long userId, @Param("key") String key, @Param("now") LocalDateTime now);

    /** 入口只写PENDING申请，不扣ticket_tier库存。 */
    @Insert("""
            INSERT INTO stock_reservation(user_id,tier_id,event_id,session_id,request_key,order_no,status,
                quantity,unit_price,total_amount,expires_at,create_time,update_time,attempts,
                inventory_state,redis_token,stock_epoch)
            VALUES(#{userId},#{tierId},#{eventId},#{sessionId},#{requestKey},#{orderNo},'RESERVED',
                #{quantity},#{unitPrice},#{totalAmount},#{expiresAt},#{createTime},#{updateTime},0,
                'PENDING',#{redisToken},#{stockEpoch})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertReservation(StockReservation reservation);

    /** 读取具名申请实体，用于接口和Redis状态同步。 */
    @Select("SELECT * FROM stock_reservation WHERE id=#{id}")
    StockReservation reservation(@Param("id") long id);

    /** 消费及释放必须锁定同一申请。 */
    @Select("SELECT * FROM stock_reservation WHERE id=#{id} FOR UPDATE")
    StockReservation lockReservation(@Param("id") long id);

    /** 版本相同且库存足够时扣减；冲突由Service有界重试。 */
    @Update("""
            UPDATE ticket_tier SET available_stock=available_stock-#{quantity},stock_version=stock_version+1
            WHERE id=#{tierId} AND stock_version=#{version} AND available_stock>=#{quantity}
            """)
    int deduct(@Param("tierId") long tierId, @Param("quantity") int quantity, @Param("version") long version);

    /** 订单建立后才把数据库库存标为HELD。 */
    @Update("""
            UPDATE stock_reservation SET inventory_state='HELD' WHERE id=#{id} AND inventory_state='PENDING'
            """)
    int held(@Param("id") long id);

    /** 完成释放时记录库存阶段，重复事件不改变阶段。 */
    @Update("UPDATE stock_reservation SET inventory_state='RELEASED' WHERE id=#{id}")
    int released(@Param("id") long id);

    /** 按主键分页恢复有效申请，避免一次读取全部历史记录。 */
    @Select("""
            SELECT * FROM stock_reservation WHERE tier_id=#{tierId} AND id>#{after}
                AND status IN('RESERVED','ORDER_CREATED','PAID')
            ORDER BY id LIMIT 100
            """)
    List<StockReservation> activeBatch(@Param("tierId") long tierId, @Param("after") long after);

    /** 计算未落库扣库存的预占张数，重建配额须扣除此项。 */
    @Select("""
            SELECT COALESCE(SUM(quantity),0) FROM stock_reservation
            WHERE tier_id=#{tierId} AND inventory_state='PENDING' AND status='RESERVED'
            """)
    int pendingQuantity(@Param("tierId") long tierId);

    /** 统计重建所需有效申请数量。 */
    @Select("""
            SELECT COUNT(*) FROM stock_reservation
            WHERE tier_id=#{tierId} AND status IN('RESERVED','ORDER_CREATED','PAID')
            """)
    int activeCount(@Param("tierId") long tierId);

    /** 历史有效订单没有申请时仍占限购资格，库存已经在历史流程扣除。 */
    @Select("""
            SELECT o.id,o.user_id,o.tier_id,o.quantity,o.create_time,
                COALESCE(o.expire_time,DATE_ADD(o.create_time,INTERVAL 30 MINUTE)) AS expires_at
            FROM order_info o WHERE o.tier_id=#{tierId} AND o.id>#{after} AND o.order_status IN(0,1,3,4)
                AND NOT EXISTS(SELECT 1 FROM stock_reservation r WHERE r.order_id=o.id)
            ORDER BY o.id LIMIT 100
            """)
    List<StockReservation> historicalBatch(@Param("tierId") long tierId, @Param("after") long after);

    /** 重建预期数也包含无申请的历史有效订单。 */
    @Select("""
            SELECT COUNT(*) FROM order_info o WHERE o.tier_id=#{tierId} AND o.order_status IN(0,1,3,4)
                AND NOT EXISTS(SELECT 1 FROM stock_reservation r WHERE r.order_id=o.id)
            """)
    int historicalCount(@Param("tierId") long tierId);

    /** 暂停屏障内把有效申请切换到新代次，旧事件不再修改配额。 */
    @Update("""
            UPDATE stock_reservation SET stock_epoch=#{epoch}
            WHERE tier_id=#{tierId} AND status IN('RESERVED','ORDER_CREATED','PAID')
            """)
    int rebase(@Param("tierId") long tierId, @Param("epoch") long epoch);
}
