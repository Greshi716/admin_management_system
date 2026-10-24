package com.admin.management.shared.config;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start when Flyway clean is enabled against a database whose name does not end in
 * {@code _test}, so a wrong profile can never wipe the real database.
 */
@Component
public class FlywayCleanGuard implements FlywayConfigurationCustomizer {

	private final Environment environment;

	public FlywayCleanGuard(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void customize(FluentConfiguration configuration) {
		if (configuration.isCleanDisabled()) {
			return;
		}
		String url = environment.getProperty("spring.datasource.url", "");
		String name = databaseName(url);
		if (!name.endsWith("_test")) {
			throw new IllegalStateException("Flyway clean is enabled but the database '" + name
					+ "' does not end in _test; refusing to start.");
		}
	}

	static String databaseName(String jdbcUrl) {
		String path = jdbcUrl;
		int query = path.indexOf('?');
		if (query >= 0) {
			path = path.substring(0, query);
		}
		return path.substring(path.lastIndexOf('/') + 1);
	}

}
