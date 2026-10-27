package org.example.ticketmanagement.service;

import org.example.ticketmanagement.dto.AdminRefundQuery;
import org.example.ticketmanagement.dto.AdminSessionQuery;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.vo.AdminDashboardVO;
import org.example.ticketmanagement.vo.EventSessionVO;

import java.util.Map;

public interface AdminOverviewService {
    /** 按管理端条件查询场次，允许查看下架记录。 */
    PageResult<EventSessionVO> sessions(AdminSessionQuery query);

    /** 按流水、订单和状态查询退款分页。 */
    PageResult<Map<String, Object>> refunds(AdminRefundQuery query);

    /** 查询同一时刻的管理概览指标。 */
    AdminDashboardVO summary();
}
