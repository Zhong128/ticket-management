package org.example.ticketmanagement.trade;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforce the project's persistence convention; fixtures may still use JDBC for setup/assertions.
 */
class MyBatisPersistenceBoundaryTest {

    @Test
    void applicationPersistenceDoesNotIntroduceASecondJdbcTemplateLayer() throws Exception {
        var violations = new ArrayList<String>();
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (source.contains("org.springframework.jdbc.core.")
                        || source.contains("org.springframework.jdbc.support.GeneratedKeyHolder")) {
                    violations.add(file.toString());
                }
            }
        }
        assertTrue(violations.isEmpty(), "Business persistence must use MyBatis Mapper: " + violations);
    }
}
