package org.example.ticketmanagement.serviceimpl;

import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.TicketTierDTO;
import org.example.ticketmanagement.dto.TicketTierQueryDTO;
import org.example.ticketmanagement.mapper.EventMapper;
import org.example.ticketmanagement.mapper.EventSessionMapper;
import org.example.ticketmanagement.mapper.TicketTierMapper;
import org.example.ticketmanagement.mapper.TradeStockMapper;
import org.example.ticketmanagement.pojo.Event;
import org.example.ticketmanagement.pojo.EventSession;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.pojo.TicketTier;
import org.example.ticketmanagement.service.TicketTierService;
import org.example.ticketmanagement.vo.StockStatisticsVO;
import org.example.ticketmanagement.vo.TicketStockVO;
import org.example.ticketmanagement.vo.TicketTierVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Autowired
    private TradeStockMapper stockMapper;

    @Value("${trade.enabled:false}")
    private boolean tradeEnabled;

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
            log.warn("场次不属于该演出: session.eventId={}, param.eventId={}", session.getEventId(),
                    ticketTierDTO.getEventId());
            return false;
        }
        // 3. 校验同一场次是否存在相同名称的票档
        int conflictCount = // 新增时没有排除的ID
                ticketTierMapper.// 新增时没有排除的ID
                        countByNameConflict(// 新增时没有排除的ID
                                ticketTierDTO.getSessionId(), // 新增时没有排除的ID
                                ticketTierDTO.getTierName(), null);
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
        TicketTier existingTier = ticketTierMapper.selectByIdForUpdate(id);
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
        if (ticketTierDTO.getTotalStock() != null || ticketTierDTO.getAvailableStock() != null) {
            throw new IllegalArgumentException("普通票档编辑不可修改库存，请使用专用库存调整接口");
        }
        // 1. 检查要更新的目标是否存在
        TicketTier existingTier = ticketTierMapper.selectByIdForUpdate(id);
        if (existingTier == null) {
            log.warn("要更新的票档不存在，ID: {}", id);
            return false;
        }
        boolean identityChanged = (ticketTierDTO.getEventId() != null
                && !ticketTierDTO.getEventId().equals(existingTier.getEventId()))
                || (ticketTierDTO.getSessionId() != null
                        && !ticketTierDTO.getSessionId().equals(existingTier.getSessionId()));
        if (identityChanged) {
            if (!tradeEnabled) {
                throw new IllegalStateException("交易迁移完成前不能变更票档所属演出或场次");
            }
            if (stockMapper.countActiveReservations(id) > 0 || stockMapper.countActiveOrders(id) > 0) {
                throw new IllegalStateException("票档存在有效申请或订单，不能变更所属演出或场次");
            }
        }
        // 2. 如果演出ID有变化，校验演出是否存在且启用
        if (ticketTierDTO.getEventId() != null && !ticketTierDTO.getEventId().equals(existingTier.getEventId())) {
            Event event = eventMapper.selectById(ticketTierDTO.getEventId());
            if (event == null || event.getStatus() != 1) {
                log.warn("演出不存在或状态异常: {}", ticketTierDTO.getEventId());
                return false;
            }
        }
        // 3. 如果场次ID有变化，校验场次是否存在且启用，并且属于该演出
        Long targetEventId = ticketTierDTO.getEventId() != null ? ticketTierDTO.getEventId()
                : existingTier.getEventId();
        if (ticketTierDTO.getSessionId() != null && !ticketTierDTO.getSessionId().equals(existingTier.getSessionId())) {
            EventSession session = eventSessionMapper.selectById(ticketTierDTO.getSessionId());
            if (session == null || session.getStatus() != 1) {
                log.warn("场次不存在或状态异常: {}", ticketTierDTO.getSessionId());
                return false;
            }
            if (!session.getEventId().equals(targetEventId)) {
                log.warn("场次不属于该演出: session.eventId={}, param.eventId={}", session.getEventId(), targetEventId);
                return false;
            }
        }
        // 4. 如果票档名称有变化，校验同一场次是否存在相同名称的票档（排除自身）
        if (ticketTierDTO.getTierName() != null && !ticketTierDTO.getTierName().equals(existingTier.getTierName())) {
            Long targetSessionId = ticketTierDTO.getSessionId() != null ? ticketTierDTO.getSessionId()
                    : existingTier.getSessionId();
            int conflictCount = // 更新时排除自身
                    ticketTierMapper.// 更新时排除自身
                            countByNameConflict(// 更新时排除自身
                                    targetSessionId, // 更新时排除自身
                                    ticketTierDTO.getTierName(), id);
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
        return tiers.stream().map(this::convertToDetailVO).collect(Collectors.toList());
    }

    @Override
    public TicketStockVO getStockInfoBySessionId(Long sessionId) {
        log.debug("获取场次票档库存信息，sessionId: {}", sessionId);
        // 1. 检查场次是否存在
        EventSession session = eventSessionMapper.selectById(sessionId);
        if (session == null) {
            log.warn("场次不存在：{}", sessionId);
            return null;
        }
        // 2. 判断是否已开票（根据当前时间是否在销售时间内）
        boolean onSale = isSessionOnSale(sessionId);
        // 【关键修改】如果未开售，直接返回，不查询库存
        if (!onSale) {
            log.debug("场次尚未开售，sessionId: {}", sessionId);
            return TicketStockVO.notOnSale(session.getEventId(), sessionId);
        }
        // 3. 查询各票档库存信息
        List<Map<String, Object>> tierStockList = ticketTierMapper.selectStockInfoBySessionId(sessionId);
        // 4. 转换为新的 VO 结构
        List<TicketStockVO.TierStockInfo> stockInfos = tierStockList.stream().map(tier -> {
            Long tierId = ((Number) tier.get("id")).longValue();
            String tierName = (String) tier.get("tier_name");
            Object hasStockObj = tier.get("has_stock");
            boolean hasStock = hasStockObj instanceof Number && ((Number) hasStockObj).intValue() > 0;
            return new TicketStockVO.TierStockInfo(tierId, tierName, hasStock);
        }).collect(Collectors.toList());
        // 5. 判断是否有库存（任一票档有库存即为有库存）
        boolean anyStock = stockInfos.stream().anyMatch(TicketStockVO.TierStockInfo::isHasStock);
        // 6. 根据库存情况返回结果
        if (anyStock) {
            return TicketStockVO.onSaleWithStock(session.getEventId(), sessionId, stockInfos);
        } else {
            return TicketStockVO.onSaleSoldOut(session.getEventId(), sessionId);
        }
    }

    @Override
    public TicketStockVO getStockInfoByEventId(Long eventId) {
        log.debug("获取演出票档库存信息，eventId: {}", eventId);
        // 1. 检查演出是否存在
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            log.warn("演出不存在：{}", eventId);
            return null;
        }
        // 2. 判断是否已开票
        boolean onSale = isEventOnSale(eventId);
        // 如果未开售，直接返回
        if (!onSale) {
            log.debug("演出尚未开售，eventId: {}", eventId);
            return TicketStockVO.notOnSale(eventId, null);
        }
        // 3. 查询并转换为票档库存信息
        List<TicketStockVO.TierStockInfo> tierStockList = ticketTierMapper.selectStockInfoByEventId(eventId).stream()
                .map(this::convertToTierStockInfo).collect(Collectors.toList());
        // 4. 根据库存情况返回结果
        boolean hasStock = tierStockList.stream().anyMatch(TicketStockVO.TierStockInfo::isHasStock);
        return hasStock ? TicketStockVO.onSaleWithStock(eventId, null, tierStockList)
                : TicketStockVO.onSaleSoldOut(eventId, null);
    }

    /**
     * 将 Map 结果转换为 TierStockInfo 对象
     */
    private TicketStockVO.TierStockInfo convertToTierStockInfo(Map<String, Object> tier) {
        Long tierId = ((Number) tier.get("id")).longValue();
        String tierName = (String) tier.get("tier_name");
        boolean hasStock = Optional.ofNullable(tier.get("has_stock"))
                .map(obj -> obj instanceof Number && ((Number) obj).intValue() > 0).orElse(false);
        return new TicketStockVO.TierStockInfo(tierId, tierName, hasStock);
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
        // 1. 构建查询条件 DTO
        TicketTierQueryDTO queryDTO = new TicketTierQueryDTO();
        queryDTO.setEventId(eventId);
        queryDTO.setSessionId(sessionId);
        // 如果指定了 tierId，则精确查询；否则查询所有
        if (tierId != null) {
            queryDTO.setTierId(tierId);
        }
        // 2. 调用新的分页查询方法（使用 DTO）
        List<TicketTier> tiers = ticketTierMapper.selectByCondition(queryDTO);
        // 3. 如果是空列表，直接返回空统计
        if (tiers.isEmpty()) {
            return createEmptyStatistics(eventId, sessionId, tierId);
        }
        // 4. 计算总体统计
        int totalTiers = tiers.size();
        int activeTiers = (int) tiers.stream().filter(t -> t.getStatus() == 1).count();
        int totalStock = tiers.stream().mapToInt(TicketTier::getTotalStock).sum();
        int totalAvailable = tiers.stream().mapToInt(TicketTier::getAvailableStock).sum();
        int totalSold = totalStock - totalAvailable;
        BigDecimal overallSellRate = calculateSellRate(totalSold, totalStock);
        // 5. 转换为票档详情列表
        List<StockStatisticsVO.TierStockDetail> tierDetails = tiers.stream().map(this::convertToTierStockDetail)
                .collect(Collectors.toList());
        // 6. 构建并返回统计对象
        StockStatisticsVO statistics = new StockStatisticsVO();
        statistics.setEventId(eventId);
        statistics.setSessionId(sessionId);
        statistics.setTierId(tierId);
        statistics.setTotalTiers(totalTiers);
        statistics.setActiveTiers(activeTiers);
        statistics.setTotalStock(totalStock);
        statistics.setTotalSold(totalSold);
        statistics.setTotalAvailable(totalAvailable);
        statistics.setOverallSellRate(overallSellRate);
        statistics.setTierDetails(tierDetails);
        statistics.setStatisticsTime(LocalDateTime.now().toString());
        return statistics;
    }

    /**
     * 创建空的统计信息
     */
    private StockStatisticsVO createEmptyStatistics(Long eventId, Long sessionId, Long tierId) {
        StockStatisticsVO statistics = new StockStatisticsVO();
        statistics.setEventId(eventId);
        statistics.setSessionId(sessionId);
        statistics.setTierId(tierId);
        statistics.setTotalTiers(0);
        statistics.setActiveTiers(0);
        statistics.setTotalStock(0);
        statistics.setTotalAvailable(0);
        statistics.setTotalSold(0);
        statistics.setOverallSellRate(BigDecimal.ZERO);
        statistics.setTierDetails(new ArrayList<>());
        statistics.setStatisticsTime(LocalDateTime.now().toString());
        return statistics;
    }

    /**
     * 将 TicketTier 转换为 TierStockDetail
     */
    private StockStatisticsVO.TierStockDetail convertToTierStockDetail(TicketTier tier) {
        int soldStock = tier.getTotalStock() - tier.getAvailableStock();
        BigDecimal sellRate = calculateSellRate(soldStock, tier.getTotalStock());
        StockStatisticsVO.TierStockDetail detail = new StockStatisticsVO.TierStockDetail();
        detail.setTierId(tier.getId());
        detail.setTierName(tier.getTierName());
        detail.setTotalStock(tier.getTotalStock());
        detail.setSoldStock(soldStock);
        detail.setAvailableStock(tier.getAvailableStock());
        detail.setSellRate(sellRate);
        detail.setPrice(tier.getCurrentPrice());
        detail.setStatus(tier.getStatus());
        detail.setLastUpdateTime(tier.getUpdateTime() != null ? tier.getUpdateTime().toString() : "N/A");
        return detail;
    }

    /**
     * 计算销售率（百分比）
     */
    private BigDecimal calculateSellRate(int sold, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(sold).divide(new BigDecimal(total), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    @Override
    public PageResult<TicketTierVO> queryTicketTiers(TicketTierQueryDTO queryDTO) {
        log.debug("分页查询票档，参数：{}", queryDTO);
        // 1. 参数校验
        if (!queryDTO.validate()) {
            log.warn("分页参数无效：page={}, size={}", queryDTO.getPage(), queryDTO.getSize());
            queryDTO.setPage(1);
            queryDTO.setSize(10);
        }
        // 2.如果未指定 status，默认为 1
        if (queryDTO.getStatus() == null) {
            queryDTO.setStatus(1);
            log.debug("未指定 status 参数，默认查询启用状态 (status=1)");
        }
        // 3. 查询总记录数
        Long total = ticketTierMapper.countByCondition(queryDTO);
        log.debug("查询到符合条件的票档总数：{}", total);
        // 4. 如果没有数据，直接返回空结果
        if (total == 0) {
            log.debug("未找到符合条件的票档");
            return PageResult.empty(queryDTO);
        }
        // 5. 计算有效页码
        int maxPage = (int) Math.ceil((double) total / queryDTO.getSize());
        if (queryDTO.getPage() > maxPage) {
            log.debug("请求页码 {} 超出范围，调整为最大页码 {}", queryDTO.getPage(), maxPage);
            queryDTO.setPage(maxPage);
        }
        // 6. 分页查询数据
        List<TicketTier> tiers = ticketTierMapper.selectByCondition(queryDTO);
        log.debug("查询到当前页票档数量：{}", tiers.size());
        // 7. 转换为 VO
        List<TicketTierVO> voList = tiers.stream().map(this::convertToDetailVO).collect(Collectors.toList());
        // 8. 返回分页结果
        return PageResult.success(voList, total, queryDTO);
    }

    /**
     * 内部辅助方法：获取票档详情（包含关联信息） 可选方法，用于需要关联信息的场景
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

    /**
     * 校验票档价格逻辑（新增时） 规则： 1. 原价和现价都不能为 null 2. 现价不能高于原价（不能有溢价） 3. 价格必须大于等于 0
     */
    private boolean validatePriceLogic(TicketTierDTO dto) {
        if (dto.getOriginalPrice() == null || dto.getCurrentPrice() == null) {
            log.warn("原价和现价不能为空：originalPrice={}, currentPrice={}", dto.getOriginalPrice(), dto.getCurrentPrice());
            return false;
        }
        // 价格必须大于等于 0
        if (dto.getOriginalPrice().compareTo(BigDecimal.ZERO) < 0
                || dto.getCurrentPrice().compareTo(BigDecimal.ZERO) < 0) {
            log.warn("价格不能小于 0: originalPrice={}, currentPrice={}", dto.getOriginalPrice(), dto.getCurrentPrice());
            return false;
        }
        // 现价不能高于原价
        if (dto.getCurrentPrice().compareTo(dto.getOriginalPrice()) > 0) {
            log.warn("现价不能高于原价：originalPrice={}, currentPrice={}", dto.getOriginalPrice(), dto.getCurrentPrice());
            return false;
        }
        return true;
    }

    /**
     * 校验票档价格逻辑（更新时，只校验非空字段）
     */
    private boolean validatePriceLogic(TicketTierDTO dto, TicketTier existingTier) {
        BigDecimal originalPrice = dto.getOriginalPrice() != null ? dto.getOriginalPrice()
                : existingTier.getOriginalPrice();
        BigDecimal currentPrice = dto.getCurrentPrice() != null ? dto.getCurrentPrice()
                : existingTier.getCurrentPrice();
        // 价格必须大于等于 0
        if (originalPrice.compareTo(BigDecimal.ZERO) < 0 || currentPrice.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("价格不能小于 0: originalPrice={}, currentPrice={}", originalPrice, currentPrice);
            return false;
        }
        // 现价不能高于原价
        if (currentPrice.compareTo(originalPrice) > 0) {
            log.warn("现价不能高于原价：originalPrice={}, currentPrice={}", originalPrice, currentPrice);
            return false;
        }
        return true;
    }

    /**
     * 校验票档库存逻辑（新增时） 规则： 1. 总库存和可用库存都不能为 null 2. 可用库存不能大于总库存 3. 库存必须大于等于 0
     */
    private boolean validateStockLogic(TicketTierDTO dto) {
        if (dto.getTotalStock() == null || dto.getAvailableStock() == null) {
            log.warn("总库存和可用库存不能为空：totalStock={}, availableStock={}", dto.getTotalStock(), dto.getAvailableStock());
            return false;
        }
        // 库存必须大于等于 0
        if (dto.getTotalStock() < 0 || dto.getAvailableStock() < 0) {
            log.warn("库存不能小于 0: totalStock={}, availableStock={}", dto.getTotalStock(), dto.getAvailableStock());
            return false;
        }
        // 可用库存不能大于总库存
        if (dto.getAvailableStock() > dto.getTotalStock()) {
            log.warn("可用库存不能大于总库存：totalStock={}, availableStock={}", dto.getTotalStock(), dto.getAvailableStock());
            return false;
        }
        return true;
    }

    /**
     * 校验票档库存逻辑（更新时，只校验非空字段）
     */
    private boolean validateStockLogic(TicketTierDTO dto, TicketTier existingTier) {
        Integer totalStock = dto.getTotalStock() != null ? dto.getTotalStock() : existingTier.getTotalStock();
        Integer availableStock = dto.getAvailableStock() != null ? dto.getAvailableStock()
                : existingTier.getAvailableStock();
        // 库存必须大于等于 0
        if (totalStock < 0 || availableStock < 0) {
            log.warn("库存不能小于 0: totalStock={}, availableStock={}", totalStock, availableStock);
            return false;
        }
        // 可用库存不能大于总库存
        if (availableStock > totalStock) {
            log.warn("可用库存不能大于总库存：totalStock={}, availableStock={}", totalStock, availableStock);
            return false;
        }
        return true;
    }
}
