package org.example.ticketmanagement.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ticketmanagement.mapper.*;
import org.example.ticketmanagement.service.SeckillLockService;
import org.example.ticketmanagement.serviceimpl.ReservationServiceImpl;
import org.example.ticketmanagement.serviceimpl.SeckillServiceImpl;
import org.example.ticketmanagement.serviceimpl.StockServiceImpl;
import org.example.ticketmanagement.trade.messaging.OutboxStore;
import org.example.ticketmanagement.trade.messaging.StockProjection;
import org.example.ticketmanagement.util.LuaScriptLoader;
import org.example.ticketmanagement.util.OrderNoGenerator;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 复用真实Mapper、事务与Redis脚本；分段锁由HTTP及双应用测试单独覆盖。 */
public record ReservationFixture(ReservationServiceImpl core, StockServiceImpl stock) {

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static ReservationFixture create(SqlSessionTemplate sessions, TransactionTemplate transactions,
            OutboxStore outbox, StringRedisTemplate redis, Clock clock) {
        LuaScriptLoader scripts = new LuaScriptLoader();
        scripts.init();
        StockServiceImpl stock = new StockServiceImpl(sessions.getMapper(TradeStockMapper.class),
                new StockProjection(redis, scripts), sessions.getMapper(SeckillAdmissionMapper.class),
                redis, scripts, new ObjectMapper(), transactions, clock);
        ReflectionTestUtils.setField(stock, "enabled", true);
        ObjectProvider<SeckillServiceImpl> provider = mock(ObjectProvider.class);
        ReservationServiceImpl core = new ReservationServiceImpl(sessions.getMapper(TradeCoreMapper.class),
                sessions.getMapper(SeckillAdmissionMapper.class), transactions, outbox, stock,
                new OrderNoGenerator(), provider, clock);
        ReflectionTestUtils.setField(core, "enabled", true);

        SeckillLockService locks = mock(SeckillLockService.class);
        when(locks.executeWithSegmentLock(anyLong(), anyLong(), anyInt(), any()))
                .thenAnswer(call -> ((Supplier) call.getArgument(3)).get());
        SeckillServiceImpl seckill = new SeckillServiceImpl(core, stock, locks,
                sessions.getMapper(TicketTierMapper.class), sessions.getMapper(EventSessionMapper.class),
                sessions.getMapper(EventMapper.class), clock);
        ReflectionTestUtils.setField(seckill, "enabled", true);
        ReflectionTestUtils.setField(seckill, "segments", 20);
        when(provider.getObject()).thenReturn(seckill);
        return new ReservationFixture(core, stock);
    }
}
