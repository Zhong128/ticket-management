package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.dto.OrderQueryDTO;
import org.example.ticketmanagement.dto.SeckillQualificationDTO;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.pojo.User;
import org.example.ticketmanagement.pojo.UserAddress;
import org.example.ticketmanagement.service.CityService;
import org.example.ticketmanagement.service.EventService;
import org.example.ticketmanagement.service.EventSessionService;
import org.example.ticketmanagement.service.OrderService;
import org.example.ticketmanagement.service.ReservationService;
import org.example.ticketmanagement.service.SeckillService;
import org.example.ticketmanagement.service.TicketTierService;
import org.example.ticketmanagement.service.UserAddressService;
import org.example.ticketmanagement.service.UserService;
import org.example.ticketmanagement.serviceimpl.CustomerServiceTools;
import org.example.ticketmanagement.vo.EventSessionVO;
import org.example.ticketmanagement.vo.OrderInfoVO;
import org.example.ticketmanagement.vo.EventVO;
import org.example.ticketmanagement.vo.TicketTierVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerServiceToolsTest {

    private final EventService events = mock(EventService.class);
    private final EventSessionService sessions = mock(EventSessionService.class);
    private final CityService cities = mock(CityService.class);
    private final UserService users = mock(UserService.class);
    private final UserAddressService addresses = mock(UserAddressService.class);
    private final OrderService orders = mock(OrderService.class);
    private final SeckillService seckill = mock(SeckillService.class);
    private final TicketTierService tiers = mock(TicketTierService.class);
    private final ReservationService reservations = mock(ReservationService.class);
    private final ToolContext identity = new ToolContext(Map.of("userId", 91L));
    private CustomerServiceTools tools;

    @BeforeEach
    void setUp() {
        tools = new CustomerServiceTools(events, sessions, cities, users, addresses, orders,
                seckill, tiers, reservations);
    }

    @Test
    void distinguishesNoDataFromBackendFailure() {
        assertEquals("EMPTY", tools.queryEventsByCity("不存在").status());
        when(cities.getCityByName("北京")).thenThrow(new IllegalStateException("database private detail"));
        var result = tools.queryEventsByCity("北京");
        assertEquals("ERROR", result.status());
        assertEquals("QUERY_FAILED", result.errorCode());
        assertFalse(result.message().contains("database"));
    }

    @Test
    void resolvesUserOnlyFromServerContextAndRestrictsPageSize() {
        when(orders.getUserOrders(any(), eq(91L))).thenReturn(new PageResult<>(List.of(), 0L, 2, 20));
        assertEquals("EMPTY", tools.getUserOrders(2, 20, null, identity).status());
        verify(orders).getUserOrders(argThat(q -> q.getPage() == 2 && q.getSize() == 20), eq(91L));
        assertEquals("REJECTED", tools.getUserOrders(1, 1000, null, identity).status());
        assertEquals("REJECTED", tools.getUserOrders(1, 10, null, new ToolContext(Map.of())).status());
        verify(orders, times(1)).getUserOrders(any(OrderQueryDTO.class), anyLong());
    }

    @Test
    void stripsPersonalInformationBeforeReturningToModel() {
        User user = new User();
        user.setPhone("13812345678");
        user.setEmail("private@example.com");
        user.setIdCard("private-id");
        user.setPassword("secret-hash");
        user.setRealName("张三");
        when(users.getUserById(91L)).thenReturn(user);
        String profile = tools.getUserProfile(identity).data().toString();
        assertTrue(profile.contains("138****5678"));
        assertFalse(profile.contains("private"));
        assertFalse(profile.contains("张三"));
        assertFalse(profile.contains("secret"));
        OrderInfoVO order = new OrderInfoVO();
        order.setId(42L);
        order.setUserName("private-name");
        order.setRemark("private-remark");
        when(orders.getOrderDetail(42L, 91L)).thenReturn(order);
        assertFalse(tools.getOrderDetail(42L, identity).data().toString().contains("private"));
    }

    @Test
    void sessionQueryAndReservationUseExistingServices() {
        when(events.getPublicEventById(5L)).thenReturn(new EventVO());
        when(sessions.getSessionsByEventIdAndStatus(5L, 1)).thenReturn(List.of(new EventSessionVO()));
        assertEquals("SUCCESS", tools.getEventSessions(5L).status());
        when(reservations.request(8L, 91L)).thenReturn(Map.of("requestStatus", "PENDING"));
        assertEquals("SUCCESS", tools.getReservationStatus(8L, identity).status());
        verify(reservations).request(8L, 91L);
    }

    @Test
    void hiddenParentEventDoesNotExposeSessionsOrTiers() {
        assertEquals("EMPTY", tools.getEventSessions(5L).status());
        verifyNoInteractions(sessions);
        EventSessionVO session = new EventSessionVO();
        session.setStatus(1);
        session.setEventId(5L);
        when(sessions.getSessionById(3L)).thenReturn(session);
        assertEquals("EMPTY", tools.getSessionDetail(3L).status());
        assertEquals("EMPTY", tools.getSessionTiers(3L, 1, 10).status());
        verifyNoInteractions(tiers);
    }

    @Test
    void tierPriceAndZeroRealTimeStockRemainSuccessfulQueries() {
        when(events.getPublicEventById(5L)).thenReturn(new EventVO());
        EventSessionVO session = new EventSessionVO();
        session.setStatus(1);
        session.setEventId(5L);
        when(sessions.getSessionById(3L)).thenReturn(session);
        TicketTierVO tier = new TicketTierVO();
        tier.setId(7L);
        tier.setEventId(5L);
        tier.setSessionId(3L);
        tier.setStatus(1);
        tier.setCurrentPrice(new BigDecimal("299.00"));
        tier.setAvailableStock(999);
        when(tiers.queryTicketTiers(any())).thenReturn(new PageResult<>(List.of(tier), 1L, 1, 10));
        var result = tools.getSessionTiers(3L, 1, 10);
        assertEquals(new BigDecimal("299.00"), result.data().getRecords().getFirst().price());
        assertFalse(result.data().getRecords().getFirst().toString().contains("999"));
        verify(tiers).queryTicketTiers(argThat(dto -> dto.getSessionId() == 3L && dto.getStatus() == 1));
        when(tiers.getTicketTierById(7L)).thenReturn(tier);
        when(seckill.getRealTimeStock(7L)).thenReturn(0);
        assertEquals("SUCCESS", tools.getRealTimeStock(7L).status());
        assertEquals(0, tools.getRealTimeStock(7L).data());
    }

    @Test
    void qualificationDenialAndFailureAreDifferentResults() {
        when(seckill.getSeckillQualification(91L, 7L, 2)).thenReturn(
                SeckillQualificationDTO.denied("SALE_NOT_STARTED", "尚未开售"));
        assertEquals("REJECTED", tools.checkSeckillQualification(7L, 2, identity).status());
        assertEquals("SALE_NOT_STARTED", tools.checkSeckillQualification(7L, 2, identity).errorCode());
        when(seckill.getSeckillQualification(91L, 7L, 2)).thenThrow(new IllegalStateException("redis unavailable"));
        assertEquals("ERROR", tools.checkSeckillQualification(7L, 2, identity).status());
    }

    @Test
    void callbackUsesServerIdentityEvenIfModelAddsUserId() {
        var callback = Arrays.stream(ToolCallbacks.from(tools))
                .filter(tool -> tool.getToolDefinition().name().equals("getUserProfile")).findFirst().orElseThrow();
        callback.call("{\"userId\":92}", identity);
        verify(users).getUserById(91L);
        verify(users, never()).getUserById(92L);
    }

    @Test
    void inaccessibleReservationIsRejectedWhileInfrastructureFailureIsError() {
        when(reservations.request(8L, 91L)).thenThrow(new IllegalArgumentException("记录不存在或无权访问"));
        assertEquals("REJECTED", tools.getReservationStatus(8L, identity).status());
        doThrow(new IllegalStateException("database unavailable")).when(reservations).request(8L, 91L);
        assertEquals("ERROR", tools.getReservationStatus(8L, identity).status());
    }

    @Test
    void inaccessibleOrderIsRejectedWhileInfrastructureFailureIsError() {
        when(orders.getOrderDetail(8L, 91L)).thenThrow(new IllegalArgumentException("记录不可访问"));
        assertEquals("REJECTED", tools.getOrderDetail(8L, identity).status());
        doThrow(new IllegalStateException("database unavailable")).when(orders).getOrderDetail(8L, 91L);
        assertEquals("ERROR", tools.getOrderDetail(8L, identity).status());
    }

    @Test
    void reservationProjectionDoesNotForwardIdempotencyKeyOrFuturePrivateFields() {
        when(reservations.request(8L, 91L)).thenReturn(Map.of("requestStatus", "PENDING",
                "requestKey", "private-key", "userPhone", "13812345678"));
        var data = tools.getReservationStatus(8L, identity).data();
        assertEquals("PENDING", data.get("requestStatus"));
        assertFalse(data.containsKey("requestKey"));
        assertFalse(data.containsKey("userPhone"));
    }

    @Test
    void addressProjectionRemovesRecipientAndStreet() {
        UserAddress address = new UserAddress();
        address.setReceiverName("private-recipient");
        address.setReceiverPhone("13812345678");
        address.setDetailAddress("private-street");
        address.setCity("北京");
        when(addresses.listUserAddressesByUserIdEntity(91L)).thenReturn(List.of(address));
        String data = tools.getUserAddresses(identity).data().toString();
        assertTrue(data.contains("北京"));
        assertTrue(data.contains("138****5678"));
        assertFalse(data.contains("private"));
        assertFalse(data.contains("13812345678"));
    }

    @Test
    void schemasNeverExposeIdentityOrWriteTools() {
        var callbacks = Arrays.asList(ToolCallbacks.from(tools));
        assertTrue(callbacks.size() >= 13);
        callbacks.forEach(callback -> {
            String schema = callback.getToolDefinition().inputSchema();
            assertFalse(schema.contains("userId"));
            assertFalse(schema.contains("toolContext"));
            assertFalse(List.of("updateUserProfile", "addUserAddress", "createOrder", "cancelOrder")
                    .contains(callback.getToolDefinition().name()));
        });
    }
}
