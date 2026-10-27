package org.example.ticketmanagement.serviceimpl;

import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.EventDTO;
import org.example.ticketmanagement.dto.EventQueryDTO;
import org.example.ticketmanagement.vo.EventVO;
import org.example.ticketmanagement.vo.EventPriceVO;
import org.example.ticketmanagement.mapper.CityMapper;
import org.example.ticketmanagement.mapper.EventCategoryMapper;
import org.example.ticketmanagement.mapper.EventMapper;
import org.example.ticketmanagement.pojo.City;
import org.example.ticketmanagement.pojo.Event;
import org.example.ticketmanagement.pojo.EventCategory;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.service.EventService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EventServiceImpl implements EventService {

    @Autowired
    private EventMapper eventMapper;

    @Autowired
    private EventCategoryMapper eventCategoryMapper;

    @Autowired
    private CityMapper cityMapper;

    @Override
    @Transactional
    public boolean addEvent(EventDTO eventDTO) {
        log.info("新增演出: {}", eventDTO.getName());

        // 1. 校验分类是否存在且启用
        EventCategory category = eventCategoryMapper.selectById(eventDTO.getCategoryId());
        if (category == null) {
            log.warn("分类不存在: {}", eventDTO.getCategoryId());
            return false;
        }
        if (category.getStatus() == 0) {
            log.warn("分类已禁用: {}", eventDTO.getCategoryId());
            return false;
        }

        // 2. 校验城市是否存在且启用
        City city = cityMapper.selectById(eventDTO.getCityId());
        if (city == null) {
            log.warn("城市不存在: {}", eventDTO.getCityId());
            return false;
        }
        if (city.getStatus() == 0) {
            log.warn("城市已禁用: {}", eventDTO.getCityId());
            return false;
        }

        // 3. 校验时间逻辑
        if (!validateEventTime(eventDTO)) {
            log.warn("演出时间逻辑校验失败");
            return false;
        }

        // 4. DTO 转 Entity（并补充必要字段）
        Event event = new Event();
        BeanUtils.copyProperties(eventDTO, event);
        event.setCreateTime(LocalDateTime.now());
        event.setUpdateTime(LocalDateTime.now());

        // 5. 调用Mapper执行插入
        int affectedRows = eventMapper.insert(event);

        // 6. 根据受影响行数判断操作结果
        boolean success = (affectedRows == 1);
        if (success) {
            log.info("新增演出成功，ID: {}", event.getId());
        } else {
            log.error("新增演出失败，受影响行数: {}", affectedRows);
        }
        return success;
    }

    @Override
    @Transactional
    public boolean deleteEvent(Long id) {
        log.info("删除演出，ID: {}", id);

        // 先检查是否存在，使反馈更友好
        Event existingEvent = eventMapper.selectById(id);
        if (existingEvent == null) {
            log.warn("要删除的演出不存在，ID: {}", id);
            return false;
        }

        int affectedRows = eventMapper.deleteById(id);
        boolean success = (affectedRows == 1);
        if (success) {
            log.info("删除演出成功，ID: {}", id);
        } else {
            log.warn("删除演出未生效，ID: {}", id);
        }
        return success;
    }

    @Override
    @Transactional
    public boolean updateEvent(Long id, EventDTO eventDTO) {
        log.info("更新演出，ID: {}", id);

        // 1. 检查要更新的目标是否存在
        Event existingEvent = eventMapper.selectById(id);
        if (existingEvent == null) {
            log.warn("要更新的演出不存在，ID: {}", id);
            return false;
        }

        // 2. 校验分类是否存在且启用（如果分类ID有变化）
        if (eventDTO.getCategoryId() != null &&
                !eventDTO.getCategoryId().equals(existingEvent.getCategoryId())) {
            EventCategory category = eventCategoryMapper.selectById(eventDTO.getCategoryId());
            if (category == null || category.getStatus() == 0) {
                log.warn("分类不存在或已禁用: {}", eventDTO.getCategoryId());
                return false;
            }
        }

        // 3. 校验城市是否存在且启用（如果城市ID有变化）
        if (eventDTO.getCityId() != null &&
                !eventDTO.getCityId().equals(existingEvent.getCityId())) {
            City city = cityMapper.selectById(eventDTO.getCityId());
            if (city == null || city.getStatus() == 0) {
                log.warn("城市不存在或已禁用: {}", eventDTO.getCityId());
                return false;
            }
        }

        // 4. 校验时间逻辑
        if (!validateEventTime(eventDTO)) {
            log.warn("演出时间逻辑校验失败");
            return false;
        }

        // 5. DTO 转 Entity
        Event event = new Event();
        BeanUtils.copyProperties(eventDTO, event);
        event.setId(id);
        event.setUpdateTime(LocalDateTime.now());

        // 6. 执行更新
        int affectedRows = eventMapper.update(event);
        boolean success = (affectedRows == 1);
        if (success) {
            log.info("更新演出成功，ID: {}", id);
        } else {
            log.warn("更新演出未生效，ID: {}", id);
        }
        return success;
    }

    @Override
    public EventVO getEventById(Long id) {
        log.debug("根据ID查询演出，ID: {}", id);
        Event event = eventMapper.selectById(id);
        if (event == null) {
            return null;
        }
        // Entity 转 VO（基础信息）
        return convertToVOs(List.of(event)).getFirst();
    }

    @Override
    public EventVO getPublicEventById(Long id) {
        // 可见性规则留在业务层，下架或草稿不进入前台展示转换。
        Event event = eventMapper.selectById(id);
        if (event == null || !Integer.valueOf(1).equals(event.getStatus())) {
            return null;
        }
        return convertToVOs(List.of(event)).getFirst();
    }

    @Override
    public PageResult<EventVO> queryPublicEvents(EventQueryDTO queryDTO) {
        queryDTO.setStatus(1);
        return queryEvents(queryDTO);
    }

    @Override
    public List<EventVO> getAllEvents() {
        log.debug("查询所有演出列表");
        List<Event> events = eventMapper.selectAll();
        return convertToVOs(events);
    }

    @Override
    public List<EventVO> getEventsByCategoryAndCity(Long categoryId, Long cityId) {
        log.debug("根据分类和城市查询演出，categoryId: {}, cityId: {}", categoryId, cityId);
        List<Event> events = eventMapper.selectByCategoryAndCity(categoryId, cityId);
        return convertToVOs(events);
    }

    @Override
    public List<EventVO> getHotEvents(Integer limit) {
        log.debug("获取热门演出，limit: {}", limit);
        int actualLimit = Math.min(limit, 100);
        List<Event> events = eventMapper.selectHotEvents(actualLimit);
        return convertToVOs(events);
    }

    @Override
    public List<EventVO> getUpcomingEvents(Integer limit) {
        log.debug("获取即将开售的演出，limit: {}", limit);

        // 调用Mapper的selectUpcomingEvents方法
        List<Event> events = eventMapper.selectUpcomingEvents(limit);

        return convertToVOs(events);
    }

    @Override
    public PageResult<EventVO> queryEvents(EventQueryDTO queryDTO) {
        log.debug("分页查询演出，参数: {}", queryDTO);

        // 1. 参数校验
        if (!queryDTO.validate()) {
            log.warn("分页参数无效: page={}, size={}", queryDTO.getPage(), queryDTO.getSize());
            queryDTO.setPage(1);
            queryDTO.setSize(10);
        }

        // 2. 查询总记录数
        Long total = eventMapper.countByCondition(queryDTO);
        log.debug("查询到符合条件的演出总数: {}", total);

        // 3. 如果没有数据，直接返回空结果
        if (total == 0) {
            log.debug("未找到符合条件的演出");
            return PageResult.empty(queryDTO);
        }

        // 4. 计算有效页码
        int maxPage = (int) Math.ceil((double) total / queryDTO.getSize());
        if (queryDTO.getPage() > maxPage) {
            log.debug("请求页码 {} 超出范围，调整为最大页码 {}", queryDTO.getPage(), maxPage);
            queryDTO.setPage(maxPage);
        }

        // 5. 分页查询数据
        List<Event> events = eventMapper.selectByCondition(queryDTO);
        log.debug("查询到当前页演出数量: {}", events.size());

        // 6. 转换为VO
        List<EventVO> voList = convertToVOs(events);

        // 7. 返回分页结果
        return PageResult.success(voList, total, queryDTO);
    }

    /**
     * 校验演出时间逻辑
     * 业务规则：
     * 1. 开售时间 < 停售时间
     * 2. 停售时间 <= 演出开始时间（通常售票会持续到演出开始）
     * 3. 演出开始时间 < 演出结束时间
     */
    private boolean validateEventTime(EventDTO eventDTO) {
        if (eventDTO.getSaleStartTime() == null || eventDTO.getSaleEndTime() == null ||
                eventDTO.getEventStartTime() == null || eventDTO.getEventEndTime() == null) {
            return false;
        }

        // 检查开售时间 < 停售时间
        if (!eventDTO.getSaleStartTime().isBefore(eventDTO.getSaleEndTime())) {
            log.warn("开售时间必须早于停售时间");
            return false;
        }

        // 检查停售时间 <= 演出开始时间（允许售票到演出开始）
        if (eventDTO.getSaleEndTime().isAfter(eventDTO.getEventStartTime())) {
            log.warn("停售时间不能晚于演出开始时间");
            return false;
        }

        // 检查演出开始时间 < 演出结束时间
        if (!eventDTO.getEventStartTime().isBefore(eventDTO.getEventEndTime())) {
            log.warn("演出开始时间必须早于演出结束时间");
            return false;
        }

        return true;
    }

    /** 转换演出列表并批量补充最低展示价。 */
    private List<EventVO> convertToVOs(List<Event> events) {
        if (events.isEmpty()) {
            return List.of();
        }

        // 批量补充展示价，避免每张演出卡片额外查询票档。
        List<Long> ids = events.stream().map(Event::getId).toList();
        Map<Long, BigDecimal> prices = new HashMap<>();
        for (EventPriceVO price : eventMapper.selectMinPrices(ids)) {
            prices.put(price.getEventId(), price.getMinPrice());
        }
        return events.stream().map(event -> {
            EventVO vo = convertToVO(event);
            vo.setMinPrice(prices.get(event.getId()));
            return vo;
        }).collect(Collectors.toList());
    }

    /** 转换单条演出及既有关联名称。 */
    private EventVO convertToVO(Event event) {
        EventVO vo = new EventVO();
        BeanUtils.copyProperties(event, vo);

        // 为可能为null的字段设置默认值
        if (vo.getArtistName() == null) {
            vo.setArtistName("");  // 艺人名称为空时设置为空字符串
        }
        
        if (vo.getCoverImage() == null) {
            vo.setCoverImage("");  // 封面图为空时设置为空字符串
        }
        
        if (vo.getDescription() == null) {
            vo.setDescription("");  // 描述为空时设置为空字符串
        }

        // 查询并填充分类名称
        if (event.getCategoryId() != null) {
            EventCategory category = eventCategoryMapper.selectById(event.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }

        // 查询并填充城市名称
        if (event.getCityId() != null) {
            City city = cityMapper.selectById(event.getCityId());
            if (city != null) {
                vo.setCityName(city.getName());
            }
        }

        return vo;
    }
}
