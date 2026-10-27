package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.dto.AdminSessionQuery;
import org.example.ticketmanagement.dto.AdminRefundQuery;
import org.example.ticketmanagement.mapper.AdminOverviewMapper;
import org.example.ticketmanagement.service.AdminOverviewService;
import org.example.ticketmanagement.pojo.PageQuery;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.vo.EventSessionVO;
import org.example.ticketmanagement.vo.AdminDashboardVO;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminOverviewServiceImpl implements AdminOverviewService {
    private final AdminOverviewMapper mapper;
    private final Clock clock;

    @Override
    public PageResult<EventSessionVO> sessions(AdminSessionQuery query) {
        validatePage(query);
        if (query.getStatus() != null && query.getStatus() != 0 && query.getStatus() != 1) {
            throw new IllegalArgumentException("场次状态必须为0或1");
        }
        if (query.getEventStatus() != null && query.getEventStatus() != 0 && query.getEventStatus() != 1) {
            throw new IllegalArgumentException("演出状态必须为0或1");
        }
        if (query.getStartTime() != null && query.getEndTime() != null
                && query.getStartTime().isAfter(query.getEndTime())) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        return new PageResult<>(mapper.sessions(query), mapper.countSessions(query), query);
    }

    @Override
    public PageResult<Map<String, Object>> refunds(AdminRefundQuery query) {
        validatePage(query);
        return new PageResult<>(mapper.refunds(query), mapper.countRefunds(query), query);
    }

    @Override
    public AdminDashboardVO summary() {
        LocalDateTime now = LocalDateTime.now(clock);
        AdminDashboardVO summary = mapper.summary(now, now.plusDays(7));
        summary.setAsOf(now);
        return summary;
    }

    private void validatePage(PageQuery query) {
        // 限制分页规模并防止偏移量整数溢出。
        if (!query.validate() || query.getSize() > 200
                || (long) (query.getPage() - 1) * query.getSize() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("分页参数不合法");
        }
    }
}
