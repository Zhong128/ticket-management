package org.example.ticketmanagement.trade;

import org.flywaydb.core.Flyway;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

/** 每用例独立数据库；容器不挂载主机目录，由Testcontainers资源回收器退出清理。 */
final class IsolatedProtocolDatabase {
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    static final StringRedisTemplate redis;

    static {
        MYSQL.start();
        REDIS.start();
        LettuceConnectionFactory connection = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        connection.afterPropertiesSet();
        connection.start();
        redis = new StringRedisTemplate(connection);
        Runtime.getRuntime().addShutdownHook(new Thread(connection::destroy));
    }

    static DriverManagerDataSource freshDatabase() {
        String schema = "protocol_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource admin = new DriverManagerDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
        new JdbcTemplate(admin).execute("CREATE DATABASE `" + schema + "`");
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:mysql://" + MYSQL.getHost()
                + ":" + MYSQL.getMappedPort(3306) + "/" + schema
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", "root", MYSQL.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        // 每个fixture单独Redis逻辑库，避免旧token污染下一个用例。
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        return dataSource;
    }
}
