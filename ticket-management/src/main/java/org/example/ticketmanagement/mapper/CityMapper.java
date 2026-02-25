// org/example/ticketmanagement/mapper/CityMapper.java
package org.example.ticketmanagement.mapper;

import org.apache.ibatis.annotations.*;
import org.example.ticketmanagement.dto.CityQueryDTO;
import org.example.ticketmanagement.mapper.provider.CitySqlProvider;
import org.example.ticketmanagement.pojo.City;

import java.util.List;

@Mapper
public interface CityMapper {

    /**
     * 1. 新增一个城市
     * @param city 要插入的城市对象
     */
    @Insert("INSERT INTO city(name, code, province, hot_level, status, create_time, update_time) " +
            "VALUES(#{name}, #{code}, #{province}, #{hotLevel}, #{status}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(City city);

    /**
     * 2. 根据ID删除一个城市
     * @param id 城市ID
     * @return 受影响的行数
     */
    @Delete("DELETE FROM city WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /**
     * 3. 更新一个城市的信息
     * @param city 包含更新信息的城市对象，必须包含id
     * @return 受影响的行数
     */
    @Update("UPDATE city SET " +
            "name = #{name}, " +
            "code = #{code}, " +
            "province = #{province}, " +
            "hot_level = #{hotLevel}, " +
            "status = #{status}, " +
            "update_time = #{updateTime} " +
            "WHERE id = #{id}")
    int update(City city);

    /**
     * 4. 根据ID查询一个城市
     * @param id 城市ID
     * @return 查询到的城市对象，未找到则返回null
     */
    @Select("SELECT id, name, code, province, hot_level, status, create_time, update_time " +
            "FROM city WHERE id = #{id}")
    City selectById(@Param("id") Long id);

    /**
     * 5. 根据城市名称查询城市
     * @param name 城市名称
     * @return 查询到的城市对象，未找到则返回null
     */
    @Select("SELECT id, name, code, province, hot_level, status, create_time, update_time " +
            "FROM city WHERE name = #{name}")
    City selectByName(@Param("name") String name);

    /**
     * 6. 根据城市代码查询城市
     * @param code 城市代码
     * @return 查询到的城市对象，未找到则返回null
     */
    @Select("SELECT id, name, code, province, hot_level, status, create_time, update_time " +
            "FROM city WHERE code = #{code}")
    City selectByCode(@Param("code") String code);

    /**
     * 7. 查询所有城市（按热门等级降序、创建时间降序排列）
     * @return 城市列表
     */
    @Select("SELECT id, name, code, province, hot_level, status, create_time, update_time " +
            "FROM city ORDER BY hot_level DESC, create_time DESC")
    List<City> selectAll();

    /**
     * 8. 根据状态查询城市
     * @param status 状态 (0-禁用，1-启用)
     * @return 符合条件的城市列表
     */
    @Select("SELECT id, name, code, province, hot_level, status, create_time, update_time " +
            "FROM city WHERE status = #{status} ORDER BY hot_level DESC, create_time DESC")
    List<City> selectByStatus(@Param("status") Integer status);

    /**
     * 9. 根据热门等级查询城市
     * @param hotLevel 热门等级 (0-普通，1-热门)
     * @return 符合条件的城市列表
     */
    @Select("SELECT id, name, code, province, hot_level, status, create_time, update_time " +
            "FROM city WHERE hot_level = #{hotLevel} AND status = 1 ORDER BY create_time DESC")
    List<City> selectByHotLevel(@Param("hotLevel") Integer hotLevel);

    /**
     * 根据城市名称模糊查询城市
     * @param name 城市名称（支持模糊匹配）
     * @return 符合条件的城市列表
     */
    @Select("SELECT id, name, code, province, hot_level, status, create_time, update_time " +
            "FROM city WHERE name LIKE CONCAT('%', #{name}, '%') " +
            "ORDER BY hot_level DESC, create_time DESC")
    List<City> selectByNameLike(@Param("name") String name);

/**
 * 分页查询城市（使用SQL Provider动态生成SQL）
 */
    @SelectProvider(type = CitySqlProvider.class, method = "selectByCondition")
    List<City> selectByCondition(CityQueryDTO queryDTO);

/**
 * 统计符合条件的城市数量
 */
    @SelectProvider(type = CitySqlProvider.class, method = "countByCondition")
    Long countByCondition(CityQueryDTO queryDTO);
}