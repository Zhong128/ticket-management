package org.example.ticketmanagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
public class AgentRuntimeConfig {

    @Bean(destroyMethod = "dispose")
    public Scheduler agentModelScheduler() {
        // 与订单/邮件线程池隔离，限制阻塞模型请求线程与排队数量。
        return Schedulers.newBoundedElastic(4, 32, "agent-model");
    }
}
