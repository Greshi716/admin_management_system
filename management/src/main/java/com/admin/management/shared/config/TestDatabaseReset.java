package com.admin.management.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wipes and rebuilds the schema at startup, so a Playwright run always begins on an empty database. Off unless
 * {@code campusflow.test.reset-database-on-start=true}, which only the {@code test} profile sets. Flyway refuses
 * to clean unless clean is enabled, and {@link FlywayCleanGuard} refuses to start when clean is enabled on a
 * database whose name does not end in {@code _test}.
 */
@Configuration
@ConditionalOnProperty(name = "campusflow.test.reset-database-on-start", havingValue = "true")
public class TestDatabaseReset {

	@Bean
	FlywayMigrationStrategy cleanThenMigrate() {
		return flyway -> {
			flyway.clean();
			flyway.migrate();
		};
	}

}
