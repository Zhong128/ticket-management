// org/example/ticketmanagement/service/TicketTierService.java
package org.example.ticketmanagement.service;

import org.example.ticketmanagement.dto.TicketTierDTO;
import org.example.ticketmanagement.dto.TicketTierQueryDTO;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.vo.StockStatisticsVO;
import org.example.ticketmanagement.vo.TicketStockVO;
import org.example.ticketmanagement.vo.TicketTierVO;

import java.util.List;

public interface TicketTierService {

    /**
     * 新增票档
     * @param ticketTierDTO 票档数据
     * @return 新增成功返回 true，失败返回 false
     */
    boolean addTicketTier(TicketTierDTO ticketTierDTO);

    /**
     * 根据ID删除票档
     * @param id 票档ID
     * @return 删除成功返回 true，失败返回 false
     */
    boolean deleteTicketTier(Long id);

    /**
     * 更新票档信息
     * @param id 要更新的票档ID
     * @param ticketTierDTO 新的票档数据
     * @return 更新成功返回 true，失败返回 false
     */
    boolean updateTicketTier(Long id, TicketTierDTO ticketTierDTO);

    /**
     * 根据ID查询票档详情
     * @param id 票档ID
     * @return 票档详情视图对象，未找到返回 null
     */
    TicketTierVO getTicketTierById(Long id);

    /**
     * 查询所有票档列表
     * @return 票档视图对象列表
     */
    List<TicketTierVO> getAllTicketTiers();

    /**
     * 获取票档库存信息（用户端）
     * @param sessionId 场次ID
     * @return 库存信息视图对象
     */
    TicketStockVO getStockInfoBySessionId(Long sessionId);

    /**
     * 获取票档库存信息（用户端）- 根据演出ID
     * @param eventId 演出ID
     * @return 库存信息视图对象
     */
    TicketStockVO getStockInfoByEventId(Long eventId);

    /**
     * 检查场次是否已开票
     * @param sessionId 场次ID
     * @return 是否已开票
     */
    boolean isSessionOnSale(Long sessionId);

    /**
     * 检查演出是否有任何票档已开票
     * @param eventId 演出ID
     * @return 是否已开票
     */
    boolean isEventOnSale(Long eventId);

    /**
     * 获取库存统计信息（管理端）
     * @param eventId 演出ID（可选）
     * @param sessionId 场次ID（可选）
     * @param tierId 票档ID（可选）
     * @return 库存统计视图对象
     */
    StockStatisticsVO getStockStatistics(Long eventId, Long sessionId, Long tierId);

    /**
     * 分页查询票档（动态条件）
     */
    PageResult<TicketTierVO> queryTicketTiers(TicketTierQueryDTO queryDTO);
}