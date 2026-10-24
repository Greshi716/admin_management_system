package com.admin.management.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class IntegrationTestSupportIT extends IntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void runsOnTestDatabaseWithMigrationsApplied() {
		String database = jdbc.queryForObject("select current_database()", String.class);
		Integer applied = jdbc.queryForObject("select count(*) from flyway_schema_history", Integer.class);
		assertThat(database).isEqualTo("admin_management_test");
		assertThat(applied).isPositive();
		assertThat(mockMvc).isNotNull();
	}

}
