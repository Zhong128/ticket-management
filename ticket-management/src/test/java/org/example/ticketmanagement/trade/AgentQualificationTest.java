package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.mapper.EventMapper;
import org.example.ticketmanagement.mapper.EventSessionMapper;
import org.example.ticketmanagement.mapper.TicketTierMapper;
import org.example.ticketmanagement.pojo.Event;
import org.example.ticketmanagement.pojo.EventSession;
import org.example.ticketmanagement.pojo.TicketTier;
import org.example.ticketmanagement.service.SeckillLockService;
import org.example.ticketmanagement.serviceimpl.ReservationServiceImpl;
import org.example.ticketmanagement.serviceimpl.SeckillServiceImpl;
import org.example.ticketmanagement.serviceimpl.StockServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentQualificationTest {

    private final TicketTierMapper tiers = mock(TicketTierMapper.class);
    private final EventSessionMapper sessions = mock(EventSessionMapper.class);
    private final EventMapper events = mock(EventMapper.class);
    private final StockServiceImpl stock = mock(StockServiceImpl.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"), ZoneOffset.UTC);
    private final SeckillServiceImpl service = new SeckillServiceImpl(mock(ReservationServiceImpl.class),
            stock, mock(SeckillLockService.class), tiers, sessions, events, clock);

    @Test
    void reportsSpecificReasonsAndPreservesBooleanContract() {
        assertEquals("INVALID_QUANTITY", service.getSeckillQualification(91L, 1L, 0).reason());
        assertFalse(service.checkSeckillQualification(91L, 1L, 0));
        assertEquals("TIER_UNAVAILABLE", service.getSeckillQualification(91L, 1L, 1).reason());
        TicketTier tier = new TicketTier();
        tier.setStatus(1);
        tier.setAvailableStock(1);
        tier.setEventId(2L);
        tier.setSessionId(3L);
        when(tiers.selectById(1L)).thenReturn(tier);
        assertEquals("INSUFFICIENT_STOCK", service.getSeckillQualification(91L, 1L, 2).reason());
        Event event = new Event();
        event.setStatus(1);
        EventSession session = new EventSession();
        session.setStatus(1);
        when(events.selectById(2L)).thenReturn(event);
        when(sessions.selectById(3L)).thenReturn(session);
        session.setStatus(0);
        assertEquals("EVENT_UNAVAILABLE", service.getSeckillQualification(91L, 1L, 1).reason());
        session.setStatus(1);
        event.setStatus(0);
        assertEquals("EVENT_UNAVAILABLE", service.getSeckillQualification(91L, 1L, 1).reason());
        event.setStatus(1);
        event.setSaleStartTime(LocalDateTime.now(clock).plusMinutes(1));
        assertEquals("SALE_NOT_STARTED", service.getSeckillQualification(91L, 1L, 1).reason());
        event.setSaleStartTime(null);
        event.setSaleEndTime(LocalDateTime.now(clock));
        assertEquals("SALE_ENDED", service.getSeckillQualification(91L, 1L, 1).reason());
        event.setSaleEndTime(null);
        when(stock.hasUserSeckilled(91L, 1L)).thenReturn(true);
        assertEquals("ALREADY_PURCHASED", service.getSeckillQualification(91L, 1L, 1).reason());
        verify(stock, never()).hasEnoughStock(1L, 1);
        when(stock.hasUserSeckilled(91L, 1L)).thenReturn(false);
        assertEquals("INSUFFICIENT_STOCK", service.getSeckillQualification(91L, 1L, 1).reason());
        when(stock.hasEnoughStock(1L, 1)).thenReturn(true);
        assertTrue(service.getSeckillQualification(91L, 1L, 1).qualified());
        assertTrue(service.checkSeckillQualification(91L, 1L, 1));
    }
}
