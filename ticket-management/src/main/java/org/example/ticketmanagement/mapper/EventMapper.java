// org/example/ticketmanagement/mapper/EventMapper.java
package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.ticketmanagement.dto.EventQueryDTO;
import org.example.ticketmanagement.mapper.provider.EventSqlProvider;
import org.example.ticketmanagement.pojo.Event;

import java.util.List;

@Mapper
public interface EventMapper {

    /**
     * 1. 新增一个演出
     */
    @Insert("INSERT INTO event(name, artist_name, category_id, city_id, venue, "
            + "cover_image, description, status, sale_start_time, sale_end_time, "
            + "event_start_time, event_end_time, create_time, update_time) "
            + "VALUES(#{name}, #{artistName}, #{categoryId}, #{cityId}, #{venue}, "
            + "#{coverImage}, #{description}, #{status}, #{saleStartTime}, #{saleEndTime}, "
            + "#{eventStartTime}, #{eventEndTime}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Event event);

    /**
     * 2. 根据ID下架演出，保留场次、票档及交易历史引用
     */
    @Update("UPDATE event SET status=0,update_time=CURRENT_TIMESTAMP WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /**
     * 3. 更新一个演出的信息
     */
    @Update("UPDATE event SET " + "name = #{name}, " + "artist_name = #{artistName}, " + "category_id = #{categoryId}, "
            + "city_id = #{cityId}, " + "venue = #{venue}, " + "cover_image = #{coverImage}, "
            + "description = #{description}, " + "status = #{status}, " + "sale_start_time = #{saleStartTime}, "
            + "sale_end_time = #{saleEndTime}, " + "event_start_time = #{eventStartTime}, "
            + "event_end_time = #{eventEndTime}, " + "update_time = #{updateTime} " + "WHERE id = #{id}")
    int update(Event event);

    /**
     * 4. 根据ID查询一个演出
     */
    @Select("SELECT id, name, artist_name, category_id, city_id, venue, "
            + "cover_image, description, status, sale_start_time, sale_end_time, "
            + "event_start_time, event_end_time, create_time, update_time " + "FROM event WHERE id = #{id}")
    Event selectById(@Param("id") Long id);

    /**
     * 5. 查询所有演出
     */
    @Select("SELECT id, name, artist_name, category_id, city_id, venue, "
            + "cover_image, description, status, sale_start_time, sale_end_time, "
            + "event_start_time, event_end_time, create_time, update_time "
            + "FROM event ORDER BY event_start_time DESC, create_time DESC")
    List<Event> selectAll();

    /**
     * 6. 根据状态查询演出
     */
    @Select("SELECT id, name, artist_name, category_id, city_id, venue, "
            + "cover_image, description, status, sale_start_time, sale_end_time, "
            + "event_start_time, event_end_time, create_time, update_time " + "FROM event WHERE status = #{status} "
            + "ORDER BY event_start_time DESC, create_time DESC")
    List<Event> selectByStatus(@Param("status") Integer status);

    /**
     * 7. 根据分类查询演出
     */
    @Select("SELECT id, name, artist_name, category_id, city_id, venue, "
            + "cover_image, description, status, sale_start_time, sale_end_time, "
            + "event_start_time, event_end_time, create_time, update_time "
            + "FROM event WHERE category_id = #{categoryId} " + "ORDER BY event_start_time DESC, create_time DESC")
    List<Event> selectByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 8. 根据城市查询演出
     */
    @Select("SELECT id, name, artist_name, category_id, city_id, venue, "
            + "cover_image, description, status, sale_start_time, sale_end_time, "
            + "event_start_time, event_end_time, create_time, update_time " + "FROM event WHERE city_id = #{cityId} "
            + "ORDER BY event_start_time DESC, create_time DESC")
    List<Event> selectByCityId(@Param("cityId") Long cityId);

    /**
     * 9. 根据分类和城市查询演出
     */
    @Select("SELECT id, name, artist_name, category_id, city_id, venue, "
            + "cover_image, description, status, sale_start_time, sale_end_time, "
            + "event_start_time, event_end_time, create_time, update_time "
            + "FROM event WHERE category_id = #{categoryId} AND city_id = #{cityId} "
            + "ORDER BY event_start_time DESC, create_time DESC")
    List<Event> selectByCategoryAndCity(@Param("categoryId") Long categoryId, @Param("cityId") Long cityId);

    /**
     * 10. 根据艺人名称模糊查询
     */
    @Select("SELECT id, name, artist_name, category_id, city_id, venue, "
            + "cover_image, description, status, sale_start_time, sale_end_time, "
            + "event_start_time, event_end_time, create_time, update_time "
            + "FROM event WHERE artist_name LIKE CONCAT('%', #{artistName}, '%') "
            + "ORDER BY event_start_time DESC, create_time DESC")
    List<Event> selectByArtistName(@Param("artistName") String artistName);

    /**
     * 11. 根据演出名称模糊查询
     */
    @Select("SELECT id, name, artist_name, category_id, city_id, venue, "
            + "cover_image, description, status, sale_start_time, sale_end_time, "
            + "event_start_time, event_end_time, create_time, update_time "
            + "FROM event WHERE name LIKE CONCAT('%', #{name}, '%') "
            + "ORDER BY event_start_time DESC, create_time DESC")
    List<Event> selectByName(@Param("name") String name);

    /**
     * 获取热门演出（状态为已发布，按开始时间升序，限制数量）
     */
    @Select("SELECT id, name, artist_name, category_id, city_id, venue, "
            + "cover_image, description, status, sale_start_time, sale_end_time, "
            + "event_start_time, event_end_time, create_time, update_time "
            + "FROM `event` WHERE status = 1 AND event_start_time > NOW() "
            + "ORDER BY event_start_time LIMIT #{limit}")
    List<Event> selectHotEvents(@Param("limit") Integer limit);

    /**
     * 获取即将开售的演出（开售时间在未来）
     */
    @Select("SELECT id, name, artist_name, category_id, city_id, venue, "
            + "cover_image, description, status, sale_start_time, sale_end_time, "
            + "event_start_time, event_end_time, create_time, update_time "
            + "FROM `event` WHERE status = 1 AND sale_start_time > NOW() " + "ORDER BY sale_start_time LIMIT #{limit}")
    List<Event> selectUpcomingEvents(@Param("limit") Integer limit);

    /**
     * 分页查询演出列表
     */
    @SelectProvider(type = EventSqlProvider.class, method = "selectByCondition")
    List<Event> selectByCondition(@Param("queryDTO") EventQueryDTO queryDTO);

    /**
     * 统计查询演出总数
     */
    @SelectProvider(type = EventSqlProvider.class, method = "countByCondition")
    Long countByCondition(@Param("queryDTO") EventQueryDTO queryDTO);
}
