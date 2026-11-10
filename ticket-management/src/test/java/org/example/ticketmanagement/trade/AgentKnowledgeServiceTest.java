package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.exception.AgentRequestException;
import org.example.ticketmanagement.mapper.AgentKnowledgeMapper;
import org.example.ticketmanagement.serviceimpl.AgentKnowledgeService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentKnowledgeServiceTest {

    @Test
    void genericRequestsAndClearlyExternalBusinessDoNotQueryTicketRules() {
        var mapper = mock(AgentKnowledgeMapper.class);
        var service = new AgentKnowledgeService(mapper);
        for (String question : List.of("帮我写一首诗", "一加一等于几", "帮我翻译hello world",
                "酒店订单可以取消吗", "机票退款手续费是多少", "网购衣服付款后多久发货", "证券账户股票库存可以转赠吗")) {
            assertTrue(service.search(question).isEmpty());
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void implicitCheckoutAndBudgetExpressionsKeepRelevantQueries() {
        var mapper = mock(AgentKnowledgeMapper.class);
        when(mapper.search(anyList(), eq(5))).thenReturn(List.of());
        var service = new AgentKnowledgeService(mapper);
        service.search("点购买后断网，不清楚有没有提交上，要再点一次吗？");
        service.search("机器人显示维护或调用次数用完，需要一直等着吗？");
        service.search("我看到的确认价后来涨了，会不经过我同意按新金额下单吗？");
        service.search("工具上限是什么意思？");
        verify(mapper, times(4)).search(anyList(), eq(5));
    }

    @Test
    void performanceContextKeepsMixedComparisonsAndNormalizesBusinessExpressions() {
        var mapper = mock(AgentKnowledgeMapper.class);
        when(mapper.search(anyList(), eq(5))).thenReturn(List.of());
        var service = new AgentKnowledgeService(mapper);
        service.search("演唱会门票能像机票一样换日期，挑座位或者送给朋友吗？");
        verify(mapper).search(argThat(terms -> terms.contains("改签") && terms.contains("选座")
                && terms.contains("转赠") && !terms.contains("帮我") && terms.size() <= 24), eq(5));
    }

    @Test
    void punctuationDoesNotRunAnUnboundedOrEmptySqlQuery() {
        var mapper = mock(AgentKnowledgeMapper.class);
        var service = new AgentKnowledgeService(mapper);
        assertTrue(service.search("？！？👋").isEmpty());
        verifyNoInteractions(mapper);
        assertThrows(AgentRequestException.class, () -> service.search("x".repeat(2001)));
        verifyNoInteractions(mapper);
    }

    @Test
    void invalidSourceAndVersionAreRejectedBeforeDatabaseAccess() {
        var mapper = mock(AgentKnowledgeMapper.class);
        var service = new AgentKnowledgeService(mapper);
        assertThrows(AgentRequestException.class, () -> service.get("../../config", 1));
        assertThrows(AgentRequestException.class, () -> service.get("KB-ORD-001", 0));
        verifyNoInteractions(mapper);
        assertEquals(404, assertThrows(AgentRequestException.class, () -> service.get("KB-ORD-001", 1)).getCode());
    }

    @Test
    void longNaturalLanguageQueryKeepsDatabaseWorkBounded() {
        var mapper = mock(AgentKnowledgeMapper.class);
        when(mapper.search(anyList(), eq(5))).thenReturn(List.of());
        var service = new AgentKnowledgeService(mapper);
        assertTrue(service.search("请问退款支付取消订单地址密码怎么处理".repeat(60)).isEmpty());
        verify(mapper).search(argThat(terms -> !terms.isEmpty() && terms.size() <= 24), eq(5));
    }
}
