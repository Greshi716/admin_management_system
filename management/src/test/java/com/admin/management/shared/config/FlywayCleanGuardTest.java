package com.admin.management.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class FlywayCleanGuardTest {

	private static FluentConfiguration config(boolean cleanDisabled) {
		return new FluentConfiguration().cleanDisabled(cleanDisabled);
	}

	private static MockEnvironment env(String db) {
		return new MockEnvironment().withProperty("spring.datasource.url",
				"jdbc:postgresql://localhost:5432/" + db + "?ssl=false");
	}

	@Test
	void allowsCleanOnTestDatabase() {
		new FlywayCleanGuard(env("admin_management_test")).customize(config(false));
	}

	@Test
	void refusesCleanOnOtherDatabase() {
		assertThatThrownBy(() -> new FlywayCleanGuard(env("admin_management_db")).customize(config(false)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void ignoresDatabaseNameWhenCleanDisabled() {
		new FlywayCleanGuard(env("admin_management_db")).customize(config(true));
	}

	@Test
	void parsesDatabaseName() {
		assertThat(FlywayCleanGuard.databaseName("jdbc:postgresql://h:5432/x_test?a=b")).isEqualTo("x_test");
	}

}
