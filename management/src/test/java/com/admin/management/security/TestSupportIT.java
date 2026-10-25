package com.admin.management.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignIn;
import com.admin.management.support.TestAdmin;

/** The test-profile endpoint that browser tests use to put the Admin back in a known state. */
class TestSupportIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void resetRestoresThePasswordClearsTheLockoutAndSetsTheFlagWithoutASession() throws Exception {
		jdbc.update("update users set password_hash = 'x', failed_attempts = 4, locked_until = now() + interval '1 hour'");

		mvc.perform(post("/api/test-support/reset-admin").param("passwordChanged", "false").with(csrf()))
			.andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("select password_changed from users", Boolean.class)).isFalse();
		assertThat(jdbc.queryForObject("select failed_attempts from users", Integer.class)).isZero();
		MockHttpSession session = new MockHttpSession();
		SignIn.login(mvc, session, TestAdmin.USERNAME, TestAdmin.PASSWORD).andExpect(status().isOk());
	}

}
