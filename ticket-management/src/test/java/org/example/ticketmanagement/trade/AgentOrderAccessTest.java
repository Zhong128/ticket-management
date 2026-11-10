package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.mapper.OrderInfoMapper;
import org.example.ticketmanagement.pojo.OrderInfo;
import org.example.ticketmanagement.serviceimpl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AgentOrderAccessTest {

    @Test
    void absentAndForeignOrdersUseExplicitRejectionWithoutHidingDatabaseFailure() {
        OrderInfoMapper mapper = mock(OrderInfoMapper.class);
        OrderServiceImpl service = mock(OrderServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "orderInfoMapper", mapper);
        assertThrows(IllegalArgumentException.class, () -> service.getOrderDetail(1L, 91L));
        OrderInfo foreign = new OrderInfo();
        foreign.setUserId(92L);
        when(mapper.selectById(1L)).thenReturn(foreign);
        assertThrows(IllegalArgumentException.class, () -> service.getOrderDetail(1L, 91L));
        when(mapper.selectById(1L)).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, () -> service.getOrderDetail(1L, 91L));
    }
}
