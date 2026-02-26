// org/example/ticketmanagement/mapper/TicketTierMapper.java
package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.ticketmanagement.mapper.provider.TicketTierSqlProvider;
import org.example.ticketmanagement.pojo.TicketTier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface TicketTierMapper {

    /**
     * 1. 新增一个票档
     * @param tier 要插入的票档对象
     * @return 受影响的行数
     */
    @Insert("INSERT INTO ticket_tier(event_id, session_id, tier_name, original_price, current_price, " +
            "total_stock, available_stock, status, create_time, update_time) " +
            "VALUES(#{eventId}, #{sessionId}, #{tierName}, #{originalPrice}, #{currentPrice}, " +
            "#{totalStock}, #{availableStock}, #{status}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TicketTier tier);

    /**
     * 2. 根据ID删除一个票档
     * @param id 票档ID
     * @return 受影响的行数
     */
    @Delete("DELETE FROM ticket_tier WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /**
     * 3. 更新一个票档的信息
     * @param tier 包含更新信息的票档对象，必须包含id
     * @return 受影响的行数
     */
    @Update("UPDATE ticket_tier SET " +
            "event_id = #{eventId}, " +
            "session_id = #{sessionId}, " +
            "tier_name = #{tierName}, " +
            "original_price = #{originalPrice}, " +
            "current_price = #{currentPrice}, " +
            "total_stock = #{totalStock}, " +
            "available_stock = #{availableStock}, " +
            "status = #{status}, " +
            "update_time = #{updateTime} " +
            "WHERE id = #{id}")
    int update(TicketTier tier);

    /**
     * 4. 根据ID查询一个票档
     * @param id 票档ID
     * @return 查询到的票档对象，未找到则返回null
     */
    @Select("SELECT id, event_id, session_id, tier_name, original_price, current_price, " +
            "total_stock, available_stock, status, create_time, update_time " +
            "FROM ticket_tier WHERE id = #{id}")
    TicketTier selectById(@Param("id") Long id);

    /**
     * 5. 根据演出ID查询所有票档
     * @param eventId 演出ID
     * @return 该演出的所有票档列表，按价格升序排列
     */
    @Select("SELECT id, event_id, session_id, tier_name, original_price, current_price, " +
            "total_stock, available_stock, status, create_time, update_time " +
            "FROM ticket_tier WHERE event_id = #{eventId} " +
            "ORDER BY current_price ASC, create_time DESC")
    List<TicketTier> selectByEventId(@Param("eventId") Long eventId);

    /**
     * 6. 根据场次ID查询所有票档
     * @param sessionId 场次ID
     * @return 该场次的所有票档列表，按价格升序排列
     */
    @Select("SELECT id, event_id, session_id, tier_name, original_price, current_price, " +
            "total_stock, available_stock, status, create_time, update_time " +
            "FROM ticket_tier WHERE session_id = #{sessionId} " +
            "ORDER BY current_price ASC, create_time DESC")
    List<TicketTier> selectBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 7. 根据演出ID和状态查询票档
     * @param eventId 演出ID
     * @param status 状态 (0-禁用，1-启用)
     * @return 符合条件的票档列表，按价格升序排列
     */
    @Select("SELECT id, event_id, session_id, tier_name, original_price, current_price, " +
            "total_stock, available_stock, status, create_time, update_time " +
            "FROM ticket_tier WHERE event_id = #{eventId} AND status = #{status} " +
            "ORDER BY current_price ASC")
    List<TicketTier> selectByEventIdAndStatus(@Param("eventId") Long eventId,
                                              @Param("status") Integer status);

    /**
     * 8. 根据场次ID和状态查询票档
     * @param sessionId 场次ID
     * @param status 状态 (0-禁用，1-启用)
     * @return 符合条件的票档列表，按价格升序排列
     */
    @Select("SELECT id, event_id, session_id, tier_name, original_price, current_price, " +
            "total_stock, available_stock, status, create_time, update_time " +
            "FROM ticket_tier WHERE session_id = #{sessionId} AND status = #{status} " +
            "ORDER BY current_price ASC")
    List<TicketTier> selectBySessionIdAndStatus(@Param("sessionId") Long sessionId,
                                                @Param("status") Integer status);

    /**
     * 9. 查询所有票档（通常用于后台管理）
     * @return 票档列表，按创建时间倒序排列
     */
    @Select("SELECT id, event_id, session_id, tier_name, original_price, current_price, " +
            "total_stock, available_stock, status, create_time, update_time " +
            "FROM ticket_tier ORDER BY create_time DESC")
    List<TicketTier> selectAll();

    /**
     * 10. 根据状态查询票档
     * @param status 状态 (0-禁用，1-启用)
     * @return 符合条件的票档列表，按创建时间倒序排列
     */
    @Select("SELECT id, event_id, session_id, tier_name, original_price, current_price, " +
            "total_stock, available_stock, status, create_time, update_time " +
            "FROM ticket_tier WHERE status = #{status} " +
            "ORDER BY create_time DESC")
    List<TicketTier> selectByStatus(@Param("status") Integer status);

    /**
     * 11. 检查同一场次是否存在相同名称的票档（用于校验唯一性）
     * 用于在新增或更新票档时校验，避免同一场次有重复的票档名称
     * @param sessionId 场次ID
     * @param tierName 票档名称
     * @param excludeId 排除的票档ID（用于更新时排除自身）
     * @return 存在相同名称的票档数量
     */
    @Select("SELECT COUNT(*) FROM ticket_tier " +
            "WHERE session_id = #{sessionId} AND tier_name = #{tierName} " +
            "AND id != COALESCE(#{excludeId}, 0)")
    int countByNameConflict(@Param("sessionId") Long sessionId,
                            @Param("tierName") String tierName,
                            @Param("excludeId") Long excludeId);

    /**
     * 12. 更新票档的可用库存（后续抢票功能使用）
     * 注意：这里使用了乐观锁的思路，但现阶段我们只做数据管理，不处理并发
     * @param id 票档ID
     * @param quantity 扣减数量（正数表示扣减，负数表示增加）
     * @return 受影响的行数
     */
    @Update("UPDATE ticket_tier SET available_stock = available_stock - #{quantity}, " +
            "update_time = NOW() WHERE id = #{id} AND available_stock >= #{quantity}")
    int updateStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 13. 根据价格范围查询票档
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @param status 状态 (1-启用)
     * @return 符合条件的票档列表，按价格升序排列
     */
    @Select("SELECT id, event_id, session_id, tier_name, original_price, current_price, " +
            "total_stock, available_stock, status, create_time, update_time " +
            "FROM ticket_tier WHERE status = #{status} " +
            "AND current_price BETWEEN #{minPrice} AND #{maxPrice} " +
            "ORDER BY current_price ASC")
    List<TicketTier> selectByPriceRange(@Param("minPrice") BigDecimal minPrice,
                                        @Param("maxPrice") BigDecimal maxPrice,
                                        @Param("status") Integer status);
    /**
     * 14. 根据场次ID查询票档库存信息（仅返回是否有库存）
     * 只返回每个票档是否有库存（available_stock > 0）
     */
    @Select("SELECT id, tier_name, available_stock > 0 as has_stock " +
            "FROM ticket_tier WHERE session_id = #{sessionId} AND status = 1")
    List<Map<String, Object>> selectStockInfoBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 15. 根据场次ID查询是否有票档已开票
     * 判断依据：当前时间 >= 场次的开售时间
     * 需要关联event_session表查询sale_start_time
     */
    @Select("SELECT COUNT(*) > 0 as has_tickets_on_sale " +
            "FROM ticket_tier tt " +
            "INNER JOIN event_session es ON tt.session_id = es.id " +
            "INNER JOIN event e ON es.event_id = e.id " +
            "WHERE tt.session_id = #{sessionId} " +
            "AND tt.status = 1 " +
            "AND e.sale_start_time <= NOW()")
    Boolean hasTicketsOnSaleBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 16. 根据演出ID查询所有场次的票档库存信息
     */
    @Select("SELECT tt.id, tt.tier_name, tt.available_stock > 0 as has_stock " +
            "FROM ticket_tier tt " +
            "INNER JOIN event_session es ON tt.session_id = es.id " +
            "WHERE tt.event_id = #{eventId} AND tt.status = 1")
    List<Map<String, Object>> selectStockInfoByEventId(@Param("eventId") Long eventId);

    /**
     * 17. 根据演出ID查询是否有任何票档已开票
     */
    @Select("SELECT COUNT(*) > 0 as has_tickets_on_sale " +
            "FROM ticket_tier tt " +
            "INNER JOIN event_session es ON tt.session_id = es.id " +
            "INNER JOIN event e ON es.event_id = e.id " +
            "WHERE tt.event_id = #{eventId} " +
            "AND tt.status = 1 " +
            "AND e.sale_start_time <= NOW()")
    Boolean hasTicketsOnSaleByEventId(@Param("eventId") Long eventId);

    /**
     * 18. 根据条件查询票档库存统计（动态SQL）
     * @param eventId 演出ID（可选）
     * @param sessionId 场次ID（可选）
     * @param tierId 票档ID（可选）
     * @return 符合条件的票档列表
     */
    @SelectProvider(type = TicketTierSqlProvider.class, method = "selectByCondition")
    List<TicketTier> selectByCondition(@Param("eventId") Long eventId,
                                       @Param("sessionId") Long sessionId,
                                       @Param("tierId") Long tierId);

    /**
     * 19. 根据价格范围和状态查询票档（动态SQL）
     * @param minPrice 最低价格（可选）
     * @param maxPrice 最高价格（可选）
     * @param status 状态
     * @return 符合条件的票档列表
     */
    @SelectProvider(type = TicketTierSqlProvider.class, method = "selectByPriceRangeAndStatus")
    List<TicketTier> selectByPriceRangeAndStatus(@Param("minPrice") BigDecimal minPrice,
                                                 @Param("maxPrice") BigDecimal maxPrice,
                                                 @Param("status") Integer status);



}