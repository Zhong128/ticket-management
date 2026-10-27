package org.example.ticketmanagement.maintenance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.example.ticketmanagement.mapper.CityMapper;
import org.example.ticketmanagement.mapper.EventCategoryMapper;
import org.example.ticketmanagement.mapper.EventMapper;
import org.example.ticketmanagement.mapper.EventSessionMapper;
import org.example.ticketmanagement.mapper.TicketTierMapper;
import org.example.ticketmanagement.pojo.City;
import org.example.ticketmanagement.pojo.Event;
import org.example.ticketmanagement.pojo.EventCategory;
import org.example.ticketmanagement.pojo.EventSession;
import org.example.ticketmanagement.pojo.TicketTier;

import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** 显式执行的本地演出维护入口，不启动支付、消息监听或迁移。 */
public final class DemoCatalogTool {

    private static final String MARKER = "[有戏演示数据 2026-10]";

    private DemoCatalogTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || (!args[0].equals("inspect") && !args[0].equals("import"))) {
            throw new IllegalArgumentException("用法：inspect 或 import <catalog.json> <manifest.json>");
        }
        if (args[0].equals("import") && args.length != 3) {
            throw new IllegalArgumentException("导入必须指定数据文件和本地清单路径");
        }

        Properties local = new Properties();
        Path configFile = Path.of("config/application-local.properties");
        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                local.load(reader);
            }
        }
        String url = setting(local, "SPRING_DATASOURCE_URL", "spring.datasource.url", "jdbc:mysql://127.0.0.1:3306/ticket");
        if (!url.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/[^?]+(\\?.*)?")) {
            throw new IllegalArgumentException("此工具仅允许显式的本机 MySQL 地址");
        }
        UnpooledDataSource source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url,
                setting(local, "SPRING_DATASOURCE_USERNAME", "spring.datasource.username", "root"),
                setting(local, "SPRING_DATASOURCE_PASSWORD", "spring.datasource.password", ""));
        Configuration configuration = new Configuration(new Environment("demo-maintenance", new JdbcTransactionFactory(), source));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(CityMapper.class);
        configuration.addMapper(EventCategoryMapper.class);
        configuration.addMapper(EventMapper.class);
        configuration.addMapper(EventSessionMapper.class);
        configuration.addMapper(TicketTierMapper.class);

        ObjectMapper json = new ObjectMapper();
        try (SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession(false)) {
            List<Event> existing = session.getMapper(EventMapper.class).selectAll();
            System.out.println("目标：" + url.replaceAll("\\?.*", "") + "，已有演出：" + existing.size());
            if (args[0].equals("inspect")) {
                System.out.println("启用城市：" + session.getMapper(CityMapper.class).selectByStatus(1).size()
                        + "，启用分类：" + session.getMapper(EventCategoryMapper.class).selectByStatus(1).size());
                return;
            }

            // 先预留清单文件再写库；清单记录每次导入涉及的主键，不修改已有记录。
            Path manifest = Path.of(args[2]);
            if (manifest.toAbsolutePath().getParent() != null) {
                Files.createDirectories(manifest.toAbsolutePath().getParent());
            }
            Files.writeString(manifest, "[]", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            List<Map<String, Object>> imported = new ArrayList<>();
            int skipped = 0;
            for (JsonNode item : json.readTree(Path.of(args[1]).toFile())) {
                String name = item.path("name").asText();
                if (name.isBlank() || existing.stream().anyMatch(event -> event.getName().equals(name))) {
                    skipped++;
                    continue;
                }
                long cityId = ensureCity(session, item);
                long categoryId = ensureCategory(session, item.path("category").asText());
                Event event = createEvent(item, cityId, categoryId);
                session.getMapper(EventMapper.class).insert(event);

                EventSession show = new EventSession();
                show.setEventId(event.getId());
                show.setSessionName("演示场次（非官方排期）");
                show.setSessionTime(event.getEventStartTime());
                show.setStatus(1);
                show.setCreateTime(LocalDateTime.now());
                session.getMapper(EventSessionMapper.class).insert(show);
                List<Long> tierIds = new ArrayList<>();
                for (int index = 0; index < 3; index++) {
                    TicketTier tier = new TicketTier();
                    tier.setEventId(event.getId());
                    tier.setSessionId(show.getId());
                    tier.setTierName(List.of("普通票", "优选票", "尊享票").get(index));
                    BigDecimal price = new BigDecimal(item.path("demoPrice").asText("180")).add(BigDecimal.valueOf(index * 100L));
                    tier.setOriginalPrice(price);
                    tier.setCurrentPrice(price);
                    int stock = item.path("demoState").asText().equals("sold-out") ? 0 : 100;
                    tier.setTotalStock(stock);
                    tier.setAvailableStock(stock);
                    tier.setStatus(1);
                    tier.setCreateTime(LocalDateTime.now());
                    tier.setUpdateTime(LocalDateTime.now());
                    session.getMapper(TicketTierMapper.class).insert(tier);
                    tierIds.add(tier.getId());
                }
                existing.add(event);
                imported.add(Map.of("eventId", event.getId(), "name", name, "sessionId", show.getId(),
                        "tierIds", tierIds, "sourceUrl", item.path("sourceUrl").asText()));
            }
            // 清单先落盘，事务再提交；失败时清单仅作为待核对记录，数据库事务回滚。
            json.writerWithDefaultPrettyPrinter().writeValue(manifest.toFile(), imported);
            session.commit();
            System.out.println("新增演出：" + imported.size() + "，跳过已有：" + skipped + "，清单：" + manifest);
        }
    }

    private static Event createEvent(JsonNode item, long cityId, long categoryId) {
        Event event = new Event();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = LocalDateTime.parse(item.path("demoTime").asText());
        event.setName(item.path("name").asText());
        event.setArtistName(item.path("artist").asText(""));
        event.setCityId(cityId);
        event.setCategoryId(categoryId);
        event.setVenue(item.path("venue").asText());
        event.setCoverImage(item.path("coverImage").asText());
        event.setDescription(MARKER + "\n" + item.path("name").asText()
                + "\n公开来源排期：" + item.path("sourceDates").asText()
                + "\n本站场次、票档、价格、库存和开售时间均为本地演示配置，不代表官方售票安排。"
                + "\n信息与海报来源：" + item.path("sourceUrl").asText());
        event.setStatus(1);
        event.setSaleStartTime(item.path("demoState").asText().equals("upcoming") ? now.plusDays(3) : now.minusDays(7));
        event.setSaleEndTime(start.minusHours(1));
        event.setEventStartTime(start);
        event.setEventEndTime(start.plusHours(2));
        event.setCreateTime(now);
        event.setUpdateTime(now);
        return event;
    }

    private static long ensureCity(SqlSession session, JsonNode item) {
        CityMapper mapper = session.getMapper(CityMapper.class);
        City city = mapper.selectByName(item.path("city").asText());
        if (city != null) {
            if (!Integer.valueOf(1).equals(city.getStatus())) {
                throw new IllegalArgumentException("城市已禁用，停止导入：" + city.getName());
            }
            return city.getId();
        }
        city = new City();
        city.setName(item.path("city").asText());
        city.setCode(item.path("cityCode").asText());
        city.setProvince(item.path("province").asText());
        city.setHotLevel(1);
        city.setStatus(1);
        city.setCreateTime(LocalDateTime.now());
        city.setUpdateTime(LocalDateTime.now());
        mapper.insert(city);
        return city.getId();
    }

    private static long ensureCategory(SqlSession session, String name) {
        EventCategoryMapper mapper = session.getMapper(EventCategoryMapper.class);
        EventCategory category = mapper.selectByName(name);
        if (category != null) {
            if (!Integer.valueOf(1).equals(category.getStatus())) {
                throw new IllegalArgumentException("分类已禁用，停止导入：" + name);
            }
            return category.getId();
        }
        category = new EventCategory();
        category.setName(name);
        category.setSortOrder(10);
        category.setStatus(1);
        category.setCreateTime(LocalDateTime.now());
        mapper.insert(category);
        return category.getId();
    }

    private static String setting(Properties local, String envKey, String propertyKey, String fallback) {
        String value = System.getenv(envKey);
        if (value != null) {
            return value;
        }
        return local.getProperty(propertyKey, local.getProperty(envKey, fallback));
    }
}
