package com.admin.management.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.admin.management.audit.AuditService;
import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignIn;
import com.admin.management.support.TestAdmin;

/** Starting with {@code campusflow.reset-admin-password=true}: the developer's forgotten-password procedure. */
@Covers("FR-045")
@Covers("FR-023")
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = { "campusflow.reset-admin-password=true", "campusflow.test-admin.username=",
		"campusflow.test-admin.password=" })
class AdminResetIT extends IntegrationTest {

	private static final Path DATA_DIR = BootstrapTestFiles.newDataDir();

	@DynamicPropertySource
	static void dataDir(DynamicPropertyRegistry registry) {
		registry.add("campusflow.data-dir", DATA_DIR::toString);
	}

	@Autowired
	FirstRunBootstrap bootstrap;

	@Autowired
	MockMvc mvc;

	@MockitoSpyBean
	AuditService audit;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void startingWithTheResetPropertyReplacesThePassword(CapturedOutput output) throws Exception {
		SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, TestAdmin.PASSWORD).andExpect(status().isOk());
		String oldHash = jdbc.queryForObject("select password_hash from users", String.class);

		bootstrap.run(null);

		assertThat(jdbc.queryForObject("select password_hash from users", String.class)).isNotEqualTo(oldHash);
		SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, TestAdmin.PASSWORD)
			.andExpect(status().isUnauthorized());
		String newPassword = passwordInFile();
		SignIn.login(mvc, new MockHttpSession(), TestAdmin.USERNAME, newPassword).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select password_changed from users", Boolean.class)).isFalse();
		assertThat(output.getAll()).doesNotContain(newPassword);
		String audit = jdbc.queryForObject(
				"select coalesce(string_agg(changes::text || coalesce(reason, ''), ' '), '') from audit_log",
				String.class);
		assertThat(audit).doesNotContain(newPassword);
		assertThat(jdbc.queryForObject("""
				select count(*) from audit_log a join users u on u.id = a.user_id
				where a.area = 'Security' and a.action = 'Edit' and a.changes->>'event' = 'reset'
				""", Integer.class)).isEqualTo(1);
	}

	@Test
	void aResetWhoseAuditEntryCannotBeSavedLeavesThePasswordAsItWas() throws Exception {
		String oldHash = jdbc.queryForObject("select password_hash from users", String.class);
		Path file = DATA_DIR.resolve("credentials-first-run.txt");
		String fileBefore = Files.exists(file) ? Files.readString(file) : null;
		// Stubbed on the target, because a call through the proxy would meet the MANDATORY check first.
		doThrow(new IllegalStateException("audit insert failed")).when(AopTestUtils.<AuditService>getUltimateTargetObject(audit))
			.recordFor(any(), any(), any(), any(), any(), any());

		assertThatThrownBy(() -> bootstrap.resetAdminPassword()).hasMessageContaining("audit insert failed");

		assertThat(jdbc.queryForObject("select password_hash from users", String.class)).isEqualTo(oldHash);
		assertThat(Files.exists(file) ? Files.readString(file) : null).isEqualTo(fileBefore);
	}

	private static String passwordInFile() throws IOException {
		Path file = DATA_DIR.resolve("credentials-first-run.txt");
		assertThat(Files.exists(file)).isTrue();
		return FirstRunBootstrapIT.passwordIn(file);
	}

}
