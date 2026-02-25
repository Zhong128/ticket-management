// org/example/ticketmanagement/service/impl/EventSessionServiceImpl.java
package org.example.ticketmanagement.serviceimpl;

import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.EventSessionDTO;
import org.example.ticketmanagement.vo.EventSessionVO;
import org.example.ticketmanagement.mapper.EventMapper;
import org.example.ticketmanagement.mapper.EventSessionMapper;
import org.example.ticketmanagement.pojo.Event;
import org.example.ticketmanagement.pojo.EventSession;
import org.example.ticketmanagement.service.EventSessionService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EventSessionServiceImpl implements EventSessionService {

    @Autowired
    private EventSessionMapper eventSessionMapper;

    @Autowired
    private EventMapper eventMapper;

    @Override
    @Transactional
    public boolean addSession(EventSessionDTO eventSessionDTO) {
        log.info("新增场次: {}", eventSessionDTO.getSessionName());

        // 1. 校验演出是否存在且启用
        Event event = eventMapper.selectById(eventSessionDTO.getEventId());
        if (event == null) {
            log.warn("演出不存在: {}", eventSessionDTO.getEventId());
            return false;
        }
        if (event.getStatus() != 1) {
            log.warn("演出状态异常，无法添加场次: {}", eventSessionDTO.getEventId());
            return false;
        }

        // 2. 校验场次时间是否在演出时间范围内
        if (!validateSessionTime(eventSessionDTO, event)) {
            log.warn("场次时间不在演出时间范围内");
            return false;
        }

        // 3. 校验同一演出是否存在时间冲突的场次
        int conflictCount = eventSessionMapper.countTimeConflict(
                eventSessionDTO.getEventId(),
                eventSessionDTO.getSessionTime(),
                null  // 新增时没有排除的ID
        );
        if (conflictCount > 0) {
            log.warn("同一演出存在时间冲突的场次");
            return false;
        }

        // 4. DTO 转 Entity（并补充必要字段）
        EventSession session = new EventSession();
        BeanUtils.copyProperties(eventSessionDTO, session);
        session.setCreateTime(LocalDateTime.now());

        // 5. 调用Mapper执行插入
        int affectedRows = eventSessionMapper.insert(session);

        // 6. 根据受影响行数判断操作结果
        boolean success = (affectedRows == 1);
        if (success) {
            log.info("新增场次成功，ID: {}", session.getId());
        } else {
            log.error("新增场次失败，受影响行数: {}", affectedRows);
        }
        return success;
    }

    @Override
    @Transactional
    public boolean deleteSession(Long id) {
        log.info("删除场次，ID: {}", id);

        // 先检查是否存在，使反馈更友好
        EventSession existingSession = eventSessionMapper.selectById(id);
        if (existingSession == null) {
            log.warn("要删除的场次不存在，ID: {}", id);
            return false;
        }

        int affectedRows = eventSessionMapper.deleteById(id);
        boolean success = (affectedRows == 1);
        if (success) {
            log.info("删除场次成功，ID: {}", id);
        } else {
            log.warn("删除场次未生效，ID: {}", id);
        }
        return success;
    }

    @Override
    @Transactional
    public boolean updateSession(Long id, EventSessionDTO eventSessionDTO) {
        log.info("更新场次，ID: {}", id);

        // 1. 检查要更新的目标是否存在
        EventSession existingSession = eventSessionMapper.selectById(id);
        if (existingSession == null) {
            log.warn("要更新的场次不存在，ID: {}", id);
            return false;
        }

        // 2. 校验演出是否存在且启用（如果演出ID有变化）
        if (eventSessionDTO.getEventId() != null &&
                !eventSessionDTO.getEventId().equals(existingSession.getEventId())) {
            Event event = eventMapper.selectById(eventSessionDTO.getEventId());
            if (event == null || event.getStatus() != 1) {
                log.warn("演出不存在或状态异常: {}", eventSessionDTO.getEventId());
                return false;
            }
        }

        // 3. 校验场次时间是否在演出时间范围内（如果时间有变化或演出ID有变化）
        Event event = eventMapper.selectById(
                eventSessionDTO.getEventId() != null ?
                        eventSessionDTO.getEventId() : existingSession.getEventId()
        );
        if (!validateSessionTime(eventSessionDTO, event)) {
            log.warn("场次时间不在演出时间范围内");
            return false;
        }

        // 4. 校验同一演出是否存在时间冲突的场次（排除自身）
        int conflictCount = eventSessionMapper.countTimeConflict(
                eventSessionDTO.getEventId() != null ?
                        eventSessionDTO.getEventId() : existingSession.getEventId(),
                eventSessionDTO.getSessionTime() != null ?
                        eventSessionDTO.getSessionTime() : existingSession.getSessionTime(),
                id  // 更新时排除自身
        );
        if (conflictCount > 0) {
            log.warn("同一演出存在时间冲突的场次");
            return false;
        }

        // 5. DTO 转 Entity，只更新非空字段
        EventSession session = new EventSession();
        // 先复制原有字段
        BeanUtils.copyProperties(existingSession, session);
        // 再覆盖新的字段（只更新非空字段）
        if (eventSessionDTO.getEventId() != null) {
            session.setEventId(eventSessionDTO.getEventId());
        }
        if (eventSessionDTO.getSessionName() != null) {
            session.setSessionName(eventSessionDTO.getSessionName());
        }
        if (eventSessionDTO.getSessionTime() != null) {
            session.setSessionTime(eventSessionDTO.getSessionTime());
        }
        if (eventSessionDTO.getStatus() != null) {
            session.setStatus(eventSessionDTO.getStatus());
        }
        session.setId(id);

        // 6. 执行更新
        int affectedRows = eventSessionMapper.update(session);
        boolean success = (affectedRows == 1);
        if (success) {
            log.info("更新场次成功，ID: {}", id);
        } else {
            log.warn("更新场次未生效，ID: {}", id);
        }
        return success;
    }

    @Override
    public EventSessionVO getSessionById(Long id) {
        log.debug("根据ID查询场次，ID: {}", id);
        EventSession session = eventSessionMapper.selectById(id);
        if (session == null) {
            return null;
        }
        // Entity 转 VO（基础信息）
        return convertToVO(session);
    }

    @Override
    public List<EventSessionVO> getAllSessions() {
        log.debug("查询所有场次列表");
        List<EventSession> sessions = eventSessionMapper.selectAll();
        return sessions.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<EventSessionVO> getSessionsByStatus(Integer status) {
        log.debug("根据状态查询场次，status: {}", status);
        List<EventSession> sessions = eventSessionMapper.selectByStatus(status);
        return sessions.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<EventSessionVO> getSessionsByEventId(Long eventId) {
        log.debug("根据演出ID查询场次，eventId: {}", eventId);
        List<EventSession> sessions = eventSessionMapper.selectByEventId(eventId);
        return sessions.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<EventSessionVO> getSessionsByEventIdAndStatus(Long eventId, Integer status) {
        log.debug("根据演出ID和状态查询场次，eventId: {}, status: {}", eventId, status);
        List<EventSession> sessions = eventSessionMapper.selectByEventIdAndStatus(eventId, status);
        return sessions.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    // 在EventSessionServiceImpl.java中添加
    @Override
    public List<EventSessionVO> getUpcomingSessions(Integer limit) {
        log.debug("获取即将开始的场次，limit: {}", limit);

        // 调用Mapper的selectUpcomingSessions方法
        List<EventSession> sessions = eventSessionMapper.selectUpcomingSessions(limit);

        return sessions.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 校验场次时间是否在演出时间范围内
     * 业务规则：
     * 1. 场次时间必须在演出开始时间和结束时间之间
     * 2. 如果场次时间等于演出开始时间或结束时间，也算有效
     */
    private boolean validateSessionTime(EventSessionDTO sessionDTO, Event event) {
        if (sessionDTO.getSessionTime() == null || event == null) {
            return false;
        }

        LocalDateTime sessionTime = sessionDTO.getSessionTime();
        LocalDateTime eventStartTime = event.getEventStartTime();
        LocalDateTime eventEndTime = event.getEventEndTime();

        // 检查场次时间是否在演出开始时间和结束时间之间（包括边界）
        boolean isValid = (sessionTime.isEqual(eventStartTime) || sessionTime.isAfter(eventStartTime))
                && (sessionTime.isEqual(eventEndTime) || sessionTime.isBefore(eventEndTime));

        if (!isValid) {
            log.warn("场次时间{}不在演出时间范围{} - {}内",
                    sessionTime, eventStartTime, eventEndTime);
        }

        return isValid;
    }

    /**
     * 内部辅助方法：将 Entity 对象转换为 VO 对象（包含关联信息）
     */
    private EventSessionVO convertToVO(EventSession session) {
        EventSessionVO vo = new EventSessionVO();
        BeanUtils.copyProperties(session, vo);

        // 查询关联的演出信息
        Event event = eventMapper.selectById(session.getEventId());
        if (event != null) {
            vo.setEventName(event.getName() != null ? event.getName() : "");
            vo.setArtistName(event.getArtistName() != null ? event.getArtistName() : "");
            vo.setVenue(event.getVenue() != null ? event.getVenue() : "");
        } else {
            // 如果找不到关联的演出，设置默认空字符串
            vo.setEventName("");
            vo.setArtistName("");
            vo.setVenue("");
        }

        return vo;
    }

}