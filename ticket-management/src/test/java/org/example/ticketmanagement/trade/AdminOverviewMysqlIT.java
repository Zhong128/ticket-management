package org.example.ticketmanagement.trade;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.example.ticketmanagement.dto.AdminRefundQuery;
import org.example.ticketmanagement.dto.AdminSessionQuery;
import org.example.ticketmanagement.dto.AdminUserDTO;
import org.example.ticketmanagement.mapper.AdminOverviewMapper;
import org.example.ticketmanagement.mapper.UserMapper;
import org.example.ticketmanagement.pojo.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class AdminOverviewMysqlIT {
    @Test
    void queriesIncludeHiddenSessionsAndUseExactBusinessBoundariesAndSafeProfileColumns() {
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")) {
            mysql.start();
            UnpooledDataSource source = new UnpooledDataSource(mysql.getDriverClassName(), mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE event(id BIGINT PRIMARY KEY,name VARCHAR(50),artist_name VARCHAR(50),venue VARCHAR(50),status INT,sale_start_time DATETIME,sale_end_time DATETIME,event_end_time DATETIME)");
            jdbc.execute("CREATE TABLE event_session(id BIGINT PRIMARY KEY,event_id BIGINT,session_name VARCHAR(50),session_time DATETIME,status INT,create_time DATETIME)");
            jdbc.execute("CREATE TABLE order_info(id BIGINT PRIMARY KEY,order_no VARCHAR(50),order_status INT)");
            jdbc.execute("CREATE TABLE refund_record(id BIGINT PRIMARY KEY,refund_no VARCHAR(50),order_id BIGINT,status VARCHAR(50),amount DECIMAL(10,2))");
            jdbc.execute("CREATE TABLE user(id BIGINT PRIMARY KEY AUTO_INCREMENT,username VARCHAR(50) UNIQUE,password VARCHAR(100),email VARCHAR(100),phone VARCHAR(50),nickname VARCHAR(50),avatar VARCHAR(100),real_name VARCHAR(50),gender INT,birthday DATE,role VARCHAR(30),status INT,create_time DATETIME,update_time DATETIME)");
            LocalDateTime now = LocalDateTime.of(2026,10,27,12,0);
            jdbc.update("INSERT INTO event VALUES(1,'售卖','艺人','剧院',1,?,?,?),(2,'下架','艺人','剧院',0,?,?,?),(3,'结束售票','艺人','剧院',1,?,?,?)", now,now.plusDays(1),now.plusDays(2),now,now.plusDays(1),now.plusDays(2),now.minusDays(1),now,now.plusDays(2));
            jdbc.update("INSERT INTO event_session VALUES(1,1,'正常',?,1,?),(2,1,'下架',?,0,?),(3,2,'父下架',?,1,?),(4,1,'边界',?,1,?)",now,now,now,now,now,now,now.plusDays(7),now);
            jdbc.execute("INSERT INTO order_info VALUES(1,'ORDER-1',0),(2,'ORDER-2',1)");
            jdbc.execute("INSERT INTO refund_record VALUES(1,'REF-1',1,'MANUAL_REQUIRED',10),(2,'REF-2',2,'SUCCEEDED',20)");
            jdbc.execute("INSERT INTO user(id,username,password,role,status) VALUES(1,'normal','hash','USER',0),(2,'admin','adminhash','SUPER_ADMIN',1)");
            Configuration configuration = new Configuration(new Environment("isolated-admin",new JdbcTransactionFactory(),source));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(AdminOverviewMapper.class);
            configuration.addMapper(UserMapper.class);
            try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(true)) {
                AdminOverviewMapper mapper = session.getMapper(AdminOverviewMapper.class);
                AdminSessionQuery query = new AdminSessionQuery();
                assertEquals(4, mapper.countSessions(query));
                assertEquals(4, mapper.sessions(query).size());
                query.setStatus(0);
                assertEquals(1, mapper.sessions(query).size());
                query.setStatus(null);
                query.setEventId(2L);
                assertEquals("父下架", mapper.sessions(query).getFirst().getSessionName());
                query.setEventStatus(1);
                assertEquals(0, mapper.countSessions(query));
                query.setEventId(null);
                query.setStatus(1);
                query.setAscending(true);
                assertEquals(2, mapper.countSessions(query));
                assertEquals(1L, mapper.sessions(query).getFirst().getId());
                query.setAscending(false);
                assertEquals(4L, mapper.sessions(query).getFirst().getId());

                var summary = mapper.summary(now,now.plusDays(7));
                assertEquals(1,summary.getOnSaleEventCount());
                assertEquals(1,summary.getUpcomingSessionCount());
                assertEquals(1,summary.getPendingOrderCount());
                assertEquals(1,summary.getRefundAttentionCount());
                AdminRefundQuery refunds = new AdminRefundQuery();
                refunds.setOrderNo("ORDER-1");
                assertEquals(1,mapper.countRefunds(refunds));
                assertEquals("REF-1",mapper.refunds(refunds).getFirst().get("refund_no"));
                refunds.setOrderNo("' OR 1=1 --");
                assertEquals(0,mapper.countRefunds(refunds));
                AdminUserDTO profile = new AdminUserDTO();
                profile.setNickname("新昵称");
                UserMapper users = session.getMapper(UserMapper.class);
                assertEquals(1,users.updateAdminProfile(1L,profile,now));
                assertEquals(0,users.updateAdminProfile(2L,profile,now));
                assertEquals(0,users.updateOrdinaryUserStatus(2L,0,now));
                assertEquals("hash",jdbc.queryForObject("SELECT password FROM user WHERE id=1",String.class));
                assertEquals(0,jdbc.queryForObject("SELECT status FROM user WHERE id=1",Integer.class));
                assertEquals("USER",jdbc.queryForObject("SELECT role FROM user WHERE id=1",String.class));
                // 模拟改密校验后的旧快照；管理员已经将账户禁用并修改昵称。
                jdbc.update("UPDATE user SET status=1 WHERE id=1");
                session.clearCache();
                User staleSnapshot = users.getUserById(1L);
                assertEquals(1, staleSnapshot.getStatus());
                jdbc.update("UPDATE user SET status=0, nickname='管理员已修改' WHERE id=1");
                String changedPassword = new BCryptPasswordEncoder().encode("newPassword123");
                assertEquals(1, users.updatePassword(staleSnapshot.getId(), changedPassword, now));
                assertEquals(0, jdbc.queryForObject("SELECT status FROM user WHERE id=1", Integer.class));
                assertEquals("管理员已修改", jdbc.queryForObject("SELECT nickname FROM user WHERE id=1", String.class));
                assertEquals("USER", users.getUserById(1L).getRole());
                assertTrue(new BCryptPasswordEncoder().matches("newPassword123", users.getUserById(1L).getPassword()));

                var newAdmin = new User();
                newAdmin.setUsername("fresh_admin");
                newAdmin.setPassword(new BCryptPasswordEncoder().encode("randomPassword123"));
                newAdmin.setEmail("fresh_admin@youxi.local");
                newAdmin.setCreateTime(now);
                newAdmin.setUpdateTime(now);
                assertEquals(1, users.insertLocalAdmin(newAdmin));
                assertNotNull(newAdmin.getId());
                assertEquals("ADMIN", users.getUserById(newAdmin.getId()).getRole());
                assertTrue(new BCryptPasswordEncoder().matches("randomPassword123", users.getUserById(newAdmin.getId()).getPassword()));
                assertThrows(Exception.class, () -> users.insertLocalAdmin(newAdmin));

            }
        }
    }
}
