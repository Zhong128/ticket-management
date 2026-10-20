package org.example.ticketmanagement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_CONTEXT_TEST", matches = "true")
class TicketManagementApplicationTests {

    @Test
    void contextLoads() {
        // 空测试，仅验证Spring上下文能否加载
    }
}
