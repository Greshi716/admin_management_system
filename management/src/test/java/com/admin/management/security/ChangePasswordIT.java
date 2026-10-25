package com.admin.management.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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

@Covers("FR-009")
@Covers("FR-025")
@ExtendWith(OutputCaptureExtension.class)
class ChangePasswordIT extends IntegrationTest {

	private static final String NEW_PASSWORD = "A-Brand-New-Secret-2";

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
	void knownPassword() {
		jdbc.update("update users set password_hash = ?, password_changed = false, failed_attempts = 0, locked_until = null",
				encoder.encode(TestAdmin.PASSWORD));
	}

	@Test
	void changeSucceedsWithTheRightCurrentPassword(CapturedOutput output) throws Exception {
		MockHttpSession session = SignIn.asTestAdmin(mvc);
		int before = passwordRows();

		change(session, TestAdmin.PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("select password_changed from users", Boolean.class)).isTrue();
		assertThat(encoder.matches(NEW_PASSWORD, jdbc.queryForObject("select password_hash from users", String.class)))
			.isTrue();
		assertThat(passwordRows()).isEqualTo(before + 1);
		String audit = jdbc.queryForObject("select string_agg(changes::text, ' ') from audit_log where action = 'Edit'",
				String.class);
		assertThat(audit).contains("changed").doesNotContain(NEW_PASSWORD).doesNotContain(TestAdmin.PASSWORD);
		assertThat(output.getAll()).doesNotContain(NEW_PASSWORD);
		SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void aWrongCurrentPasswordIsRefusedOnThatField() throws Exception {
		MockHttpSession session = SignIn.asTestAdmin(mvc);

		change(session, "not-the-password", NEW_PASSWORD).andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));

		assertThat(jdbc.queryForObject("select password_changed from users", Boolean.class)).isFalse();
	}

	@Test
	void aShortOrUnchangedNewPasswordIsRefused() throws Exception {
		MockHttpSession session = SignIn.asTestAdmin(mvc);

		change(session, TestAdmin.PASSWORD, "short").andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
		change(session, TestAdmin.PASSWORD, TestAdmin.PASSWORD).andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
	}

	@Test
	void theCredentialsFileIsDeletedOnceThePasswordIsChanged() throws Exception {
		Files.writeString(DATA_DIR.resolve("credentials-first-run.txt"), "Username: x\nPassword: y\n");
		MockHttpSession session = SignIn.asTestAdmin(mvc);

		change(session, TestAdmin.PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

		assertThat(DATA_DIR.resolve("credentials-first-run.txt")).doesNotExist();
	}

	@Test
	void changingThePasswordNeedsASession() throws Exception {
		mvc.perform(post("/api/auth/change-password").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"currentPassword\":\"a\",\"newPassword\":\"bbbbbbbb\"}")).andExpect(status().isUnauthorized());
	}

	private ResultActions change(MockHttpSession session, String current, String next) throws Exception {
		return mvc.perform(post("/api/auth/change-password").with(csrf())
			.session(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
	}

	private int passwordRows() throws IOException {
		return jdbc.queryForObject("""
				select count(*) from audit_log where area = 'Security' and action = 'Edit' and changes->>'event' = 'password'
				""", Integer.class);
	}

}
