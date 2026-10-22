package org.example.ticketmanagement.util;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

/** 兼容原Mapper查询结果的数字与时间类型，不承担业务判断。 */
public final class ReservationValues {
    private ReservationValues() {
    }

    public static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    public static LocalDateTime time(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        return value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value;
    }
}
