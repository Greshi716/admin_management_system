package com.admin.management.shared.config;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Refuses to start under the {@code test} profile unless the database name ends in {@code _test}. The profile
 * opens an unauthenticated Admin reset and uses a password that is committed in the repository, so it must never
 * run against a real database.
 */
@Component
@Profile("test")
public class TestProfileGuard {

	private final Environment environment;

	public TestProfileGuard(Environment environment) {
		this.environment = environment;
	}

	@PostConstruct
	void check() {
		String name = FlywayCleanGuard.databaseName(environment.getProperty("spring.datasource.url", ""));
		if (!name.endsWith("_test")) {
			throw new IllegalStateException("The test profile is active but the database '" + name
					+ "' does not end in _test; refusing to start.");
		}
	}

}
