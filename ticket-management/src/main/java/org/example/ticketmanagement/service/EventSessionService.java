package org.example.ticketmanagement.service;

import org.example.ticketmanagement.dto.EventSessionDTO;
import org.example.ticketmanagement.vo.EventSessionVO;
import java.util.List;

public interface EventSessionService {

    /**
     * 新增场次
     * @param eventSessionDTO 场次数据
     * @return 新增成功返回 true，失败返回 false
     */
    boolean addSession(EventSessionDTO eventSessionDTO);

    /**
     * 根据ID删除场次
     * @param id 场次ID
     * @return 删除成功返回 true，失败返回 false
     */
    boolean deleteSession(Long id);

    /**
     * 更新场次信息
     * @param id 要更新的场次ID
     * @param eventSessionDTO 新的场次数据
     * @return 更新成功返回 true，失败返回 false
     */
    boolean updateSession(Long id, EventSessionDTO eventSessionDTO);

    /**
     * 根据ID查询场次详情
     * @param id 场次ID
     * @return 场次详情视图对象，未找到返回 null
     */
    EventSessionVO getSessionById(Long id);

    /**
     * 查询所有场次列表
     * @return 场次视图对象列表
     */
    List<EventSessionVO> getAllSessions();

    /**
     * 根据状态查询场次列表
     * @param status 状态 (0-禁用，1-启用)
     * @return 符合条件的场次列表
     */
    List<EventSessionVO> getSessionsByStatus(Integer status);

    /**
     * 根据演出ID和状态查询场次列表
     * @param eventId 演出ID
     * @param status 状态 (0-禁用，1-启用)
     * @return 符合条件的场次列表
     */
    List<EventSessionVO> getSessionsByEventIdAndStatus(Long eventId, Integer status);

    /**
     * 获取即将开始的场次
     * @param limit 返回数量限制
     * @return 即将开始的场次列表
     */
    List<EventSessionVO> getUpcomingSessions(Integer limit);
}