package org.example.ticketmanagement.serviceimpl;

import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.pojo.User;
import org.example.ticketmanagement.service.AvatarService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class AvatarServiceImpl implements AvatarService {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final String URL_PREFIX = "/media/avatars/";
    private static final Pattern MANAGED_NAME = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png");

    private final UserMapper userMapper;
    private final Path directory;

    public AvatarServiceImpl(UserMapper userMapper,
                             @Value("${app.avatar-directory:./uploads/avatars}") String directory) {
        this.userMapper = userMapper;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public String upload(Long userId, MultipartFile file) {
        // 验证账号与图片真实内容，原始文件名和声明的类型不参与路径或格式判断。
        User user = requireActiveUser(userId);
        BufferedImage avatar = readAvatar(file);
        Path target = directory.resolve(userId + "-" + UUID.randomUUID() + ".png");
        try {
            Files.createDirectories(directory);
            ImageIO.write(avatar, "png", target.toFile());
        } catch (IOException e) {
            deleteQuietly(target);
            throw new IllegalStateException("头像保存失败，请稍后重试", e);
        }

        String url = URL_PREFIX + target.getFileName();
        try {
            // 单条SQL自动提交后才清理旧文件，失败时仅补偿本次新文件。
            if (userMapper.updateAvatar(userId, url, LocalDateTime.now()) != 1) {
                throw new IllegalStateException("头像更新失败，请刷新后重试");
            }
        } catch (RuntimeException e) {
            deleteQuietly(target);
            throw e;
        }
        deleteManagedAvatar(userId, user.getAvatar());
        return url;
    }

    @Override
    public String clear(Long userId) {
        User user = requireActiveUser(userId);
        // 空值表示默认头像，重复恢复也只更新当前账号的头像字段。
        if (userMapper.updateAvatar(userId, null, LocalDateTime.now()) != 1) {
            throw new IllegalStateException("头像更新失败，请刷新后重试");
        }
        deleteManagedAvatar(userId, user.getAvatar());
        return null;
    }

    private User requireActiveUser(Long userId) {
        User user = userMapper.getUserById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new IllegalArgumentException("用户不存在或账号已停用");
        }
        return user;
    }

    private BufferedImage readAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择头像图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("头像图片不能超过2MB");
        }
        try (var source = file.getInputStream()) {
            byte[] bytes = source.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new IllegalArgumentException("头像图片不能超过2MB");
            }
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    throw new IllegalArgumentException("头像仅支持有效的JPG或PNG图片");
                }
                ImageReader reader = readers.next();
                try {
                    String format = reader.getFormatName();
                    if (!"PNG".equalsIgnoreCase(format) && !"JPEG".equalsIgnoreCase(format)) {
                        throw new IllegalArgumentException("头像仅支持JPG或PNG图片");
                    }
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    // 解码前限制尺寸和像素，避免小体积图片耗尽内存。
                    if (width < 1 || height < 1 || width > 4096 || height > 4096
                            || (long) width * height > 16_000_000) {
                        throw new IllegalArgumentException("头像尺寸不能超过4096像素且总像素不能超过1600万");
                    }
                    BufferedImage original = reader.read(0);
                    if (original == null) {
                        throw new IllegalArgumentException("头像图片已损坏，请重新选择");
                    }
                    int side = Math.min(width, height);
                    int x = (width - side) / 2;
                    int y = (height - side) / 2;
                    BufferedImage result = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
                    var graphics = result.createGraphics();
                    try {
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        graphics.drawImage(original, 0, 0, 256, 256, x, y, x + side, y + side, null);
                    } finally {
                        graphics.dispose();
                    }
                    return result;
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("头像图片无法读取，请选择有效的JPG或PNG图片", e);
        }
    }

    private void deleteManagedAvatar(Long userId, String url) {
        // 仅删除本人前缀的托管文件，避免资料中的其他用户链接触发误删。
        if (url != null && url.startsWith(URL_PREFIX)) {
            String name = url.substring(URL_PREFIX.length());
            String ownerPrefix = userId + "-";
            if (name.startsWith(ownerPrefix)
                    && MANAGED_NAME.matcher(name.substring(ownerPrefix.length())).matches()) {
                deleteQuietly(directory.resolve(name));
            }
        }
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("头像文件清理失败，文件名: {}", file.getFileName());
        }
    }
}
