package org.example.ticketmanagement.serviceimpl;

import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.pojo.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AvatarServiceImplTest {
    @TempDir
    Path directory;
    UserMapper mapper;
    AvatarServiceImpl service;
    User user;

    @BeforeEach
    void setUp() {
        mapper = mock(UserMapper.class);
        service = new AvatarServiceImpl(mapper, directory.toString());
        user = new User();
        user.setId(7L);
        user.setStatus(1);
        when(mapper.getUserById(7L)).thenReturn(user);
        when(mapper.updateAvatar(eq(7L), nullable(String.class), any())).thenReturn(1);
    }

    @Test
    void convertsPngAndJpegToCentered256PixelPng() throws Exception {
        for (String format : new String[]{"png", "jpg"}) {
            String url = service.upload(7L, image(format, 600, 300));
            assertTrue(url.matches("/media/avatars/7-[0-9a-f-]{36}\\.png"));
            BufferedImage saved = ImageIO.read(directory.resolve(url.substring(url.lastIndexOf('/') + 1)).toFile());
            assertEquals(256, saved.getWidth());
            assertEquals(256, saved.getHeight());
            assertTrue(new Color(saved.getRGB(5, 128)).getRed() >= 250);
            assertTrue(new Color(saved.getRGB(250, 128)).getRed() >= 250);
            Color center = new Color(saved.getRGB(128, 128));
            assertTrue(center.getRed() >= 250 && center.getBlue() <= 5 && center.getGreen() <= 5);
        }
    }

    @Test
    void rejectsForgedEmptyOversizeAndOversizedDimensionsWithoutWriting() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.upload(7L,
                new MockMultipartFile("file", "fake.png", "image/png", "<script>".getBytes())));
        assertThrows(IllegalArgumentException.class, () -> service.upload(7L,
                new MockMultipartFile("file", new byte[0])));
        assertThrows(IllegalArgumentException.class, () -> service.upload(7L,
                new MockMultipartFile("file", new byte[2 * 1024 * 1024 + 1])));
        assertThrows(IllegalArgumentException.class, () -> service.upload(7L, image("png", 4097, 1)));
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void preservesOldAvatarAndRemovesNewFileWhenUpdateFails() throws Exception {
        Path old = oldAvatar();
        when(mapper.updateAvatar(eq(7L), anyString(), any())).thenThrow(new IllegalStateException("database"));
        assertThrows(IllegalStateException.class, () -> service.upload(7L, image("png", 16, 16)));
        assertTrue(Files.exists(old));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void clearsAvatarAndDeletesOnlyStrictlyManagedOldPath() throws Exception {
        Path old = oldAvatar();
        assertNull(service.clear(7L));
        assertFalse(Files.exists(old));
        user.setAvatar(null);
        assertNull(service.clear(7L));
        Path unrelated = Files.writeString(directory.resolve("keep.txt"), "keep");
        user.setAvatar("/media/avatars/keep.txt");
        service.clear(7L);
        assertTrue(Files.exists(unrelated));
        user.setAvatar("/media/avatars/../keep.txt");
        service.clear(7L);
        assertTrue(Files.exists(unrelated));
    }

    @Test
    void removesOldFileOnlyAfterSuccessfulUpdate() throws Exception {
        Path old = oldAvatar();
        when(mapper.updateAvatar(eq(7L), anyString(), any())).thenAnswer(call -> {
            assertTrue(Files.exists(old));
            return 1;
        });
        service.upload(7L, image("png", 30, 20));
        assertFalse(Files.exists(old));
    }

    @Test
    void rejectsDisabledUserAndZeroRowUpdate() throws Exception {
        user.setStatus(0);
        assertThrows(IllegalArgumentException.class, () -> service.upload(7L, image("png", 16, 16)));
        user.setStatus(1);
        when(mapper.updateAvatar(eq(7L), anyString(), any())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.upload(7L, image("png", 16, 16)));
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void clearFailureKeepsOldFile() throws Exception {
        Path old = oldAvatar();
        when(mapper.updateAvatar(eq(7L), isNull(), any())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.clear(7L));
        assertTrue(Files.exists(old));
    }

    @Test
    void keepsOtherUsersAvatarEvenWhenItWasAssignedToCurrentProfile() throws Exception {
        String name = "8-" + UUID.randomUUID() + ".png";
        Path otherAvatar = Files.writeString(directory.resolve(name), "other");
        user.setAvatar("/media/avatars/" + name);
        service.upload(7L, image("png", 16, 16));
        assertTrue(Files.exists(otherAvatar));
        user.setAvatar("/media/avatars/" + name);
        service.clear(7L);
        assertTrue(Files.exists(otherAvatar));
    }

    private Path oldAvatar() throws Exception {
        String name = "7-" + UUID.randomUUID() + ".png";
        user.setAvatar("/media/avatars/" + name);
        return Files.writeString(directory.resolve(name), "old");
    }

    private MockMultipartFile image(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.RED);
        graphics.fillRect(width / 4, 0, width / 2, height);
        graphics.dispose();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, format, bytes);
        return new MockMultipartFile("file", "untrusted.exe", "application/octet-stream", bytes.toByteArray());
    }
}
