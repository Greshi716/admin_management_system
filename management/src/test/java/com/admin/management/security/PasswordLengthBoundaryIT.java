package com.admin.management.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignIn;
import com.admin.management.support.TestAdmin;

/** Test plan FR-009-B: a new password of exactly 8 characters is accepted, 7 is refused. */
@Covers("FR-009")
class PasswordLengthBoundaryIT extends IntegrationTest {

	private static final Path DATA_DIR = BootstrapTestFiles.newDataDir();

	@DynamicPropertySource
	static void dataDir(DynamicPropertyRegistry registry) {
		registry.add("campusflow.data-dir", DATA_DIR::toString);
	}

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	@BeforeEach
	@AfterEach
	void knownPassword() {
		jdbc.update("update users set password_hash = ?, password_changed = false, failed_attempts = 0, locked_until = null",
				encoder.encode(TestAdmin.PASSWORD));
	}

	@Test
	void sevenCharactersAreRefusedAndTheOldPasswordStays() throws Exception {
		MockHttpSession session = SignIn.asTestAdmin(mvc);

		change(session, TestAdmin.PASSWORD, "Abcde12").andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));

		assertThat(encoder.matches(TestAdmin.PASSWORD, jdbc.queryForObject("select password_hash from users", String.class)))
			.isTrue();
	}

	@Test
	void eightCharactersAreAccepted() throws Exception {
		MockHttpSession session = SignIn.asTestAdmin(mvc);

		change(session, TestAdmin.PASSWORD, "Abcdef12").andExpect(status().isNoContent());

		assertThat(encoder.matches("Abcdef12", jdbc.queryForObject("select password_hash from users", String.class)))
			.isTrue();
	}

	private ResultActions change(MockHttpSession session, String current, String next) throws Exception {
		return mvc.perform(post("/api/auth/change-password").with(csrf())
			.session(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
	}

}
