package org.example.ticketmanagement.service;

import org.example.ticketmanagement.dto.EventCategoryDTO;
import org.example.ticketmanagement.dto.EventCategoryQueryDTO;
import org.example.ticketmanagement.vo.EventCategoryVO;
import org.example.ticketmanagement.pojo.PageResult;

import java.util.List;

public interface EventCategoryService {

    /**
     * 新增演出分类
     * @param eventCategoryDTO 分类数据
     * @return 新增成功返回 true，失败返回 false
     */
    boolean addCategory(EventCategoryDTO eventCategoryDTO);

    /**
     * 根据ID删除演出分类
     * @param id 分类ID
     * @return 删除成功返回 true，失败返回 false
     */
    boolean deleteCategory(Long id);

    /**
     * 更新演出分类信息
     * @param id 要更新的分类ID
     * @param eventCategoryDTO 新的分类数据
     * @return 更新成功返回 true，失败返回 false
     */
    boolean updateCategory(Long id, EventCategoryDTO eventCategoryDTO);

    /**
     * 根据ID查询演出分类详情
     * @param id 分类ID
     * @return 分类详情视图对象，未找到返回 null
     */
    EventCategoryVO getCategoryById(Long id);

    /**
     * 根据状态查询演出分类列表
     * @param status 状态 (0-禁用， 1-启用)
     * @return 符合条件的分类视图对象列表
     */
    List<EventCategoryVO> getCategoriesByStatus(Integer status);

    // 新增分页查询方法
    PageResult<EventCategoryVO> queryCategories(EventCategoryQueryDTO queryDTO);
}