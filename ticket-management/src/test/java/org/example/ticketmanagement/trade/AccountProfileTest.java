package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.controller.UserController;
import org.example.ticketmanagement.dto.UserAddressDTO;
import org.example.ticketmanagement.dto.UserProfileDTO;
import org.example.ticketmanagement.mapper.UserAddressMapper;
import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.pojo.User;
import org.example.ticketmanagement.pojo.UserAddress;
import org.example.ticketmanagement.serviceimpl.UserAddressServiceImpl;
import org.example.ticketmanagement.serviceimpl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import cn.dev33.satoken.stp.StpUtil;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountProfileTest {

    @Test
    void updatingProfileKeepsAccountStatusAndOtherPrivateFields() {
        UserMapper mapper = mock(UserMapper.class);
        UserServiceImpl service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        User original = new User();
        original.setId(71L);
        original.setUsername("viewer");
        original.setEmail("viewer@example.invalid");
        original.setPassword("existing-hash");
        original.setStatus(1);
        original.setRole("USER");
        original.setIdCard("existing-private-value");
        when(mapper.getUserById(71L)).thenReturn(original);
        when(mapper.updateProfile(eq(71L), any(UserProfileDTO.class), any())).thenReturn(1);
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", service);
        UserProfileDTO profile = new UserProfileDTO();
        profile.setNickname("现场常客");
        try (var token = mockStatic(StpUtil.class)) {
            token.when(StpUtil::getLoginIdAsLong).thenReturn(71L);
            assertEquals(1, controller.updateProfile(profile).getCode());
        }
        verify(mapper).updateProfile(eq(71L), same(profile), any());
        verify(mapper, never()).updateUser(any(User.class));
        assertEquals(1, original.getStatus());
        assertEquals("viewer@example.invalid", original.getEmail());
        assertEquals("existing-private-value", original.getIdCard());
    }

    @Test
    void profileUpdateNeverWritesTheFullAccountSnapshot() {
        UserMapper mapper = mock(UserMapper.class);
        UserServiceImpl service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        User current = new User();
        current.setId(71L);
        current.setStatus(1);
        current.setPassword("old-password-hash");
        when(mapper.getUserById(71L)).thenReturn(current);
        when(mapper.updateProfile(eq(71L), any(UserProfileDTO.class), any())).thenReturn(1);
        UserProfileDTO input = new UserProfileDTO();
        input.setNickname("只改资料");
        assertTrue(service.updateProfile(71L, input));
        verify(mapper, never()).updateUser(any(User.class));
    }

    @Test
    void passwordChangeRejectsValuesThatCannotBeUsedByLogin() {
        UserMapper mapper = mock(UserMapper.class);
        UserServiceImpl service = new UserServiceImpl();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        User user = new User();
        user.setId(71L);
        user.setPassword(encoder.encode("old-pass"));
        when(mapper.getUserById(71L)).thenReturn(user);
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(71L, "old-pass", "short"));
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(71L, "old-pass", "x".repeat(21)));
        verify(mapper, never()).updateUser(any(User.class));
    }

    @Test
    void editingDefaultAddressKeepsDefaultFlag() {
        UserAddressMapper mapper = mock(UserAddressMapper.class);
        UserAddressServiceImpl service = new UserAddressServiceImpl();
        ReflectionTestUtils.setField(service, "userAddressMapper", mapper);
        UserAddress original = new UserAddress();
        original.setId(81L);
        original.setUserId(71L);
        original.setIsDefault(1);
        when(mapper.getUserAddressById(81L)).thenReturn(original);
        UserAddressDTO input = new UserAddressDTO();
        input.setReceiverName("小林");
        input.setReceiverPhone("13800138000");
        input.setProvince("北京市");
        input.setCity("北京市");
        input.setDetailAddress("现场路1号");
        assertTrue(service.updateUserAddressById(81L, input));
        ArgumentCaptor<UserAddress> saved = ArgumentCaptor.forClass(UserAddress.class);
        verify(mapper).updateUserAddressById(saved.capture());
        assertEquals(1, saved.getValue().getIsDefault());
        assertEquals(71L, saved.getValue().getUserId());
    }
}
