package org.example.ticketmanagement.service;

import org.example.ticketmanagement.dto.EventDTO;
import org.example.ticketmanagement.dto.EventQueryDTO;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.vo.EventVO;

import java.util.List;

public interface  EventService {

    /**
     * 新增演出
     * @param eventDTO 演出数据
     * @return 新增成功返回 true，失败返回 false
     */
    boolean addEvent(EventDTO eventDTO);

    /**
     * 根据ID删除演出
     * @param id 演出ID
     * @return 删除成功返回 true，失败返回 false
     */
    boolean deleteEvent(Long id);

    /**
     * 更新演出信息
     * @param id 要更新的演出ID
     * @param eventDTO 新的演出数据
     * @return 更新成功返回 true，失败返回 false
     */
    boolean updateEvent(Long id, EventDTO eventDTO);

    /**
     * 根据ID查询演出详情
     * @param id 演出ID
     * @return 演出详情视图对象，未找到返回 null
     */
    EventVO getEventById(Long id);

    /**
     * 查询所有演出列表
     * @return 演出视图对象列表
     */
    List<EventVO> getAllEvents();

    /**
     * 根据分类和城市查询演出列表
     * @param categoryId 分类ID
     * @param cityId 城市ID
     * @return 符合条件的演出列表
     */
    List<EventVO> getEventsByCategoryAndCity(Long categoryId, Long cityId);

    /**
     * 获取热门演出（状态为已发布，按时间排序）
     * @param limit 返回数量限制
     * @return 热门演出列表
     */
    List<EventVO> getHotEvents(Integer limit);

    /**
     * 获取即将开售的演出（开售时间在未来）
     * @return 即将开售的演出列表
     */
    List<EventVO> getUpcomingEvents(Integer limit);

    /**
     * 分页查询演出列表
     * @param queryDTO 查询参数
     * @return 分页结果
     */
    PageResult<EventVO> queryEvents(EventQueryDTO queryDTO);
}