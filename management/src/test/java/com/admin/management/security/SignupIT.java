package com.admin.management.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignIn;

/** F6: demo signup with the setting on. */
@Covers("FR-001")
@Covers("FR-003")
@Covers("FR-004")
@Covers("FR-005")
@Covers("FR-006")
@Covers("FR-007")
@Covers("FR-008")
@Covers("FR-009")
@Covers("FR-010")
@TestPropertySource(properties = "campusflow.signup-enabled=true")
class SignupIT extends IntegrationTest {

	private static final String PASSWORD = "demo-pass-123";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	private ResultActions signup(MockHttpSession session, String username, String password, String confirm)
			throws Exception {
		return mvc.perform(post("/api/auth/signup").with(csrf())
			.session(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Demo Reviewer\",\"username\":\"%s\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}"
				.formatted(username, password, confirm)));
	}

	private static String unique() {
		return "rev" + UUID.randomUUID().toString().substring(0, 8);
	}

	@Test
	void configSaysSignupIsOn() throws Exception {
		mvc.perform(get("/api/auth/config")).andExpect(status().isOk()).andExpect(jsonPath("$.signupEnabled").value(true));
	}

	@Test
	void signupCreatesAdminSignsInAndAuditsWithoutPassword() throws Exception {
		String username = unique();
		MockHttpSession session = new MockHttpSession();

		signup(session, username, PASSWORD, PASSWORD).andExpect(status().isCreated())
			.andExpect(jsonPath("$.username").value(username))
			.andExpect(jsonPath("$.role").value("ADMIN"))
			.andExpect(jsonPath("$.passwordChanged").value(true));

		mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
		String hash = jdbc.queryForObject("select password_hash from users where username = ?", String.class, username);
		assertThat(hash).isNotEqualTo(PASSWORD).startsWith("$2");
		Integer audited = jdbc.queryForObject(
				"select count(*) from audit_log where area = 'Security' and action = 'Create' and changes::text like ?",
				Integer.class, "%" + username + "%");
		assertThat(audited).isEqualTo(1);
		Integer leaked = jdbc.queryForObject("select count(*) from audit_log where changes::text like ? or changes::text like ?",
				Integer.class, "%" + PASSWORD + "%", "%$2%");
		assertThat(leaked).isZero();
		// The new account can sign in again from scratch.
		SignIn.login(mvc, new MockHttpSession(), username, PASSWORD).andExpect(status().isOk());
	}

	@Test
	void duplicateUsernameIsRefused() throws Exception {
		String username = unique();
		signup(new MockHttpSession(), username, PASSWORD, PASSWORD).andExpect(status().isCreated());

		signup(new MockHttpSession(), username, PASSWORD, PASSWORD).andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("username"));
		Integer count = jdbc.queryForObject("select count(*) from users where username = ?", Integer.class, username);
		assertThat(count).isEqualTo(1);
	}

	@Test
	void shortPasswordAndMismatchAreRefused() throws Exception {
		String username = unique();

		signup(new MockHttpSession(), username, "short", "short").andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
		signup(new MockHttpSession(), username, PASSWORD, PASSWORD + "x").andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("confirmPassword"));
		Integer count = jdbc.queryForObject("select count(*) from users where username = ?", Integer.class, username);
		assertThat(count).isZero();
	}

}
