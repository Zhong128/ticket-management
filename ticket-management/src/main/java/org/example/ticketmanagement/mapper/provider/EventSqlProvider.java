// org/example/ticketmanagement/mapper/provider/EventSqlProvider.java
package org.example.ticketmanagement.mapper.provider;

import org.apache.ibatis.jdbc.SQL;
import org.example.ticketmanagement.dto.EventQueryDTO;

public class EventSqlProvider {

    /**
     * 构建分页查询的WHERE条件
     */
    public String selectByCondition(EventQueryDTO queryDTO) {
        return new SQL() {{
            SELECT("id, name, artist_name, category_id, city_id, venue, " +
                   "cover_image, description, status, sale_start_time, sale_end_time, " +
                   "event_start_time, event_end_time, create_time, update_time");
            FROM("event");

            if (queryDTO.getName() != null && !queryDTO.getName().isEmpty()) {
                WHERE("name LIKE CONCAT('%', #{queryDTO.name}, '%')");
            }
            if (queryDTO.getArtistName() != null && !queryDTO.getArtistName().isEmpty()) {
                WHERE("artist_name LIKE CONCAT('%', #{queryDTO.artistName}, '%')");
            }
            if (queryDTO.getCategoryId() != null) {
                WHERE("category_id = #{queryDTO.categoryId}");
            }
            if (queryDTO.getCityId() != null) {
                WHERE("city_id = #{queryDTO.cityId}");
            }
            if (queryDTO.getStatus() != null) {
                WHERE("status = #{queryDTO.status}");
            }

            ORDER_BY("event_start_time DESC, create_time DESC");
            // 添加分页（如果需要）
            if (queryDTO.getSize() != null && queryDTO.getOffset() != null) {
                LIMIT("#{queryDTO.size}");
                OFFSET("#{queryDTO.offset}");
            }
        }}.toString();
    }

    /**
     * 构建统计查询的WHERE条件
     */
    public String countByCondition(EventQueryDTO queryDTO) {
        return new SQL() {{
            SELECT("COUNT(*)");
            FROM("event");

            if (queryDTO.getName() != null && !queryDTO.getName().isEmpty()) {
                WHERE("name LIKE CONCAT('%', #{queryDTO.name}, '%')");
            }
            if (queryDTO.getArtistName() != null && !queryDTO.getArtistName().isEmpty()) {
                WHERE("artist_name LIKE CONCAT('%', #{queryDTO.artistName}, '%')");
            }
            if (queryDTO.getCategoryId() != null) {
                WHERE("category_id = #{queryDTO.categoryId}");
            }
            if (queryDTO.getCityId() != null) {
                WHERE("city_id = #{queryDTO.cityId}");
            }
            if (queryDTO.getStatus() != null) {
                WHERE("status = #{queryDTO.status}");
            }
        }}.toString();
    }
}