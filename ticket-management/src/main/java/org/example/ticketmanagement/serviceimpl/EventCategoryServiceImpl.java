package org.example.ticketmanagement.serviceimpl;

import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.EventCategoryDTO;
import org.example.ticketmanagement.dto.EventCategoryQueryDTO;
import org.example.ticketmanagement.vo.EventCategoryVO;
import org.example.ticketmanagement.mapper.EventCategoryMapper;
import org.example.ticketmanagement.pojo.EventCategory;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.service.EventCategoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EventCategoryServiceImpl implements EventCategoryService {

    @Autowired
    private EventCategoryMapper eventCategoryMapper;

    @Override
    @Transactional
    public boolean addCategory(EventCategoryDTO eventCategoryDTO) {
        log.info("新增演出分类: {}", eventCategoryDTO.getName());

        // 参数校验看名称是否重复，体验更好
        if (eventCategoryMapper.selectByName(eventCategoryDTO.getName()) != null) {
            log.warn("分类名称已存在: {}", eventCategoryDTO.getName());
            return false;
        }


        // 1. DTO 转 Entity（并补充必要字段）
        EventCategory category = new EventCategory();
        BeanUtils.copyProperties(eventCategoryDTO, category);
        category.setCreateTime(LocalDateTime.now()); // 设置创建时间

        // 2. 调用Mapper执行插入
        int affectedRows = eventCategoryMapper.insert(category);

        // 3. 根据受影响行数判断操作结果
        boolean success = (affectedRows == 1);
        if (success) {
            log.info("新增演出分类成功，ID: {}", category.getId());
        } else {
            log.error("新增演出分类失败，受影响行数: {}", affectedRows);
        }
        return success;
    }

    @Override
    @Transactional
    public boolean deleteCategory(Long id) {
        log.info("删除演出分类，ID: {}", id);

        // 可选但推荐：先检查是否存在，使反馈更友好
        if (eventCategoryMapper.selectById(id) == null) {
            log.warn("要删除的分类不存在，ID: {}", id);
            // 这里也可以选择返回false，或者抛出一个自定义的业务异常
            return false;
        }

        int affectedRows = eventCategoryMapper.deleteById(id);
        boolean success = (affectedRows == 1);
        if (success) {
            log.info("删除演出分类成功，ID: {}", id);
        } else {
            log.warn("删除演出分类未生效，ID: {}", id); // 理论上不会走到这里，因为前面检查了存在性
        }
        return success;
    }

    @Override
    @Transactional
    public boolean updateCategory(Long id, EventCategoryDTO eventCategoryDTO) {
        log.info("更新演出分类，ID: {}", id);

        // 1. 检查要更新的目标是否存在
        EventCategory existingCategory = eventCategoryMapper.selectById(id);
        if (existingCategory == null) {
            log.warn("要更新的分类不存在，ID: {}", id);
            return false;
        }

        // 2. DTO 转 Entity（避免覆盖不需要更新的字段，如createTime）
        EventCategory category = new EventCategory();
        BeanUtils.copyProperties(eventCategoryDTO, category);
        category.setId(id); // 确保ID被设置

        // 3. 执行更新
        int affectedRows = eventCategoryMapper.update(category);
        boolean success = (affectedRows == 1);
        if (success) {
            log.info("更新演出分类成功，ID: {}", id);
        } else {
            log.warn("更新演出分类未生效，ID: {}", id);
        }
        return success;
    }

    @Override
    public EventCategoryVO getCategoryById(Long id) {
        log.debug("根据ID查询演出分类，ID: {}", id);
        EventCategory category = eventCategoryMapper.selectById(id);
        if (category == null) {
            return null;
        }
        // Entity 转 VO
        return convertToVO(category);
    }

    @Override
    public List<EventCategoryVO> getAllCategories() {
        log.debug("查询所有演出分类列表");
        List<EventCategory> categories = eventCategoryMapper.selectAll();
        // 使用Stream API将Entity列表转换为VO列表
        return categories.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<EventCategoryVO> getCategoriesByStatus(Integer status) {
        log.debug("根据状态查询演出分类，status: {}", status);
        List<EventCategory> categories = eventCategoryMapper.selectByStatus(status);
        return categories.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    // ================ 新增分页查询方法 ================

    @Override
    public PageResult<EventCategoryVO> queryCategories(EventCategoryQueryDTO queryDTO) {
        log.debug("分页查询分类，参数: {}", queryDTO);

        // 1. 参数校验
        if (!queryDTO.validate()) {
            log.warn("分页参数无效: page={}, size={}", queryDTO.getPage(), queryDTO.getSize());
            queryDTO.setPage(1);
            queryDTO.setSize(10);
        }

        // 2. 查询总记录数
        Long total = eventCategoryMapper.countByCondition(queryDTO);
        log.debug("查询到符合条件的分类总数: {}", total);

        // 3. 如果没有数据，直接返回空结果
        if (total == 0) {
            log.debug("未找到符合条件的分类");
            return PageResult.empty(queryDTO);
        }

        // 4. 计算有效页码
        int maxPage = (int) Math.ceil((double) total / queryDTO.getSize());
        if (queryDTO.getPage() > maxPage) {
            log.debug("请求页码 {} 超出范围，调整为最大页码 {}", queryDTO.getPage(), maxPage);
            queryDTO.setPage(maxPage);
        }

        // 5. 分页查询数据
        List<EventCategory> categories = eventCategoryMapper.selectByCondition(queryDTO);
        log.debug("查询到当前页分类数量: {}", categories.size());

        // 6. 转换为VO
        List<EventCategoryVO> voList = categories.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 7. 返回分页结果
        return PageResult.success(voList, total, queryDTO);
    }

    /**
     * 内部辅助方法：将 Entity 对象转换为 VO 对象
     * 将转换逻辑封装在这里，避免代码重复
     */
    private EventCategoryVO convertToVO(EventCategory category) {
        EventCategoryVO vo = new EventCategoryVO();
        BeanUtils.copyProperties(category, vo);
        return vo;
    }
}