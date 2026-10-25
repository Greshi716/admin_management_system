package com.admin.management.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TestDatabaseResetTest {

	ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(TestDatabaseReset.class);

	@Test
	void isOffByDefault() {
		runner.run(context -> assertThat(context).doesNotHaveBean(FlywayMigrationStrategy.class));
	}

	@Test
	void isOnlyOnWhenThePropertyIsTrue() {
		runner.withPropertyValues("campusflow.test.reset-database-on-start=false")
			.run(context -> assertThat(context).doesNotHaveBean(FlywayMigrationStrategy.class));
		runner.withPropertyValues("campusflow.test.reset-database-on-start=true")
			.run(context -> assertThat(context).hasSingleBean(FlywayMigrationStrategy.class));
	}

}
