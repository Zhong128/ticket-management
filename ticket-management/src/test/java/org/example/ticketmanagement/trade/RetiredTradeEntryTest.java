package org.example.ticketmanagement.trade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** 防止已迁移的旧交易入口重新进入运行产物。 */
class RetiredTradeEntryTest {

    @Test
    void retiredImplementationsAreAbsentFromRuntime() {
        for (String type : new String[] {
                "core.TradeOperationsImpl", "messaging.TradeEventConsumer",
                "messaging.TradeRecovery", "messaging.LegacyMessageAdapter" }) {
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName("org.example.ticketmanagement.trade." + type), type);
        }
    }
}
