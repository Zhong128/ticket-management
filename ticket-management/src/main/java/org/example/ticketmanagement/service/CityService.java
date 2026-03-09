package org.example.ticketmanagement.service;

import org.example.ticketmanagement.dto.CityDTO;
import org.example.ticketmanagement.dto.CityQueryDTO;
import org.example.ticketmanagement.pojo.City;
import org.example.ticketmanagement.vo.CityVO;
import org.example.ticketmanagement.pojo.PageResult;

import java.util.List;

public interface CityService {

    /**
     * 新增城市
     * @param cityDTO 城市数据
     * @return 新增成功返回 true，失败返回 false
     */
    boolean addCity(CityDTO cityDTO);

    /**
     * 根据ID删除城市
     * @param id 城市ID
     * @return 删除成功返回 true，失败返回 false
     */
    boolean deleteCity(Long id);

    /**
     * 更新城市信息
     * @param id 要更新的城市ID
     * @param cityDTO 新的城市数据
     * @return 更新成功返回 true，失败返回 false
     */
    boolean updateCity(Long id, CityDTO cityDTO);

    /**
     * 根据ID查询城市详情
     * @param id 城市ID
     * @return 城市详情视图对象，未找到返回 null
     */
    CityVO getCityById(Long id);

    /**
     * 查询所有城市列表
     * @return 城市视图对象列表
     */
    List<CityVO> getAllCities();
    /**
     * 根据城市名称查询城市信息
     */
    City getCityByName(String cityName);

    /**
     * 根据城市名称模糊查询城市列表
     * @param name 城市名称（支持模糊匹配）
     * @return 符合条件的城市视图对象列表
     */
    List<CityVO> searchCitiesByName(String name);

    // 新增分页查询方法
    PageResult<CityVO> queryCities(CityQueryDTO queryDTO);
}