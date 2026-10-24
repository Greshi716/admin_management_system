package com.admin.management.support;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Resets the test database once per test class, creates the known test admin and puts the test clock
 * back to real time.
 *
 * <p>
 * Which base class to extend: {@link IntegrationTest} (MockMvc, no real server) for almost every test;
 * {@link ServerIntegrationTest} (RANDOM_PORT, real servlet container) only when the container itself
 * matters, such as session expiry and sign-in lock-out timing.
 */
@ActiveProfiles("test")
@Import(TestClockConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractIntegrationTest {

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	protected MutableClock clock;

	@BeforeAll
	void resetDatabase() {
		clock.reset();
		flyway.clean();
		flyway.migrate();
		if (!getClass().isAnnotationPresent(WithoutTestAdmin.class)) {
			TestAdmin.create(jdbc);
		}
	}

}
