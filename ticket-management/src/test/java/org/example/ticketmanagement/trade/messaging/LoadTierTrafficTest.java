package org.example.ticketmanagement.trade.messaging;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LoadTierTrafficTest {
    @Test
    void hotAndNormalTiersReachBothNodesWithoutParityBias() {
        Map<String, Integer> routes = new HashMap<>();
        for (int buyer = 0; buyer < 100; buyer++) {
            int tier = LoadTierTraffic.targetTier(4001, buyer);
            routes.merge(tier + ":" + buyer % 2, 1, Integer::sum);
        }
        assertEquals(Map.of("4001:0", 40, "4001:1", 40,
                "4002:0", 5, "4002:1", 5, "4003:0", 5, "4003:1", 5), routes);
    }
}
