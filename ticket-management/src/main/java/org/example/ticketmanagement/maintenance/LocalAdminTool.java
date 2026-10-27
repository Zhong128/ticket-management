package org.example.ticketmanagement.maintenance;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.pojo.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/** 显式本地管理员初始化入口，不注册为Bean，不提权或覆盖现有用户。 */
public final class LocalAdminTool {
    private LocalAdminTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !"create".equals(args[0]) || !args[1].matches("[A-Za-z][A-Za-z0-9_]{2,19}")) {
            throw new IllegalArgumentException("用法：create <全新管理员用户名，3到20位字母数字下划线>");
        }
        String url = System.getenv("SPRING_DATASOURCE_URL");
        validateTarget(url);
        String dbPassword = System.getenv("SPRING_DATASOURCE_PASSWORD");
        if (dbPassword == null || dbPassword.isBlank()) {
            throw new IllegalArgumentException("必须显式设置本地数据库密码");
        }

        // 凭据输出固定在仓库忽略的output目录，拒绝覆盖既有文件。
        Path module = Path.of("").toAbsolutePath().normalize();
        Path root = Files.isDirectory(module.resolve("ticket-management")) ? module : module.getParent();
        if (root == null || !Files.isDirectory(root.resolve("ticket-management"))) {
            throw new IllegalArgumentException("请在仓库根目录或ticket-management目录执行");
        }
        Path output = root.resolve("output/local-admin-" + args[1] + ".txt");
        Files.createDirectories(output.getParent());
        if (Files.exists(output)) {
            throw new IllegalArgumentException("本地凭据文件已存在，拒绝覆盖");
        }
        UnpooledDataSource source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url,
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "root"), dbPassword);
        Configuration configuration = new Configuration(new Environment("local-admin", new JdbcTransactionFactory(), source));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(UserMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(false)) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            if (mapper.getUserByUsername(args[1]) != null) {
                throw new IllegalArgumentException("用户名已存在，拒绝修改现有用户");
            }
            byte[] random = new byte[15];
            new SecureRandom().nextBytes(random);
            String password = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            User user = new User();
            user.setUsername(args[1]);
            user.setPassword(new BCryptPasswordEncoder().encode(password));
            user.setEmail(args[1] + "@youxi.local");
            user.setNickname("本地管理员");
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(user.getCreateTime());
            mapper.insertLocalAdmin(user);
            Files.writeString(output, "username=" + args[1] + "\npassword=" + password + "\nrole=ADMIN\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            session.commit();
        }
        System.out.println("本地管理员已创建；凭据已写入忽略的output文件。");
    }

    public static void validateTarget(String url) {
        if (url == null || !url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):3308/youxi_local(\\?.*)?")) {
            throw new IllegalArgumentException("只允许本机3308端口的youxi_local数据库");
        }
    }
}
