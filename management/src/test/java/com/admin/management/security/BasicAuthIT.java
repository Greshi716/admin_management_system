package com.admin.management.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.TestAdmin;

/** F6: HTTP Basic on /api. */
@Covers("FR-011")
@Covers("FR-012")
@Covers("FR-013")
@Covers("FR-014")
@Covers("FR-015")
class BasicAuthIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void cleanState() {
		jdbc.update("update users set failed_attempts = 0, locked_until = null, active = true");
	}

	private static String basic(String username, String password) {
		return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
	}

	private ResultActions me(String header) throws Exception {
		return mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, header));
	}

	@Test
	void validHeaderIsSignedInWithoutSessionOrCsrf() throws Exception {
		me(basic(TestAdmin.USERNAME, TestAdmin.PASSWORD)).andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value(TestAdmin.USERNAME));
		// A write also needs no CSRF token; the endpoint answers for the signed-in user, not 401 or 403.
		mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION,
				basic(TestAdmin.USERNAME, TestAdmin.PASSWORD))).andExpect(status().isNoContent());
	}

	@Test
	void wrongPasswordUnknownUserAndBadHeaderGiveSameJson401WithoutChallenge() throws Exception {
		String wrongPassword = me(basic(TestAdmin.USERNAME, "wrong-password-1")).andExpect(status().isUnauthorized())
			.andReturn().getResponse().getContentAsString();
		var unknown = me(basic("nobody", "wrong-password-1")).andExpect(status().isUnauthorized()).andReturn().getResponse();
		assertThat(unknown.getContentAsString()).isEqualTo(wrongPassword);
		assertThat(unknown.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
		me("Basic !!!not-base64").andExpect(status().isUnauthorized());
	}

	@Test
	void noHeaderStaysUnauthorizedAndSessionlessWritesNeedCsrf() throws Exception {
		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());
	}

	@Test
	void fiveFailuresPauseBothBasicAndTheForm() throws Exception {
		for (int i = 0; i < 5; i++) {
			me(basic(TestAdmin.USERNAME, "wrong-password-1")).andExpect(status().is(i < 4 ? 401 : 423));
		}
		me(basic(TestAdmin.USERNAME, TestAdmin.PASSWORD)).andExpect(status().isLocked());
		com.admin.management.support.SignIn.login(mvc, new org.springframework.mock.web.MockHttpSession(),
				TestAdmin.USERNAME, TestAdmin.PASSWORD).andExpect(status().isLocked());
	}

	@Test
	void inactiveUserIsRefusedAndSuccessStampsLastLogin() throws Exception {
		jdbc.update("update users set last_login = null");
		me(basic(TestAdmin.USERNAME, TestAdmin.PASSWORD)).andExpect(status().isOk());
		Integer stamped = jdbc.queryForObject("select count(*) from users where last_login is not null", Integer.class);
		assertThat(stamped).isEqualTo(1);

		jdbc.update("update users set active = false where username = ?", TestAdmin.USERNAME);
		me(basic(TestAdmin.USERNAME, TestAdmin.PASSWORD)).andExpect(status().isUnauthorized());
	}

}
