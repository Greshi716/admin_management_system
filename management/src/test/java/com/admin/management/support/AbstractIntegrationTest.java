package com.admin.management.support;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Resets the test database once per test class and creates the known test admin. */
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractIntegrationTest {

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeAll
	void resetDatabase() {
		flyway.clean();
		flyway.migrate();
		if (!getClass().isAnnotationPresent(WithoutTestAdmin.class)) {
			TestAdmin.create(jdbc);
		}
	}

}
