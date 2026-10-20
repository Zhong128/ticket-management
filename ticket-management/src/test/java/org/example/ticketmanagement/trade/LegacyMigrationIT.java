package org.example.ticketmanagement.trade;

import org.example.ticketmanagement.mapper.TradeSchemaMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Only a disposable container is modified. Exercises an existing V1 database with history.
 */
@Testcontainers
class LegacyMigrationIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Test
    void upgradePreservesFinancialHistoryAndBackfillsOnlySuccessfulPayment() {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var db = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).target("1").cleanDisabled(true).load().migrate();
        for (int i = 1; i <= 4; i++) {
            db.update("INSERT INTO"
                    + " order_info(id,order_no,user_id,event_id,session_id,tier_id,quantity,total_amount,order_status,create_time)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?)", i, "LEGACY" + i, 1, 1, 1, 1, 2, 200, i == 3 ? 2 : i == 4 ? 1 : 0,
                    "2026-10-01 10:00:00");
        }
        db.update("INSERT INTO"
                + " payment_record(id,order_id,order_no,user_id,payment_no,total_amount,pay_status,expire_time)"
                + " VALUES(1,1,'LEGACY1',1,'WAITING',200,0,'2026-10-01"
                + " 10:30:00'),(2,4,'LEGACY4',1,'PAID',200,2,'2026-10-01 10:30:00')");
        db.update("INSERT INTO payment_compensation(payment_no,order_id,order_no,user_id)"
                + " VALUES('WAITING',1,'LEGACY1',1)");
        Flyway flyway = Flyway.configure().dataSource(source).cleanDisabled(true).load();
        flyway.migrate();
        flyway.validate();
        assertEquals(4, db.queryForObject("SELECT COUNT(*) FROM order_info", Integer.class));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM payment_record", Integer.class));
        assertEquals(800, db.queryForObject("SELECT SUM(total_amount) FROM order_info", Integer.class));
        assertNull(db.queryForObject("SELECT active_payment_id FROM order_info WHERE id=1", Long.class));
        assertEquals(2L, db.queryForObject("SELECT active_payment_id FROM order_info WHERE id=4", Long.class));
        assertEquals("2026-10-01T10:30",
                db.queryForObject("SELECT expire_time FROM order_info WHERE id=1", LocalDateTime.class).toString());
        assertEquals("RELEASED",
                db.queryForObject("SELECT status FROM stock_reservation WHERE order_id=3", String.class));
        assertEquals("PAID", db.queryForObject("SELECT status FROM stock_reservation WHERE order_id=4", String.class));
        assertNull(db.queryForObject("SELECT reservation_id FROM purchase_guard WHERE user_id=1 AND tier_id=1",
                Long.class));
        assertEquals("PAYMENT_QUERY", db.queryForObject("SELECT task_type FROM payment_compensation", String.class));
        var schemaMapper = MapperTestSupport.sessions(source).getMapper(TradeSchemaMapper.class);
        assertDoesNotThrow(() -> new TradeSchemaGuard(schemaMapper, true).run(new DefaultApplicationArguments()));
    }
}
