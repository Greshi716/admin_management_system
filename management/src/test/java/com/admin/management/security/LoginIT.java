package com.admin.management.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignIn;
import com.admin.management.support.TestAdmin;

@Covers("FR-001")
@Covers("FR-002")
@Covers("FR-004")
@Covers("FR-005")
@Covers("FR-007")
class LoginIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void cleanState() {
		jdbc.update("update users set failed_attempts = 0, locked_until = null, active = true, last_login = null");
	}

	@Test
	void correctLoginReturnsMeAndUpdatesLastLogin() throws Exception {
		MockHttpSession session = new MockHttpSession();

		SignIn.login(mvc, session, TestAdmin.USERNAME, TestAdmin.PASSWORD)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value(TestAdmin.USERNAME))
			.andExpect(jsonPath("$.role").value("ADMIN"))
			.andExpect(jsonPath("$.passwordChanged").value(true))
			.andExpect(jsonPath("$.idleTimeoutSeconds").value(600));

		Integer signedIn = jdbc.queryForObject("select count(*) from users where last_login is not null",
				Integer.class);
		assertThat(signedIn).isEqualTo(1);
		mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
	}

	@Test
	void wrongUsernameAndWrongPasswordGiveTheSameMessage() throws Exception {
		String wrongUser = SignIn.login(mvc, new MockHttpSession(), "nobody", "whatever-123")
			.andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String wrongPassword = SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, "wrong-password-1")
			.andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(wrongUser).isEqualTo(wrongPassword);
	}

	@Test
	void inactiveUserIsRefusedWithTheSameMessage() throws Exception {
		String expected = SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, "wrong-password-1")
			.andReturn()
			.getResponse()
			.getContentAsString();
		jdbc.update("update users set active = false");

		String actual = SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, TestAdmin.PASSWORD)
			.andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	void unauthenticatedApiCallsReturn401() throws Exception {
		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/academic-years")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/anything/at/all")).andExpect(status().isUnauthorized());
	}

	@Test
	void passwordIsStoredHashedAndNeverAppearsInResponsesOrAudit() throws Exception {
		String body = SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, TestAdmin.PASSWORD)
			.andReturn()
			.getResponse()
			.getContentAsString();
		SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, "wrong-password-1");

		String hash = jdbc.queryForObject("select password_hash from users where username = ?", String.class,
				TestAdmin.USERNAME);
		assertThat(hash).startsWith("$2").isNotEqualTo(TestAdmin.PASSWORD);
		assertThat(body).doesNotContain(TestAdmin.PASSWORD).doesNotContain(hash).doesNotContain("password_hash");
		String audit = jdbc.queryForObject(
				"select coalesce(string_agg(coalesce(changes::text, '') || coalesce(reason, ''), ' '), '') from audit_log",
				String.class);
		assertThat(audit).doesNotContain(TestAdmin.PASSWORD).doesNotContain("wrong-password-1").doesNotContain(hash);
	}

	@Test
	void everySuccessfulAndFailedSignInIsAudited() throws Exception {
		int before = loginAuditRows();

		SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, TestAdmin.PASSWORD);
		SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, "wrong-password-1");

		assertThat(loginAuditRows()).isEqualTo(before + 2);
	}

	private int loginAuditRows() {
		return jdbc.queryForObject("select count(*) from audit_log where area = 'Security' and action = 'Login'",
				Integer.class);
	}

}
