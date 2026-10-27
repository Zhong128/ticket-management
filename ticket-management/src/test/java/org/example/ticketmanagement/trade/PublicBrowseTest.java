package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.controller.EventController;
import org.example.ticketmanagement.dto.EventQueryDTO;
import org.example.ticketmanagement.service.EventService;
import org.example.ticketmanagement.serviceimpl.EventServiceImpl;
import org.example.ticketmanagement.mapper.EventMapper;
import org.example.ticketmanagement.pojo.Event;
import org.example.ticketmanagement.vo.EventVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicBrowseTest {

    @Test
    void publicServiceEnforcesVisibilityBeforeLoadingPrice() {
        EventMapper mapper = mock(EventMapper.class);
        EventServiceImpl service = new EventServiceImpl();
        ReflectionTestUtils.setField(service, "eventMapper", mapper);
        Event draft = new Event();
        draft.setId(1L);
        draft.setStatus(0);
        when(mapper.selectById(1L)).thenReturn(draft);
        assertNull(service.getPublicEventById(1L));
        verify(mapper, never()).selectMinPrices(anyList());

        EventQueryDTO query = new EventQueryDTO();
        query.setStatus(0);
        when(mapper.countByCondition(query)).thenReturn(0L);
        service.queryPublicEvents(query);
        assertEquals(1, query.getStatus());
    }

    @Test
    void publicListOverridesRequestedDraftStatus() {
        EventService service = mock(EventService.class);
        EventController controller = new EventController();
        ReflectionTestUtils.setField(controller, "eventService", service);
        EventQueryDTO query = new EventQueryDTO();
        query.setStatus(0);
        controller.getEventsByPage(query);
        verify(service).queryPublicEvents(query);
    }

    @Test
    void publicDetailHidesDraftAndMissingEvents() {
        EventService service = mock(EventService.class);
        EventController controller = new EventController();
        ReflectionTestUtils.setField(controller, "eventService", service);
        when(service.getPublicEventById(7L)).thenReturn(null);
        assertEquals(0, controller.getPublicEvent(7L).getCode());
        assertEquals(0, controller.getPublicEvent(8L).getCode());
        EventVO published = new EventVO();
        published.setStatus(1);
        when(service.getPublicEventById(7L)).thenReturn(published);
        assertSame(published, controller.getPublicEvent(7L).getData());
    }
}
