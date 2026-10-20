package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.mapper.TradeSchemaMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Prevent activation against an unmigrated database; never executes DDL.
 */
@Component
public class TradeSchemaGuard implements ApplicationRunner {

    private final TradeSchemaMapper mapper;
    private final boolean enabled;

    public TradeSchemaGuard(TradeSchemaMapper mapper, @Value("${trade.enabled:false}") boolean enabled) {
        this.mapper = mapper;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!enabled) {
            return;
        }
        try {
            mapper.checkReservations();
            mapper.checkPurchaseGuards();
            mapper.checkOutbox();
            mapper.checkRefunds();
            mapper.checkOrderColumns();
            mapper.checkStockColumns();
            mapper.checkCompensationColumns();
            if (mapper.countVerifiedSchemaVersion() != 1) {
                throw new IllegalStateException("Missing verified schema version 3");
            }
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Trade activation refused: apply and verify migrations V1-V3 on a database copy first", exception);
        }
    }
}
