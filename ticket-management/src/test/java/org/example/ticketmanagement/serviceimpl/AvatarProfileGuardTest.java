package org.example.ticketmanagement.serviceimpl;

import org.example.ticketmanagement.dto.UserProfileDTO;
import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AvatarProfileGuardTest {
    @Test
    void profileCannotBypassAvatarUploadValidation() {
        UserServiceImpl service = new UserServiceImpl();
        UserMapper mapper = mock(UserMapper.class);
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        UserProfileDTO profile = new UserProfileDTO();
        profile.setAvatar("https://untrusted.example/image.svg");
        assertThrows(IllegalArgumentException.class, () -> service.updateProfile(7L, profile));
        verifyNoInteractions(mapper);
    }

    @Test
    void ordinaryProfileEditKeepsExistingAvatar() {
        UserServiceImpl service = new UserServiceImpl();
        UserMapper mapper = mock(UserMapper.class);
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        User user = new User();
        user.setId(7L);
        user.setAvatar("/media/avatars/old.png");
        when(mapper.getUserById(7L)).thenReturn(user);
        when(mapper.updateProfile(eq(7L), any(), any())).thenReturn(1);
        UserProfileDTO profile = new UserProfileDTO();
        profile.setNickname("新昵称");
        assertTrue(service.updateProfile(7L, profile));
        verify(mapper, never()).updateAvatar(any(), any(), any());
    }
}
