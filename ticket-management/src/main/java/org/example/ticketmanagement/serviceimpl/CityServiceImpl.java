package org.example.ticketmanagement.serviceimpl;

import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.CityDTO;
import org.example.ticketmanagement.dto.CityQueryDTO;
import org.example.ticketmanagement.vo.CityVO;
import org.example.ticketmanagement.mapper.CityMapper;
import org.example.ticketmanagement.pojo.City;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.service.CityService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CityServiceImpl implements CityService {

    @Autowired
    private CityMapper cityMapper;

    @Override
    @Transactional
    public boolean addCity(CityDTO cityDTO) {
        log.info("新增城市: {}", cityDTO.getName());

        // 1. 检查城市名称是否已存在
        City existingCity = cityMapper.selectByName(cityDTO.getName());
        if (existingCity != null) {
            log.warn("城市名称已存在: {}", cityDTO.getName());
            return false;
        }

        // 2. 检查城市代码是否已存在
        existingCity = cityMapper.selectByCode(cityDTO.getCode());
        if (existingCity != null) {
            log.warn("城市代码已存在: {}", cityDTO.getCode());
            return false;
        }

        // 3. DTO 转 Entity（并补充必要字段）
        City city = new City();
        BeanUtils.copyProperties(cityDTO, city);
        city.setCreateTime(LocalDateTime.now());
        city.setUpdateTime(LocalDateTime.now());

        // 4. 调用Mapper执行插入
        int affectedRows = cityMapper.insert(city);

        // 5. 根据受影响行数判断操作结果
        boolean success = (affectedRows == 1);
        if (success) {
            log.info("新增城市成功，ID: {}", city.getId());
        } else {
            log.error("新增城市失败，受影响行数: {}", affectedRows);
        }
        return success;
    }

    @Override
    @Transactional
    public boolean deleteCity(Long id) {
        log.info("删除城市，ID: {}", id);

        // 先检查是否存在，使反馈更友好
        City existingCity = cityMapper.selectById(id);
        if (existingCity == null) {
            log.warn("要删除的城市不存在，ID: {}", id);
            return false;
        }

        int affectedRows = cityMapper.deleteById(id);
        boolean success = (affectedRows == 1);
        if (success) {
            log.info("删除城市成功，ID: {}", id);
        } else {
            log.warn("删除城市未生效，ID: {}", id);
        }
        return success;
    }

    @Override
    @Transactional
    public boolean updateCity(Long id, CityDTO cityDTO) {
        log.info("更新城市，ID: {}", id);

        // 1. 检查要更新的目标是否存在
        City existingCity = cityMapper.selectById(id);
        if (existingCity == null) {
            log.warn("要更新的城市不存在，ID: {}", id);
            return false;
        }

        // 2. 检查新的城市名称是否与其他城市冲突
        City cityByName = cityMapper.selectByName(cityDTO.getName());
        if (cityByName != null && !cityByName.getId().equals(id)) {
            log.warn("城市名称已存在: {}", cityDTO.getName());
            return false;
        }

        // 3. 检查新的城市代码是否与其他城市冲突
        City cityByCode = cityMapper.selectByCode(cityDTO.getCode());
        if (cityByCode != null && !cityByCode.getId().equals(id)) {
            log.warn("城市代码已存在: {}", cityDTO.getCode());
            return false;
        }

        // 4. DTO 转 Entity（避免覆盖不需要更新的字段，如createTime）
        City city = new City();
        BeanUtils.copyProperties(cityDTO, city);
        city.setId(id);
        city.setUpdateTime(LocalDateTime.now());

        // 5. 执行更新
        int affectedRows = cityMapper.update(city);
        boolean success = (affectedRows == 1);
        if (success) {
            log.info("更新城市成功，ID: {}", id);
        } else {
            log.warn("更新城市未生效，ID: {}", id);
        }
        return success;
    }

    @Override
    public CityVO getCityById(Long id) {
        log.debug("根据ID查询城市，ID: {}", id);
        City city = cityMapper.selectById(id);
        if (city == null) {
            return null;
        }
        // Entity 转 VO
        return convertToVO(city);
    }
    @Override
    public City getCityByName(String cityName) {
        return cityMapper.selectByName(cityName);
    }

    @Override
    public List<CityVO> getAllCities() {
        log.debug("查询所有城市列表");
        List<City> cities = cityMapper.selectAll();
        // 使用Stream API将Entity列表转换为VO列表
        return cities.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<CityVO> getCitiesByStatus(Integer status) {
        log.debug("根据状态查询城市，status: {}", status);
        List<City> cities = cityMapper.selectByStatus(status);
        return cities.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<CityVO> getCitiesByHotLevel(Integer hotLevel) {
        log.debug("根据热门等级查询城市，hotLevel: {}", hotLevel);
        List<City> cities = cityMapper.selectByHotLevel(hotLevel);
        return cities.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<CityVO> queryCities(CityQueryDTO queryDTO) {
        log.debug("分页查询城市，参数: {}", queryDTO);

        // 1. 参数校验
        if (!queryDTO.validate()) {
            log.warn("分页参数无效: page={}, size={}", queryDTO.getPage(), queryDTO.getSize());
            queryDTO.setPage(1);
            queryDTO.setSize(10);
        }

        // 2. 查询总记录数
        Long total = cityMapper.countByCondition(queryDTO);
        log.debug("查询到符合条件的城市总数: {}", total);

        // 3. 如果没有数据，直接返回空结果
        if (total == 0) {
            log.debug("未找到符合条件的城市");
            return PageResult.empty(queryDTO);
        }

        // 4. 计算有效页码（防止请求超出范围的页码）
        int maxPage = (int) Math.ceil((double) total / queryDTO.getSize());
        if (queryDTO.getPage() > maxPage) {
            log.debug("请求页码 {} 超出范围，调整为最大页码 {}", queryDTO.getPage(), maxPage);
            queryDTO.setPage(maxPage);
        }

        // 5. 分页查询数据
        List<City> cities = cityMapper.selectByCondition(queryDTO);
        log.debug("查询到当前页城市数量: {}", cities.size());

        // 6. 转换为VO
        List<CityVO> voList = cities.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 7. 返回分页结果
        return PageResult.success(voList, total, queryDTO);
    }

    @Override
    public List<CityVO> searchCitiesByName(String name) {
        log.debug("根据名称模糊查询城市: {}", name);
        
        if (name == null || name.trim().isEmpty()) {
            log.warn("查询名称为空，返回所有城市");
            return getAllCities();
        }
        
        List<City> cities = cityMapper.selectByNameLike(name.trim());
        return cities.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 内部辅助方法：将 Entity 对象转换为 VO 对象
     * 将转换逻辑封装在这里，避免代码重复
     */
    private CityVO convertToVO(City city) {
        CityVO vo = new CityVO();
        BeanUtils.copyProperties(city, vo);
        return vo;
    }
}