package com.admin.management.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@Covers("FR-006")
@Covers("FR-022")
class LogoutIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void afterLogoutTheOldSessionIsRejected() throws Exception {
		MockHttpSession session = SignIn.asTestAdmin(mvc);
		mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

		mvc.perform(post("/api/auth/logout").with(csrf()).session(session)).andExpect(status().isNoContent());

		mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void logoutWritesExactlyOneAuditEntry() throws Exception {
		MockHttpSession session = SignIn.asTestAdmin(mvc);
		int before = logoutRows();

		mvc.perform(post("/api/auth/logout").with(csrf()).session(session)).andExpect(status().isNoContent());

		assertThat(logoutRows()).isEqualTo(before + 1);
	}

	private int logoutRows() {
		return jdbc.queryForObject("""
				select count(*) from audit_log
				where area = 'Security' and action = 'Login' and changes->>'event' = 'logout'
				""", Integer.class);
	}

}
