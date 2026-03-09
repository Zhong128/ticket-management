package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.ticketmanagement.pojo.EventSession;

import java.util.List;

@Mapper
public interface EventSessionMapper {

    /**
     * 1. 新增一个场次
     * @param session 要插入的场次对象
     * @return 受影响的行数
     * 注意：@Options 注解用于获取数据库生成的主键并回填至 session 对象的 id 属性中
     */

    @Insert("INSERT INTO event_session(event_id, session_name, session_time, status, create_time) " +
            "VALUES(#{eventId}, #{sessionName}, #{sessionTime}, #{status}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EventSession session);

    /**
     * 2. 根据ID删除一个场次
     * @param id 场次ID
     * @return 受影响的行数
     */
    @Delete("DELETE FROM event_session WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /**
     * 3. 更新一个场次的信息
     * @param session 包含更新信息的场次对象，必须包含id
     * @return 受影响的行数
     */
    @Update("UPDATE event_session SET " +
            "event_id = #{eventId}, " +
            "session_name = #{sessionName}, " +
            "session_time = #{sessionTime}, " +
            "status = #{status} " +
            "WHERE id = #{id}")
    int update(EventSession session);

    /**
     * 4. 根据ID查询一个场次
     * @param id 场次ID
     * @return 查询到的场次对象，未找到则返回null
     */
    @Select("SELECT id, event_id, session_name, session_time, status, create_time " +
            "FROM event_session WHERE id = #{id}")
    EventSession selectById(@Param("id") Long id);

    /**
     * 5. 根据演出ID查询所有场次
     * @param eventId 演出ID
     * @return 该演出的所有场次列表，按时间升序排列
     */
    @Select("SELECT id, event_id, session_name, session_time, status, create_time " +
            "FROM event_session WHERE event_id = #{eventId} " +
            "ORDER BY session_time")
    List<EventSession> selectByEventId(@Param("eventId") Long eventId);

    /**
     * 6. 根据演出ID和状态查询场次
     * @param eventId 演出ID
     * @param status 状态 (0-禁用，1-启用)
     * @return 符合条件的场次列表，按时间升序排列
     */
    @Select("SELECT id, event_id, session_name, session_time, status, create_time " +
            "FROM event_session WHERE event_id = #{eventId} AND status = #{status} " +
            "ORDER BY session_time")
    List<EventSession> selectByEventIdAndStatus(@Param("eventId") Long eventId,
                                                @Param("status") Integer status);

    /**
     * 7. 查询所有场次（通常用于后台管理）
     * @return 场次列表，按创建时间倒序排列
     */
    @Select("SELECT id, event_id, session_name, session_time, status, create_time " +
            "FROM event_session ORDER BY create_time DESC")
    List<EventSession> selectAll();

    /**
     * 8. 根据状态查询场次
     * @param status 状态 (0-禁用，1-启用)
     * @return 符合条件的场次列表，按时间升序排列
     */
    @Select("SELECT id, event_id, session_name, session_time, status, create_time " +
            "FROM event_session WHERE status = #{status} " +
            "ORDER BY session_time")
    List<EventSession> selectByStatus(@Param("status") Integer status);

    /**
     * 9. 检查同一演出是否存在时间冲突的场次
     * 用于在新增或更新场次时校验，避免同一演出在同一时间有多个场次
     * @param eventId 演出ID
     * @param sessionTime 场次时间
     * @param excludeId 排除的场次ID（用于更新时排除自身）
     * @return 存在冲突的场次数量
     */
    @Select("SELECT COUNT(*) FROM event_session " +
            "WHERE event_id = #{eventId} AND session_time = #{sessionTime} " +
            "AND id != COALESCE(#{excludeId}, 0)")
    int countTimeConflict(@Param("eventId") Long eventId,
                          @Param("sessionTime") java.time.LocalDateTime sessionTime,
                          @Param("excludeId") Long excludeId);

    /**
     * 获取即将开始的场次（状态为启用，时间在未来）
     */
    @Select("SELECT id, event_id, session_name, session_time, status, create_time " +
            "FROM event_session WHERE status = 1 AND session_time > NOW() " +
            "ORDER BY session_time LIMIT #{limit}")
    List<EventSession> selectUpcomingSessions(@Param("limit") Integer limit);
}