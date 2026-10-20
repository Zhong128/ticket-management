package org.example.ticketmanagement.trade;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Configuration
public class TradeInfrastructureConfiguration {

    @Bean
    public Clock tradeClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    @ConditionalOnMissingBean(TransactionTemplate.class)
    public TransactionTemplate tradeTransactionTemplate(PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }
}
