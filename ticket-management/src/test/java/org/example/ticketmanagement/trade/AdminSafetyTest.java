package org.example.ticketmanagement.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ticketmanagement.dto.AdminUserDTO;
import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.pojo.User;
import org.example.ticketmanagement.serviceimpl.StpInterfaceImpl;
import org.example.ticketmanagement.serviceimpl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminSafetyTest {
    @Test
    void superAdminInheritsAdminAndUser() {
        UserMapper mapper = mock(UserMapper.class);
        User user = user("SUPER_ADMIN");
        when(mapper.getUserById(1L)).thenReturn(user);
        StpInterfaceImpl roles = new StpInterfaceImpl();
        ReflectionTestUtils.setField(roles, "userMapper", mapper);
        assertTrue(roles.getRoleList(1L, "login").containsAll(List.of("SUPER_ADMIN", "ADMIN", "USER")));
    }

    @Test
    void adminEditRejectsSensitiveFieldsEvenWhenJacksonIgnoresUnknownFields() {
        ObjectMapper json = new ObjectMapper().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertThrows(Exception.class, () -> json.readValue("{\"role\":\"ADMIN\"}", AdminUserDTO.class));
        assertThrows(Exception.class, () -> json.readValue("{\"password\":\"secret123\"}", AdminUserDTO.class));
        assertThrows(Exception.class, () -> json.readValue("{\"status\":0}", AdminUserDTO.class));
    }

    @Test
    void batchStatusValidatesEveryUserBeforeAnyWrite() {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.getUserById(1L)).thenReturn(user("USER"));
        when(mapper.getUserById(2L)).thenReturn(user("SUPER_ADMIN"));
        UserServiceImpl service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        assertThrows(RuntimeException.class, () -> service.updateUsersStatus(List.of(1L, 2L), 1));
        verify(mapper, never()).updateUser(any());
    }

    @Test
    void superAdminCannotBeDeletedOrEnabled() {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.getUserById(1L)).thenReturn(user("SUPER_ADMIN"));
        UserServiceImpl service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        assertThrows(RuntimeException.class, () -> service.deleteUserById(1L));
        assertThrows(RuntimeException.class, () -> service.updateUserStatus(1L, 1));
        verify(mapper, never()).deleteUserById(any());
        verify(mapper, never()).updateUser(any());
    }

    @Test
    void adminProfileRejectsProtectedAccountsAndAllowsPasswordFreePartialProfile() throws Exception {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.getUserById(1L)).thenReturn(user("SUPER_ADMIN"));
        UserServiceImpl service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        AdminUserDTO profile = new ObjectMapper().readValue("{\"nickname\":\"新昵称\"}", AdminUserDTO.class);
        assertThrows(IllegalArgumentException.class, () -> service.updateAdminProfile(1L, profile));
        verify(mapper, never()).updateAdminProfile(any(), any(), any());
        when(mapper.getUserById(1L)).thenReturn(user("USER"));
        when(mapper.updateAdminProfile(eq(1L), eq(profile), any())).thenReturn(1);
        assertTrue(service.updateAdminProfile(1L, profile));
        verify(mapper, never()).updateUser(any());
    }

    @Test
    void localAdminToolRejectsOtherDatabasesBeforeConnecting() {
        org.example.ticketmanagement.maintenance.LocalAdminTool.validateTarget("jdbc:mysql://127.0.0.1:3308/youxi_local?useSSL=false");
        for (String target : List.of("jdbc:mysql://127.0.0.1:3306/youxi_local", "jdbc:mysql://127.0.0.1:3308/ticket", "jdbc:mysql://remote:3308/youxi_local", "jdbc:mysql://localhost:3308/youxi_local_other")) {
            assertThrows(IllegalArgumentException.class, () -> org.example.ticketmanagement.maintenance.LocalAdminTool.validateTarget(target));
        }
    }

    @Test
    void changingPasswordNeverWritesAccountStatusOrRoleFromSnapshot() {
        UserMapper mapper = mock(UserMapper.class);
        User snapshot = user("USER");
        var encoder = new BCryptPasswordEncoder();
        snapshot.setPassword(encoder.encode("oldPassword123"));
        when(mapper.getUserById(1L)).thenReturn(snapshot);
        UserServiceImpl service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        when(mapper.updatePassword(eq(1L), anyString(), any())).thenAnswer(invocation -> {
            String encoded = invocation.getArgument(1);
            assertTrue(encoder.matches("newPassword123", encoded));
            return 1;
        });
        assertTrue(service.changePassword(1L, "oldPassword123", "newPassword123"));
        verify(mapper, never()).updateUser(any());
        verify(mapper, never()).updateOrdinaryUserStatus(any(), any(), any());
        assertEquals(1, snapshot.getStatus());
        assertEquals("USER", snapshot.getRole());
    }

    private User user(String role) {
        User user = new User();
        user.setId(1L);
        user.setRole(role);
        user.setStatus(1);
        return user;
    }
}
