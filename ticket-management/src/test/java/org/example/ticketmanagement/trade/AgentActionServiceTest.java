package org.example.ticketmanagement.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ticketmanagement.dto.SeckillQualificationDTO;
import org.example.ticketmanagement.exception.AgentRequestException;
import org.example.ticketmanagement.exception.ReservationRejectedException;
import org.example.ticketmanagement.mapper.EventMapper;
import org.example.ticketmanagement.mapper.EventSessionMapper;
import org.example.ticketmanagement.mapper.TicketTierMapper;
import org.example.ticketmanagement.pojo.AgentAction;
import org.example.ticketmanagement.pojo.Event;
import org.example.ticketmanagement.pojo.EventSession;
import org.example.ticketmanagement.pojo.TicketTier;
import org.example.ticketmanagement.service.OrderService;
import org.example.ticketmanagement.serviceimpl.AgentActionService;
import org.example.ticketmanagement.serviceimpl.AgentActionStore;
import org.example.ticketmanagement.serviceimpl.ReservationServiceImpl;
import org.example.ticketmanagement.serviceimpl.SeckillServiceImpl;
import org.example.ticketmanagement.vo.OrderInfoVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentActionServiceTest {

    private final AgentActionStore store = mock(AgentActionStore.class);
    private final TicketTierMapper tiers = mock(TicketTierMapper.class);
    private final EventSessionMapper sessions = mock(EventSessionMapper.class);
    private final EventMapper events = mock(EventMapper.class);
    private final SeckillServiceImpl seckill = mock(SeckillServiceImpl.class);
    private final ReservationServiceImpl reservations = mock(ReservationServiceImpl.class);
    private final OrderService orders = mock(OrderService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T01:00:00Z"), ZoneOffset.UTC);
    private final AgentActionService service = new AgentActionService(store, tiers, sessions, events,
            seckill, reservations, orders, clock);
    private AgentAction action;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "tradeEnabled", true);
        ReflectionTestUtils.setField(service, "customerServiceEnabled", true);
        action = new AgentAction();
        action.setId("00000000-0000-0000-0000-000000000001");
        action.setUserId(7);
        action.setConversationId("conversation");
        action.setRunId("run");
        action.setType("PURCHASE");
        action.setTargetId(8);
        action.setQuantity(2);
        action.setUnitPrice(new BigDecimal("100.00"));
        action.setTotalAmount(new BigDecimal("200.00"));
        action.setEventName("演出");
        action.setSessionName("晚场");
        action.setSessionTime(LocalDateTime.now(clock).plusDays(1));
        action.setTierName("看台");
        action.setRequestKey("fixed-key");
        action.setStatus("EXECUTING");
        action.setExpiresAt(LocalDateTime.now(clock).plusMinutes(5));
        action.setUpdatedAt(LocalDateTime.now(clock));
        when(store.get(7, action.getId())).thenAnswer(call -> action);
        when(store.claim(7, action.getId())).thenReturn(new AgentActionStore.Claim(action, true));
        doAnswer(call -> {
            action.setStatus(call.getArgument(1));
            action.setRequestStatus(call.getArgument(2));
            action.setOrderId(call.getArgument(3));
            action.setMessage(call.getArgument(4));
            return null;
        }).when(store).result(same(action), anyString(), nullable(String.class), nullable(Long.class), anyString());
        when(seckill.getSeckillQualification(7L, 8L, 2)).thenReturn(SeckillQualificationDTO.allowed());
        TicketTier tier = new TicketTier();
        tier.setId(8L);
        tier.setEventId(3L);
        tier.setSessionId(4L);
        tier.setStatus(1);
        tier.setTierName("看台");
        tier.setCurrentPrice(new BigDecimal("100.00"));
        when(tiers.selectById(8L)).thenReturn(tier);
        Event event = new Event();
        event.setId(3L);
        event.setName("演出");
        event.setStatus(1);
        when(events.selectById(3L)).thenReturn(event);
        EventSession session = new EventSession();
        session.setId(4L);
        session.setEventId(3L);
        session.setStatus(1);
        session.setSessionName("晚场");
        session.setSessionTime(action.getSessionTime());
        when(sessions.selectById(4L)).thenReturn(session);
    }

    @Test
    void proposingDraftDoesNotReserveOrCancelAndPublicViewDoesNotLeakKey() throws Exception {
        when(store.create(eq(7L), eq("run"), any())).thenAnswer(call -> {
            AgentAction draft = call.getArgument(2);
            draft.setId(action.getId());
            draft.setExpiresAt(action.getExpiresAt());
            draft.setRequestKey("private-key");
            return draft;
        });
        var view = service.proposePurchase(7, "run", 8, 2);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(view);
        assertFalse(json.contains("private-key"));
        assertFalse(json.contains("userId"));
        // 工具JSON和HTTP必须使用同样的ISO时间，不能将卡片日期发成数组。
        var data = new ObjectMapper().readTree(json);
        assertTrue(data.path("sessionTime").isTextual());
        assertTrue(data.path("expiresAt").isTextual());
        assertEquals(new BigDecimal("200.00"), view.totalAmount());
        verify(seckill, never()).reserveConfirmedApplication(anyLong(), anyLong(), anyInt(), anyString(), any());
        verifyNoInteractions(orders);
    }

    @Test
    void changedPriceRejectsBeforeBusinessWrite() {
        tiers.selectById(8L).setCurrentPrice(new BigDecimal("120.00"));
        assertEquals("REJECTED", service.confirm(7, action.getId()).status());
        verify(seckill, never()).reserveConfirmedApplication(anyLong(), anyLong(), anyInt(), anyString(), any());
    }

    @Test
    void soldOutAtAdmissionIsKnownRejectionAndAllowsNewDraftInsteadOfPermanentUnknown() {
        when(seckill.reserveConfirmedApplication(anyLong(), anyLong(), anyInt(), anyString(), any()))
                .thenThrow(new ReservationRejectedException("库存不足"));
        assertEquals("REJECTED", service.confirm(7, action.getId()).status());
        assertEquals("REJECTED", action.getRequestStatus());
        when(store.create(eq(7L), eq("retry-run"), any())).thenAnswer(call -> {
            AgentAction draft = call.getArgument(2);
            draft.setId("new-card");
            draft.setExpiresAt(action.getExpiresAt());
            draft.setStatus("PENDING");
            return draft;
        });
        assertEquals("PENDING", service.proposePurchase(7, "retry-run", 8, 2).status());
        verify(store).create(eq(7L), eq("retry-run"), any());
        verifyNoInteractions(reservations);
    }

    @Test
    void acceptedApplicationIsNotShownAsCreatedOrderAndRepeatOnlyQueries() {
        when(seckill.reserveConfirmedApplication(7, 8, 2, "fixed-key", action.getUnitPrice()))
                .thenReturn(Map.of("requestStatus", "RESERVED"));
        when(reservations.requestByKey(7, "fixed-key")).thenReturn(Map.of("requestStatus", "RESERVED"));
        assertEquals("EXECUTING", service.confirm(7, action.getId()).status());
        when(store.claim(7, action.getId())).thenReturn(new AgentActionStore.Claim(action, false));
        when(reservations.requestByKey(7, "fixed-key")).thenReturn(Map.of("requestStatus", "ORDER_CREATED", "orderId", 99L));
        var view = service.confirm(7, action.getId());
        assertEquals("SUCCEEDED", view.status());
        assertEquals("99", view.orderId());
        verify(seckill, times(1)).reserveConfirmedApplication(7, 8, 2, "fixed-key", action.getUnitPrice());
    }

    @Test
    void unknownWriteDoesNotReplayAndClearedPreviousCanBeResolvedWithoutReturningItsContent() {
        when(seckill.reserveConfirmedApplication(anyLong(), anyLong(), anyInt(), anyString(), any()))
                .thenThrow(new IllegalStateException("private backend reason"));
        when(reservations.requestByKey(7, "fixed-key")).thenReturn(Map.of("requestStatus", "UNKNOWN"));
        var view = service.confirm(7, action.getId());
        assertEquals("UNKNOWN", view.status());
        assertFalse(view.message().contains("private"));
        action.setContentErased(true);
        when(store.unresolved(7, "PURCHASE", 8)).thenReturn(action);
        when(reservations.requestByKey(7, "fixed-key")).thenReturn(Map.of("requestStatus", "REJECTED"));
        when(store.create(eq(7L), eq("new-run"), any())).thenThrow(new AgentRequestException(409, "test stop"));
        assertThrows(AgentRequestException.class, () -> service.proposePurchase(7, "new-run", 8, 2));
        assertEquals("REJECTED", action.getStatus());
        verify(seckill, times(1)).reserveConfirmedApplication(anyLong(), anyLong(), anyInt(), anyString(), any());
    }

    @Test
    void cancellationPendingIsNotReportedAsCanceledAndPaidIsRejected() {
        action.setType("CANCEL");
        OrderInfoVO order = new OrderInfoVO();
        order.setOrderStatus(0);
        when(orders.getOrderDetail(8L, 7L)).thenReturn(order);
        when(orders.userCancelOrder(8L, 7L)).thenAnswer(call -> { order.setOrderStatus(4); return false; });
        assertEquals("EXECUTING", service.confirm(7, action.getId()).status());
        when(store.claim(7, action.getId())).thenReturn(new AgentActionStore.Claim(action, false));
        order.setOrderStatus(1);
        assertEquals("REJECTED", service.confirm(7, action.getId()).status());
        verify(orders, times(1)).userCancelOrder(8L, 7L);
    }

    @Test
    void maintenanceRejectsBeforeObtainingExecutionClaim() {
        ReflectionTestUtils.setField(service, "tradeEnabled", false);
        assertThrows(AgentRequestException.class, () -> service.confirm(7, action.getId()));
        verify(store, never()).claim(anyLong(), anyString());
        verifyNoInteractions(orders);
    }
}
