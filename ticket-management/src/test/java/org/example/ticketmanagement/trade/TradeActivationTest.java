package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.mapper.TradeSchemaMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeActivationTest {

    @Test
    void disabledTradingDoesNotReadOrMigrateDatabase() {
        var jdbc = mock(TradeSchemaMapper.class);
        new TradeSchemaGuard(jdbc, false).run(new DefaultApplicationArguments());
        verifyNoInteractions(jdbc);
    }

    @Test
    void enabledTradingRefusesUnmigratedDatabase() {
        var jdbc = mock(TradeSchemaMapper.class);
        when(jdbc.checkReservations()).thenThrow(new IllegalStateException("missing schema"));
        assertThrows(IllegalStateException.class,
                () -> new TradeSchemaGuard(jdbc, true).run(new DefaultApplicationArguments()));
    }
}
