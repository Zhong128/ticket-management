package org.example.ticketmanagement.trade.messaging;

import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoadSqlDiagnosticsTest {

    @Test
    void recordsVersionMissWithoutChangingAffectedRows() throws Throwable {
        Executor executor = mock(Executor.class);
        MappedStatement statement = statement();
        Map<String, Object> parameters = Map.of("version", 12);
        when(executor.update(statement, parameters)).thenReturn(0);
        LoadSqlDiagnostics diagnostics = new LoadSqlDiagnostics(new AtomicLong()::getAndIncrement);

        // 采集器必须保留CAS零影响行语义，不得自行重试SQL。
        assertEquals(0, diagnostics.intercept(invocation(executor, statement, parameters)));
        verify(executor).update(statement, parameters);
        assertEquals(1, diagnostics.snapshot().size());
        assertEquals(1, diagnostics.snapshot().getFirst().calls());
        assertEquals(1, diagnostics.snapshot().getFirst().zeroRows());
        assertEquals(0, diagnostics.snapshot().getFirst().errors());
        assertEquals(1, diagnostics.snapshot().getFirst().nanos());
    }

    @Test
    void recordsFailureAndPropagatesOriginalDatabaseException() throws Exception {
        Executor executor = mock(Executor.class);
        MappedStatement statement = statement();
        Map<String, Object> parameters = Map.of("version", 12);
        IllegalStateException failure = new IllegalStateException("isolated database failure");
        when(executor.update(statement, parameters)).thenThrow(failure);
        LoadSqlDiagnostics diagnostics = new LoadSqlDiagnostics(new AtomicLong()::getAndIncrement);

        // 记录失败仍传播原异常，不能吞异常或改变事务回滚行为。
        InvocationTargetException propagated = assertThrows(InvocationTargetException.class,
                () -> diagnostics.intercept(invocation(executor, statement, parameters)));
        assertSame(failure, propagated.getCause());
        assertEquals(1, diagnostics.snapshot().size());
        assertEquals(1, diagnostics.snapshot().getFirst().calls());
        assertEquals(1, diagnostics.snapshot().getFirst().errors());
    }

    private MappedStatement statement() {
        Configuration configuration = new Configuration();
        return new MappedStatement.Builder(configuration,
                "org.example.ticketmanagement.mapper.SeckillAdmissionMapper.deduct",
                new StaticSqlSource(configuration, "UPDATE isolated_diagnostic SET version=version+1"),
                SqlCommandType.UPDATE).build();
    }

    private Invocation invocation(Executor executor, MappedStatement statement, Object parameters) throws Exception {
        return new Invocation(executor, Executor.class.getMethod("update", MappedStatement.class, Object.class),
                new Object[] { statement, parameters });
    }
}
