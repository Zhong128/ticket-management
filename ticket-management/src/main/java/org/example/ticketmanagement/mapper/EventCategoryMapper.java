package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.ticketmanagement.dto.EventCategoryQueryDTO;
import org.example.ticketmanagement.mapper.provider.EventCategorySqlProvider;
import org.example.ticketmanagement.pojo.EventCategory;

import java.util.List;

@Mapper
public interface EventCategoryMapper {

    /**
     * 1. 新增一个演出分类
     */
    @Insert("INSERT INTO event_category(name, sort_order, status, create_time) " +
            "VALUES(#{name}, #{sortOrder}, #{status}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")//这是MyBatis中处理数据库自增主键的关键注解。
    int insert(EventCategory category);

    /**
     * 2. 根据ID删除一个演出分类（逻辑删除或物理删除，这里先做物理删除）
     */
    @Delete("DELETE FROM event_category WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /**
     * 3. 更新一个演出分类的信息
     */
    @Update("UPDATE event_category SET " +
            "name = #{name}, " +
            "sort_order = #{sortOrder}, " +
            "status = #{status} " +
            "WHERE id = #{id}")
    int update(EventCategory category);

    /**
     * 4. 根据ID查询一个演出分类
     */
    @Select("SELECT id, name, sort_order, status, create_time FROM event_category WHERE id = #{id}")
    EventCategory selectById(@Param("id") Long id);

    /**
     * 5. 根据名称查询一个演出分类
     */
    @Select("SELECT id, name, sort_order, status, create_time FROM event_category WHERE name = #{name}")
    EventCategory selectByName(@Param("name") String name);

    /**
     * 6. 查询所有演出分类（通常用于后台管理列表或前台下拉选择）
     */
    @Select("SELECT id, name, sort_order, status, create_time FROM event_category ORDER BY sort_order ASC, create_time DESC")
    List<EventCategory> selectAll();

    /**
     * 7. 根据状态查询演出分类（例如：只查询启用的分类，用于前台展示）
     */
    @Select("SELECT id, name, sort_order, status, create_time FROM event_category WHERE status = #{status} ORDER BY sort_order ASC")
    List<EventCategory> selectByStatus(@Param("status") Integer status);

    /**
     * 8.分页查询分类（使用SQL Provider动态生成SQL）
     */
    @SelectProvider(type = EventCategorySqlProvider.class, method = "selectByCondition")
    List<EventCategory> selectByCondition(EventCategoryQueryDTO queryDTO);

    /**
     * 9.统计符合条件的分类数量
     */
    @SelectProvider(type = EventCategorySqlProvider.class, method = "countByCondition")
    Long countByCondition(EventCategoryQueryDTO queryDTO);
}