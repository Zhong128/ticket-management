// org/example/ticketmanagement/service/impl/TicketTierServiceImpl.java
package org.example.ticketmanagement.serviceimpl;

import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.TicketTierDTO;
import org.example.ticketmanagement.vo.StockStatisticsVO;
import org.example.ticketmanagement.vo.TicketStockVO;
import org.example.ticketmanagement.vo.TicketTierVO;
import org.example.ticketmanagement.mapper.EventMapper;
import org.example.ticketmanagement.mapper.EventSessionMapper;
import org.example.ticketmanagement.mapper.TicketTierMapper;
import org.example.ticketmanagement.pojo.Event;
import org.example.ticketmanagement.pojo.EventSession;
import org.example.ticketmanagement.pojo.TicketTier;
import org.example.ticketmanagement.service.TicketTierService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TicketTierServiceImpl implements TicketTierService {

    @Autowired
    private TicketTierMapper ticketTierMapper;

    @Autowired
    private EventMapper eventMapper;

    @Autowired
    private EventSessionMapper eventSessionMapper;

    @Override
    @Transactional
    public boolean addTicketTier(TicketTierDTO ticketTierDTO) {
        log.info("新增票档: {}", ticketTierDTO.getTierName());

        // 1. 校验演出是否存在且启用
        Event event = eventMapper.selectById(ticketTierDTO.getEventId());
        if (event == null) {
            log.warn("演出不存在: {}", ticketTierDTO.getEventId());
            return false;
        }
        if (event.getStatus() != 1) {
            log.warn("演出状态异常，无法添加票档: {}", ticketTierDTO.getEventId());
            return false;
        }

        // 2. 校验场次是否存在且启用，并且属于该演出
        EventSession session = eventSessionMapper.selectById(ticketTierDTO.getSessionId());
        if (session == null) {
            log.warn("场次不存在: {}", ticketTierDTO.getSessionId());
            return false;
        }
        if (session.getStatus() != 1) {
            log.warn("场次状态异常，无法添加票档: {}", ticketTierDTO.getSessionId());
            return false;
        }
        if (!session.getEventId().equals(ticketTierDTO.getEventId())) {
            log.warn("场次不属于该演出: session.eventId={}, param.eventId={}",
                    session.getEventId(), ticketTierDTO.getEventId());
            return false;
        }

        // 3. 校验同一场次是否存在相同名称的票档
        int conflictCount = ticketTierMapper.countByNameConflict(
                ticketTierDTO.getSessionId(),
                ticketTierDTO.getTierName(),
                null  // 新增时没有排除的ID
        );
        if (conflictCount > 0) {
            log.warn("同一场次存在相同名称的票档: {}", ticketTierDTO.getTierName());
            return false;
        }

        // 4. 校验价格逻辑
        if (!validatePriceLogic(ticketTierDTO)) {
            log.warn("票档价格逻辑校验失败");
            return false;
        }

        // 5. 校验库存逻辑
        if (!validateStockLogic(ticketTierDTO)) {
            log.warn("票档库存逻辑校验失败");
            return false;
        }

        // 6. DTO 转 Entity（并补充必要字段）
        TicketTier tier = new TicketTier();
        BeanUtils.copyProperties(ticketTierDTO, tier);
        tier.setCreateTime(LocalDateTime.now());
        tier.setUpdateTime(LocalDateTime.now());

        // 7. 调用Mapper执行插入
        int affectedRows = ticketTierMapper.insert(tier);

        // 8. 根据受影响行数判断操作结果
        boolean success = (affectedRows == 1);
        if (success) {
            log.info("新增票档成功，ID: {}", tier.getId());
        } else {
            log.error("新增票档失败，受影响行数: {}", affectedRows);
        }
        return success;
    }

    @Override
    @Transactional
    public boolean deleteTicketTier(Long id) {
        log.info("删除票档，ID: {}", id);

        // 先检查是否存在，使反馈更友好
        TicketTier existingTier = ticketTierMapper.selectById(id);
        if (existingTier == null) {
            log.warn("要删除的票档不存在，ID: {}", id);
            return false;
        }

        int affectedRows = ticketTierMapper.deleteById(id);
        boolean success = (affectedRows == 1);
        if (success) {
            log.info("删除票档成功，ID: {}", id);
        } else {
            log.warn("删除票档未生效，ID: {}", id);
        }
        return success;
    }

    @Override
    @Transactional
    public boolean updateTicketTier(Long id, TicketTierDTO ticketTierDTO) {
        log.info("更新票档，ID: {}", id);

        // 1. 检查要更新的目标是否存在
        TicketTier existingTier = ticketTierMapper.selectById(id);
        if (existingTier == null) {
            log.warn("要更新的票档不存在，ID: {}", id);
            return false;
        }

        // 2. 如果演出ID有变化，校验演出是否存在且启用
        if (ticketTierDTO.getEventId() != null &&
                !ticketTierDTO.getEventId().equals(existingTier.getEventId())) {
            Event event = eventMapper.selectById(ticketTierDTO.getEventId());
            if (event == null || event.getStatus() != 1) {
                log.warn("演出不存在或状态异常: {}", ticketTierDTO.getEventId());
                return false;
            }
        }

        // 3. 如果场次ID有变化，校验场次是否存在且启用，并且属于该演出
        Long targetEventId = ticketTierDTO.getEventId() != null ?
                ticketTierDTO.getEventId() : existingTier.getEventId();
        if (ticketTierDTO.getSessionId() != null &&
                !ticketTierDTO.getSessionId().equals(existingTier.getSessionId())) {
            EventSession session = eventSessionMapper.selectById(ticketTierDTO.getSessionId());
            if (session == null || session.getStatus() != 1) {
                log.warn("场次不存在或状态异常: {}", ticketTierDTO.getSessionId());
                return false;
            }
            if (!session.getEventId().equals(targetEventId)) {
                log.warn("场次不属于该演出: session.eventId={}, param.eventId={}",
                        session.getEventId(), targetEventId);
                return false;
            }
        }

        // 4. 如果票档名称有变化，校验同一场次是否存在相同名称的票档（排除自身）
        if (ticketTierDTO.getTierName() != null &&
                !ticketTierDTO.getTierName().equals(existingTier.getTierName())) {
            Long targetSessionId = ticketTierDTO.getSessionId() != null ?
                    ticketTierDTO.getSessionId() : existingTier.getSessionId();
            int conflictCount = ticketTierMapper.countByNameConflict(
                    targetSessionId,
                    ticketTierDTO.getTierName(),
                    id  // 更新时排除自身
            );
            if (conflictCount > 0) {
                log.warn("同一场次存在相同名称的票档: {}", ticketTierDTO.getTierName());
                return false;
            }
        }

        // 5. 校验价格逻辑
        if (!validatePriceLogic(ticketTierDTO, existingTier)) {
            log.warn("票档价格逻辑校验失败");
            return false;
        }

        // 6. 校验库存逻辑
        if (!validateStockLogic(ticketTierDTO, existingTier)) {
            log.warn("票档库存逻辑校验失败");
            return false;
        }

        // 7. DTO 转 Entity，只更新非空字段
        TicketTier tier = new TicketTier();
        // 先复制原有字段
        BeanUtils.copyProperties(existingTier, tier);
        // 再覆盖新的字段（只更新非空字段）
        if (ticketTierDTO.getEventId() != null) {
            tier.setEventId(ticketTierDTO.getEventId());
        }
        if (ticketTierDTO.getSessionId() != null) {
            tier.setSessionId(ticketTierDTO.getSessionId());
        }
        if (ticketTierDTO.getTierName() != null) {
            tier.setTierName(ticketTierDTO.getTierName());
        }
        if (ticketTierDTO.getOriginalPrice() != null) {
            tier.setOriginalPrice(ticketTierDTO.getOriginalPrice());
        }
        if (ticketTierDTO.getCurrentPrice() != null) {
            tier.setCurrentPrice(ticketTierDTO.getCurrentPrice());
        }
        if (ticketTierDTO.getTotalStock() != null) {
            tier.setTotalStock(ticketTierDTO.getTotalStock());
        }
        if (ticketTierDTO.getAvailableStock() != null) {
            tier.setAvailableStock(ticketTierDTO.getAvailableStock());
        }
        if (ticketTierDTO.getStatus() != null) {
            tier.setStatus(ticketTierDTO.getStatus());
        }
        tier.setId(id);
        tier.setUpdateTime(LocalDateTime.now());

        // 8. 执行更新
        int affectedRows = ticketTierMapper.update(tier);
        boolean success = (affectedRows == 1);
        if (success) {
            log.info("更新票档成功，ID: {}", id);
        } else {
            log.warn("更新票档未生效，ID: {}", id);
        }
        return success;
    }

    @Override
    public TicketTierVO getTicketTierById(Long id) {
        log.debug("根据ID查询票档，ID: {}", id);
        TicketTier tier = ticketTierMapper.selectById(id);
        if (tier == null) {
            return null;
        }
        // Entity 转 VO（包含关联信息）
        return convertToDetailVO(tier);
    }

    @Override
    public List<TicketTierVO> getAllTicketTiers() {
        log.debug("查询所有票档列表");
        List<TicketTier> tiers = ticketTierMapper.selectAll();
        return tiers.stream()
                .map(this::convertToDetailVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<TicketTierVO> getTicketTiersByStatus(Integer status) {
        log.debug("根据状态查询票档，status: {}", status);
        List<TicketTier> tiers = ticketTierMapper.selectByStatus(status);
        return tiers.stream()
                .map(this::convertToDetailVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<TicketTierVO> getTicketTiersByEventId(Long eventId) {
        log.debug("根据演出ID查询票档，eventId: {}", eventId);
        List<TicketTier> tiers = ticketTierMapper.selectByEventId(eventId);
        return tiers.stream()
                .map(this::convertToDetailVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<TicketTierVO> getTicketTiersBySessionId(Long sessionId) {
        log.debug("根据场次ID查询票档，sessionId: {}", sessionId);
        List<TicketTier> tiers = ticketTierMapper.selectBySessionId(sessionId);
        return tiers.stream()
                .map(this::convertToDetailVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<TicketTierVO> getTicketTiersByEventIdAndStatus(Long eventId, Integer status) {
        log.debug("根据演出ID和状态查询票档，eventId: {}, status: {}", eventId, status);
        List<TicketTier> tiers = ticketTierMapper.selectByEventIdAndStatus(eventId, status);
        return tiers.stream()
                .map(this::convertToDetailVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<TicketTierVO> getTicketTiersBySessionIdAndStatus(Long sessionId, Integer status) {
        log.debug("根据场次ID和状态查询票档，sessionId: {}, status: {}", sessionId, status);
        List<TicketTier> tiers = ticketTierMapper.selectBySessionIdAndStatus(sessionId, status);
        return tiers.stream()
                .map(this::convertToDetailVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<TicketTierVO> getTicketTiersByPriceRange(BigDecimal minPrice, BigDecimal maxPrice, Integer status) {
        log.debug("根据价格范围查询票档，minPrice: {}, maxPrice: {}, status: {}", minPrice, maxPrice, status);
        List<TicketTier> tiers = ticketTierMapper.selectByPriceRange(minPrice, maxPrice, status);
        return tiers.stream()
                .map(this::convertToDetailVO)
                .collect(Collectors.toList());
    }

    @Override
    public TicketStockVO getStockInfoBySessionId(Long sessionId) {
        log.debug("获取场次票档库存信息，sessionId: {}", sessionId);

        // 1. 检查场次是否存在
        EventSession session = eventSessionMapper.selectById(sessionId);
        if (session == null) {
            log.warn("场次不存在: {}", sessionId);
            return null;
        }

        // 2. 查询各票档库存信息
        List<Map<String, Object>> tierStockList = ticketTierMapper.selectStockInfoBySessionId(sessionId);

        // 3. 判断是否有库存（任一票档有库存即为有库存）
        boolean hasStock = tierStockList.stream()
                .anyMatch(tier -> {
                    Object hasStockObj = tier.get("has_stock");
                    if (hasStockObj instanceof Long) {
                        return ((Long) hasStockObj) > 0;
                    }
                    return false;
                });

        // 4. 判断是否已开票
        boolean onSale = isSessionOnSale(sessionId);

        // 5. 构建返回对象
        TicketStockVO stockVO = TicketStockVO.create(session.getEventId(), sessionId, hasStock, onSale);
        stockVO.setTierStockList(tierStockList);

        return stockVO;
    }

    @Override
    public TicketStockVO getStockInfoByEventId(Long eventId) {
        log.debug("获取演出票档库存信息，eventId: {}", eventId);

        // 1. 检查演出是否存在
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            log.warn("演出不存在: {}", eventId);
            return null;
        }

        // 2. 查询各票档库存信息
        List<Map<String, Object>> tierStockList = ticketTierMapper.selectStockInfoByEventId(eventId);

        // 3. 判断是否有库存
        boolean hasStock = tierStockList.stream()
                .anyMatch(tier -> {
                    Object hasStockObj = tier.get("has_stock");
                    if (hasStockObj instanceof Long) {
                        return ((Long) hasStockObj) > 0;
                    }
                    return false;
                });

        // 4. 判断是否有票档已开票
        boolean onSale = isEventOnSale(eventId);

        // 5. 构建返回对象
        TicketStockVO stockVO = TicketStockVO.create(eventId, null, hasStock, onSale);
        stockVO.setTierStockList(tierStockList);

        return stockVO;
    }

    @Override
    public boolean isSessionOnSale(Long sessionId) {
        log.debug("检查场次是否已开票，sessionId: {}", sessionId);

        // 1. 检查场次是否存在
        EventSession session = eventSessionMapper.selectById(sessionId);
        if (session == null) {
            log.warn("场次不存在: {}", sessionId);
            return false;
        }

        // 2. 使用Mapper查询是否有票档已开票
        Boolean result = ticketTierMapper.hasTicketsOnSaleBySessionId(sessionId);
        return result != null && result;
    }

    @Override
    public boolean isEventOnSale(Long eventId) {
        log.debug("检查演出是否有票档已开票，eventId: {}", eventId);

        // 1. 检查演出是否存在
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            log.warn("演出不存在: {}", eventId);
            return false;
        }

        // 2. 使用Mapper查询是否有票档已开票
        Boolean result = ticketTierMapper.hasTicketsOnSaleByEventId(eventId);
        return result != null && result;
    }


    @Override
    public StockStatisticsVO getStockStatistics(Long eventId, Long sessionId, Long tierId) {
        log.debug("获取库存统计，eventId: {}, sessionId: {}, tierId: {}", eventId, sessionId, tierId);

        // 1. 使用动态SQL查询票档
        List<TicketTier> tiers = ticketTierMapper.selectByCondition(eventId, sessionId, tierId);

        // 2. 计算统计信息
        StockStatisticsVO statistics = new StockStatisticsVO();
        statistics.setEventId(eventId);
        statistics.setSessionId(sessionId);
        statistics.setTierId(tierId);

        if (tiers.isEmpty()) {
            statistics.setStatisticsTime(LocalDateTime.now().toString());
            statistics.setTierDetails(new ArrayList<>());
            return statistics;
        }

        // 3. 计算总体统计
        int totalStock = 0;
        int totalAvailable = 0;
        int activeTiers = 0;

        List<StockStatisticsVO.TierStockDetail> details = new ArrayList<>();

        for (TicketTier tier : tiers) {
            // 统计启用状态的票档
            if (tier.getStatus() == 1) {
                activeTiers++;
            }

            int soldStock = tier.getTotalStock() - tier.getAvailableStock();
            totalStock += tier.getTotalStock();
            totalAvailable += tier.getAvailableStock();

            // 计算销售率
            BigDecimal sellRate = BigDecimal.ZERO;
            if (tier.getTotalStock() > 0) {
                sellRate = new BigDecimal(soldStock)
                        .divide(new BigDecimal(tier.getTotalStock()), 4, java.math.RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }

            // 添加到详情列表
            StockStatisticsVO.TierStockDetail detail = new StockStatisticsVO.TierStockDetail();
            detail.setTierId(tier.getId());
            detail.setTierName(tier.getTierName());
            detail.setTotalStock(tier.getTotalStock());
            detail.setSoldStock(soldStock);
            detail.setAvailableStock(tier.getAvailableStock());
            detail.setSellRate(sellRate);
            detail.setPrice(tier.getCurrentPrice());
            detail.setStatus(tier.getStatus());
            detail.setLastUpdateTime(tier.getUpdateTime() != null ?
                    tier.getUpdateTime().toString() : "N/A");
            details.add(detail);
        }

        // 4. 设置统计结果
        statistics.setTotalTiers(tiers.size());
        statistics.setActiveTiers(activeTiers);
        statistics.setTotalStock(totalStock);
        statistics.setTotalAvailable(totalAvailable);
        statistics.setTotalSold(totalStock - totalAvailable);

        // 计算总体销售率
        if (totalStock > 0) {
            BigDecimal overallSellRate = new BigDecimal(totalStock - totalAvailable)
                    .divide(new BigDecimal(totalStock), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            statistics.setOverallSellRate(overallSellRate);
        } else {
            statistics.setOverallSellRate(BigDecimal.ZERO);
        }

        statistics.setTierDetails(details);
        statistics.setStatisticsTime(LocalDateTime.now().toString());

        return statistics;
    }

    /**
     * 校验票档价格逻辑（新增时）
     * 规则：
     * 1. 原价和现价都不能为null
     * 2. 现价不能高于原价（不能有溢价）
     * 3. 价格必须大于等于0
     */
    private boolean validatePriceLogic(TicketTierDTO dto) {
        if (dto.getOriginalPrice() == null || dto.getCurrentPrice() == null) {
            return false;
        }

        // 价格必须大于等于0
        if (dto.getOriginalPrice().compareTo(BigDecimal.ZERO) < 0 ||
                dto.getCurrentPrice().compareTo(BigDecimal.ZERO) < 0) {
            log.warn("价格不能小于0: originalPrice={}, currentPrice={}",
                    dto.getOriginalPrice(), dto.getCurrentPrice());
            return false;
        }

        // 现价不能高于原价
        if (dto.getCurrentPrice().compareTo(dto.getOriginalPrice()) > 0) {
            log.warn("现价不能高于原价: originalPrice={}, currentPrice={}",
                    dto.getOriginalPrice(), dto.getCurrentPrice());
            return false;
        }

        return true;
    }

    /**
     * 校验票档价格逻辑（更新时，只校验非空字段）
     */
    private boolean validatePriceLogic(TicketTierDTO dto, TicketTier existingTier) {
        BigDecimal originalPrice = dto.getOriginalPrice() != null ?
                dto.getOriginalPrice() : existingTier.getOriginalPrice();
        BigDecimal currentPrice = dto.getCurrentPrice() != null ?
                dto.getCurrentPrice() : existingTier.getCurrentPrice();

        // 价格必须大于等于0
        if (originalPrice.compareTo(BigDecimal.ZERO) < 0 ||
                currentPrice.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("价格不能小于0: originalPrice={}, currentPrice={}",
                    originalPrice, currentPrice);
            return false;
        }

        // 现价不能高于原价
        if (currentPrice.compareTo(originalPrice) > 0) {
            log.warn("现价不能高于原价: originalPrice={}, currentPrice={}",
                    originalPrice, currentPrice);
            return false;
        }

        return true;
    }

    /**
     * 校验票档库存逻辑（新增时）
     * 规则：
     * 1. 总库存和可用库存都不能为null
     * 2. 可用库存不能大于总库存
     * 3. 库存必须大于等于0
     */
    private boolean validateStockLogic(TicketTierDTO dto) {
        if (dto.getTotalStock() == null || dto.getAvailableStock() == null) {
            return false;
        }

        // 库存必须大于等于0
        if (dto.getTotalStock() < 0 || dto.getAvailableStock() < 0) {
            log.warn("库存不能小于0: totalStock={}, availableStock={}",
                    dto.getTotalStock(), dto.getAvailableStock());
            return false;
        }

        // 可用库存不能大于总库存
        if (dto.getAvailableStock() > dto.getTotalStock()) {
            log.warn("可用库存不能大于总库存: totalStock={}, availableStock={}",
                    dto.getTotalStock(), dto.getAvailableStock());
            return false;
        }

        return true;
    }

    /**
     * 校验票档库存逻辑（更新时，只校验非空字段）
     */
    private boolean validateStockLogic(TicketTierDTO dto, TicketTier existingTier) {
        Integer totalStock = dto.getTotalStock() != null ?
                dto.getTotalStock() : existingTier.getTotalStock();
        Integer availableStock = dto.getAvailableStock() != null ?
                dto.getAvailableStock() : existingTier.getAvailableStock();

        // 库存必须大于等于0
        if (totalStock < 0 || availableStock < 0) {
            log.warn("库存不能小于0: totalStock={}, availableStock={}",
                    totalStock, availableStock);
            return false;
        }

        // 可用库存不能大于总库存
        if (availableStock > totalStock) {
            log.warn("可用库存不能大于总库存: totalStock={}, availableStock={}",
                    totalStock, availableStock);
            return false;
        }

        return true;
    }


    /**
     * 内部辅助方法：获取票档详情（包含关联信息）
     * 可选方法，用于需要关联信息的场景
     */
    private TicketTierVO convertToDetailVO(TicketTier tier) {
        TicketTierVO vo = new TicketTierVO();
        BeanUtils.copyProperties(tier, vo);

        // 查询关联的演出信息
        Event event = eventMapper.selectById(tier.getEventId());
        if (event != null) {
            vo.setEventName(event.getName());
        }

        // 查询关联的场次信息
        EventSession session = eventSessionMapper.selectById(tier.getSessionId());
        if (session != null) {
            vo.setSessionName(session.getSessionName());
            vo.setSessionTime(session.getSessionTime());
        }

        return vo;
    }
}