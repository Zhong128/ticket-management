package org.example.ticketmanagement.trade;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.example.ticketmanagement.mapper.EventMapper;
import org.example.ticketmanagement.dto.EventQueryDTO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 新建隔离 MySQL 验证真实 Mapper，不访问项目原数据库。 */
class PublicBrowseMysqlIT {

    @Test
    void minimumPriceIgnoresDisabledAndMismatchedSessionsAndKeepsSoldOutTiers() {
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")) {
            mysql.start();
            UnpooledDataSource source = new UnpooledDataSource(mysql.getDriverClassName(), mysql.getJdbcUrl(),
                    mysql.getUsername(), mysql.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE event_session(id BIGINT PRIMARY KEY, event_id BIGINT, status INT)");
            jdbc.execute("CREATE TABLE ticket_tier(id BIGINT PRIMARY KEY, event_id BIGINT, session_id BIGINT, current_price DECIMAL(10,2), status INT, available_stock INT)");
            jdbc.execute("INSERT INTO event_session VALUES(1,10,1),(2,10,0),(3,20,1)");
            jdbc.execute("INSERT INTO ticket_tier VALUES(1,10,1,280,1,0),(2,10,1,80,0,10),(3,10,2,50,1,10),(4,10,3,10,1,10),(5,20,3,480,1,10)");
            jdbc.execute("CREATE TABLE event(id BIGINT PRIMARY KEY, name VARCHAR(100), artist_name VARCHAR(100), status INT, event_start_time DATETIME, create_time DATETIME)");
            jdbc.update("INSERT INTO event VALUES(1,?, ?,1,NOW(),NOW()),(2,?, ?,1,NOW(),NOW()),(3,?, ?,0,NOW(),NOW())",
                    "演唱会", "孙燕姿", "孙燕姿现场", "", "草稿", "孙燕姿");
            Configuration config = new Configuration(new Environment("isolated-browse", new JdbcTransactionFactory(), source));
            config.setMapUnderscoreToCamelCase(true);
            config.addMapper(EventMapper.class);
            try (SqlSession session = new SqlSessionFactoryBuilder().build(config).openSession()) {
                var prices = session.getMapper(EventMapper.class).selectMinPrices(List.of(10L, 20L, 30L));
                assertEquals(2, prices.size());
                assertEquals(new BigDecimal("280.00"), prices.stream().filter(price -> price.getEventId() == 10L).findFirst().orElseThrow().getMinPrice());
                assertEquals(new BigDecimal("480.00"), prices.stream().filter(price -> price.getEventId() == 20L).findFirst().orElseThrow().getMinPrice());
                EventQueryDTO query = new EventQueryDTO();
                query.setKeyword("孙燕姿");
                query.setStatus(1);
                assertEquals(2, session.getMapper(EventMapper.class).countByCondition(query));
                assertEquals(2, session.getMapper(EventMapper.class).selectByCondition(query).size());
            }
        }
    }
}
