package org.example.ticketmanagement.trade.messaging;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

/** 仅供隔离性能诊断使用，不进入业务产物。 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = { MappedStatement.class, Object.class }),
        @Signature(type = Executor.class, method = "query",
                args = { MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class })
})
class LoadSqlDiagnostics implements Interceptor {

    private final LongSupplier nanoTime;
    private final ConcurrentHashMap<String, Counters> statements = new ConcurrentHashMap<>();

    LoadSqlDiagnostics(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        if (!statement.getId().startsWith("org.example.ticketmanagement.mapper.")) {
            return invocation.proceed();
        }

        // 只记录具名Mapper耗时及结果计数，不读取SQL参数或改变调用方式。
        Counters counters = statements.computeIfAbsent(statement.getId(), key -> new Counters());
        long started = nanoTime.getAsLong();
        try {
            Object result = invocation.proceed();
            if (result instanceof Integer affected && affected == 0) {
                counters.zeroRows.increment();
            }
            return result;
        } catch (Throwable failure) {
            counters.errors.increment();
            throw failure;
        } finally {
            long elapsed = nanoTime.getAsLong() - started;
            counters.calls.increment();
            counters.nanos.add(elapsed);
            counters.max.accumulate(elapsed);
        }
    }

    List<Timing> snapshot() {
        return statements.entrySet().stream().map(entry -> {
            Counters counters = entry.getValue();
            return new Timing(entry.getKey(), counters.calls.sum(), counters.zeroRows.sum(), counters.errors.sum(),
                    counters.nanos.sum(), counters.max.get());
        }).sorted(Comparator.comparingLong(Timing::nanos).reversed()).toList();
    }

    void reset() {
        statements.clear();
    }

    void write(Path path) throws IOException {
        StringBuilder output = new StringBuilder("statement\tcalls\tzeroRows\terrors\ttotalMillis\tmaxMillis\n");
        for (Timing timing : snapshot()) {
            output.append(timing.statement()).append('\t').append(timing.calls()).append('\t')
                    .append(timing.zeroRows()).append('\t').append(timing.errors()).append('\t')
                    .append(timing.nanos() / 1_000_000.0).append('\t')
                    .append(timing.maxNanos() / 1_000_000.0).append('\n');
        }
        Files.writeString(path, output, StandardCharsets.UTF_8);
    }

    record Timing(String statement, long calls, long zeroRows, long errors, long nanos, long maxNanos) {
    }

    private static class Counters {
        private final LongAdder calls = new LongAdder();
        private final LongAdder zeroRows = new LongAdder();
        private final LongAdder errors = new LongAdder();
        private final LongAdder nanos = new LongAdder();
        private final LongAccumulator max = new LongAccumulator(Long::max, 0);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "load.diagnostics", havingValue = "true")
    static class DiagnosticConfiguration {

        @Bean
        LoadSqlDiagnostics loadSqlDiagnostics() {
            return new LoadSqlDiagnostics(System::nanoTime);
        }
    }
}
