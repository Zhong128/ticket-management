package org.example.ticketmanagement.trade;

import org.flywaydb.core.Flyway;

import java.util.Set;

/**
 * Explicit offline maintenance entrypoint; does not start the application or payment clients.
 */
public final class TradeMigrationTool {

    private TradeMigrationTool() {
    }

    public static void main(String[] args) {
        if (args.length != 1 || !Set.of("baseline-existing", "migrate", "validate").contains(args[0])) {
            throw new IllegalArgumentException(
                    "Use baseline-existing, migrate or validate; backup and preflight a database copy first");
        }
        String url = required("SPRING_DATASOURCE_URL");
        String user = required("SPRING_DATASOURCE_USERNAME");
        String password = required("SPRING_DATASOURCE_PASSWORD");
        Flyway flyway = Flyway.configure().dataSource(url, user, password).baselineVersion("1").baselineOnMigrate(false)
                .cleanDisabled(true).load();
        switch (args[0]) {
        case "baseline-existing" -> flyway.baseline();
        case "migrate" -> flyway.migrate();
        default -> flyway.validate();
        }
        System.out.println("Explicit migration operation completed: " + args[0]);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing environment variable " + name);
        }
        return value;
    }
}
