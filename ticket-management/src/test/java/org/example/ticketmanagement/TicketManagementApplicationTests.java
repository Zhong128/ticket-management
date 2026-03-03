package org.example.ticketmanagement;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class TicketManagementApplicationTests {
    @Test
    void contextLoads() {
        // 空测试，仅验证Spring上下文能否加载
    }
}
