package org.example.ticketmanagement.controller;

import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.pojo.Result;
import org.example.ticketmanagement.pojo.User;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/public/test")
@Tag(name = "并发测试用户生成器", description = "并发测试")
@RequiredArgsConstructor
public class TestDataController {

    private final UserMapper userMapper;

    /**
     * 生成测试用户和token，并返回CSV内容（不保存文件）
     * 适用于少量用户的测试场景
     */
    @PostMapping("/generate-test-users")
    public Result<String> generateTestUsers(@RequestParam int count) {
        log.info("开始生成 {} 个测试用户", count);

        StringBuilder csv = new StringBuilder();
        csv.append("userId,token\n");

        List<Long> createdUserIds = new ArrayList<>();

        try {
            for (int i = 1; i <= count; i++) {
                // 1. 创建测试用户
                User user = new User();
                user.setUsername("test" + System.currentTimeMillis() + i);
                user.setEmail("test" + i + "@test.com");
                user.setPassword("123456");
                user.setStatus(1);
                user.setCreateTime(LocalDateTime.now());

                userMapper.registerUser(user);  // 插入后会自动回填 ID
                createdUserIds.add(user.getId());

                // 2. 生成 token
                StpUtil.login(user.getId());
                String token = StpUtil.getTokenValue();

                // 3. 添加到 CSV
                csv.append(user.getId()).append(",").append(token).append("\n");

                // 4. 退出登录（清除 session）
                StpUtil.logout(user.getId());

                log.debug("已生成用户: id={}, token={}", user.getId(), token.substring(0, 20) + "...");
            }

            log.info("成功生成 {} 个测试用户", count);

            // 直接返回CSV内容
            return Result.success("生成成功，请复制以下内容保存为CSV文件", csv.toString());

        } catch (Exception e) {
            log.error("生成测试用户失败", e);
            // 如果有部分用户已创建，尝试清理
            for (Long userId : createdUserIds) {
                try {
                    userMapper.deleteUserById(userId);
                } catch (Exception ex) {
                    log.error("清理用户失败: {}", userId);
                }
            }
            return Result.error("生成失败：" + e.getMessage());
        }
    }

    /**
     * 生成测试用户并保存到指定路径的文件
     */
    @PostMapping("/generate-test-users-to-file")
    public Result<String> generateTestUsersToFile(
            @RequestParam int count,
            @RequestParam(defaultValue = "./test-users.csv") String filePath) {
        // 自动多生成一个用户作为缓冲
        int actualCount = count + 1;

        log.info("生成 {} 个测试用户（包含1个缓冲），保存到文件: {}", actualCount, filePath);

        File file = new File(filePath);
        // 确保目录存在
        file.getParentFile().mkdirs();

        List<Long> createdUserIds = new ArrayList<>();

        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("userId,token");

            for (int i = 1; i <= actualCount; i++) {
                // 1. 创建测试用户
                User user = new User();
                user.setUsername("test" + System.currentTimeMillis() + i);
                user.setEmail("test" + i + "@test.com");
                user.setPassword("123456");
                user.setStatus(1);
                user.setCreateTime(LocalDateTime.now());

                userMapper.registerUser(user);
                createdUserIds.add(user.getId());

                // 2. 生成 token
                StpUtil.login(user.getId());
                String token = StpUtil.getTokenValue();

                // 3. 写入文件
                writer.println(user.getId() + "," + token);


                log.debug("已生成用户: id={}", user.getId());
            }

            writer.flush();

            // 返回文件的绝对路径
            String absolutePath = file.getAbsolutePath();
            log.info("测试用户数据已保存到: {}", absolutePath);

            return Result.success("生成成功，文件保存在：" + absolutePath,
                    "文件路径: " + absolutePath + "\n" +
                            "文件内容示例:\n" +
                            "userId,token\n" +
                            "1,eyJhbGciOiJIUzI1NiIs...\n" +
                            "2,eyJhbGciOiJIUzI1NiIs...");

        } catch (Exception e) {
            log.error("生成测试用户失败", e);
            // 清理已创建的用户
            for (Long userId : createdUserIds) {
                try {
                    userMapper.deleteUserById(userId);
                } catch (Exception ex) {
                    log.error("清理用户失败: {}", userId);
                }
            }
            return Result.error("生成失败：" + e.getMessage());
        }
    }

    /**
     * 清理测试用户
     */
    @DeleteMapping("/clean-test-users")
    public Result<String> cleanTestUsers() {
        log.info("开始清理测试用户");

        try {
            // 删除用户名为 test 开头的用户
            int count = userMapper.deleteTestUsers();
            log.info("已清理 {} 个测试用户", count);

            return Result.success("清理成功，共删除 " + count + " 个测试用户");

        } catch (Exception e) {
            log.error("清理测试用户失败", e);
            return Result.error("清理失败：" + e.getMessage());
        }
    }
}