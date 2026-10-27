package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.dto.AdminSessionQuery;
import org.example.ticketmanagement.dto.AdminRefundQuery;
import org.example.ticketmanagement.mapper.AdminOverviewMapper;
import org.example.ticketmanagement.service.AdminOverviewService;
import org.example.ticketmanagement.serviceimpl.AdminOverviewServiceImpl;
import org.example.ticketmanagement.vo.AdminDashboardVO;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminQueriesTest {
    @Test
    void sessionPageExposesExplicitParentVisibilityAndSafeSortDirection() {
        assertDoesNotThrow(() -> AdminSessionQuery.class.getMethod("setEventStatus", Integer.class));
        assertDoesNotThrow(() -> AdminSessionQuery.class.getMethod("setAscending", boolean.class));
    }

    @Test
    void rejectsReversedDateRangeAndOversizedPagesBeforeQuery() {
        AdminOverviewMapper mapper = mock(AdminOverviewMapper.class);
        AdminOverviewService service = new AdminOverviewServiceImpl(mapper,
                Clock.fixed(Instant.parse("2026-10-27T04:00:00Z"), ZoneOffset.UTC));
        AdminSessionQuery query = new AdminSessionQuery();
        query.setStartTime(LocalDateTime.of(2026, 10, 27, 12, 0));
        query.setEndTime(query.getStartTime().minusHours(1));
        assertThrows(IllegalArgumentException.class, () -> service.sessions(query));
        AdminRefundQuery refunds = new AdminRefundQuery();
        refunds.setSize(201);
        assertThrows(IllegalArgumentException.class, () -> service.refunds(refunds));
        verifyNoInteractions(mapper);
    }

    @Test
    void returnsCompatiblePageMetadataAndSingleSummaryTimestamp() {
        AdminOverviewMapper mapper = mock(AdminOverviewMapper.class);
        AdminOverviewService service = new AdminOverviewServiceImpl(mapper,
                Clock.fixed(Instant.parse("2026-10-27T04:00:00Z"), ZoneOffset.UTC));
        AdminRefundQuery query = new AdminRefundQuery();
        query.setPage(2);
        when(mapper.refunds(query)).thenReturn(List.of());
        when(mapper.countRefunds(query)).thenReturn(25L);
        var page = service.refunds(query);
        assertEquals(25L, page.getTotal());
        assertEquals(3, page.getTotalPages());
        assertTrue(page.getHasPrevious());
        assertTrue(page.getHasNext());
        when(mapper.summary(any(), any())).thenReturn(new AdminDashboardVO());
        assertEquals(LocalDateTime.of(2026, 10, 27, 4, 0), service.summary().getAsOf());
        verify(mapper).summary(LocalDateTime.of(2026, 10, 27, 4, 0), LocalDateTime.of(2026, 11, 3, 4, 0));
    }
}
