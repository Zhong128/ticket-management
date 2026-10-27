package org.example.ticketmanagement.trade;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.pojo.User;
import org.example.ticketmanagement.service.VerificationCodeService;
import org.example.ticketmanagement.serviceimpl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistrationPasswordTest {

    @Test
    void verifiedRegistrationHashesTheChosenPasswordOnce() {
        UserMapper mapper = mock(UserMapper.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        AuthServiceImpl service = new AuthServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "verificationCodeService", mock(VerificationCodeService.class));
        doAnswer(invocation -> {
            User inserted = invocation.getArgument(0);
            assertTrue(encoder.matches("ChosenPassword42", inserted.getPassword()));
            assertNotEquals("ChosenPassword42", inserted.getPassword());
            inserted.setId(42L);
            return null;
        }).when(mapper).registerUser(any(User.class));
        try (var token = mockStatic(StpUtil.class)) {
            token.when(StpUtil::getSession).thenReturn(new SaSession("test-only"));
            token.when(StpUtil::getTokenValue).thenReturn("test-only");
            assertEquals(42L, service.completeRegistrationWithCode("new@example.invalid", "ChosenPassword42").getId());
            verify(mapper).registerUser(any(User.class));
        }
    }
}
