package org.example.ticketmanagement.trade;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TradeCoreContractTest {

    @Test
    void durableReservationExposesStateMachineInsteadOfRedisOnlyAcceptance() throws Exception {
        Class<?> operations = Class.forName("org.example.ticketmanagement.service.ReservationService");
        assertTrue(operations.isInterface());
        assertEquals(Map.class,
                operations.getMethod("reserve", long.class, long.class, int.class, String.class).getReturnType());
        assertEquals(boolean.class, operations.getMethod("applyPaid", long.class, long.class).getReturnType());
        assertEquals(boolean.class, operations.getMethod("beginCancel", long.class, Long.class).getReturnType());
    }
}
