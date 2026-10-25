package com.admin.management.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.WithoutTestAdmin;

@Covers("FR-003")
@Covers("FR-009")
@Covers("FR-045")
@WithoutTestAdmin
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = { "campusflow.test-admin.username=", "campusflow.test-admin.password=" })
class FirstRunBootstrapIT extends IntegrationTest {

	private static final Path DATA_DIR = BootstrapTestFiles.newDataDir();

	@DynamicPropertySource
	static void dataDir(DynamicPropertyRegistry registry) {
		registry.add("campusflow.data-dir", DATA_DIR::toString);
	}

	@Autowired
	FirstRunBootstrap bootstrap;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	@Test
	void anEmptyUsersTableGetsExactlyOneAdminWithARandomPassword(CapturedOutput output) throws IOException {
		jdbc.update("delete from users");
		Files.deleteIfExists(DATA_DIR.resolve("credentials-first-run.txt"));

		bootstrap.run(null);

		assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select role from users", String.class)).isEqualTo("ADMIN");
		assertThat(jdbc.queryForObject("select password_changed from users", Boolean.class)).isFalse();
		Path file = DATA_DIR.resolve("credentials-first-run.txt");
		assertThat(file).exists();
		String password = passwordIn(file);
		assertThat(password).hasSizeGreaterThanOrEqualTo(16);
		assertThat(encoder.matches(password, jdbc.queryForObject("select password_hash from users", String.class)))
			.isTrue();
		assertThat(output.getAll()).doesNotContain(password)
			.doesNotContain(jdbc.queryForObject("select password_hash from users", String.class));
	}

	@Test
	void aSecondStartCreatesNothing() throws IOException {
		jdbc.update("delete from users");
		bootstrap.run(null);
		String firstFile = Files.readString(DATA_DIR.resolve("credentials-first-run.txt"));
		String firstHash = jdbc.queryForObject("select password_hash from users", String.class);

		bootstrap.run(null);

		assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select password_hash from users", String.class)).isEqualTo(firstHash);
		assertThat(Files.readString(DATA_DIR.resolve("credentials-first-run.txt"))).isEqualTo(firstFile);
	}

	@Test
	void aResetWritesANewPasswordMarksItUnchangedAndAuditsWithoutTheSecret(CapturedOutput output)
			throws IOException {
		jdbc.update("delete from users");
		bootstrap.run(null);
		String oldPassword = passwordIn(DATA_DIR.resolve("credentials-first-run.txt"));
		jdbc.update("update users set password_changed = true, failed_attempts = 3");
		int before = resetRows();

		bootstrap.resetAdminPassword();

		String newPassword = passwordIn(DATA_DIR.resolve("credentials-first-run.txt"));
		assertThat(newPassword).isNotEqualTo(oldPassword);
		assertThat(encoder.matches(newPassword, jdbc.queryForObject("select password_hash from users", String.class)))
			.isTrue();
		assertThat(jdbc.queryForObject("select password_changed from users", Boolean.class)).isFalse();
		assertThat(jdbc.queryForObject("select failed_attempts from users", Integer.class)).isZero();
		assertThat(resetRows()).isEqualTo(before + 1);
		String audit = jdbc.queryForObject("select string_agg(changes::text, ' ') from audit_log", String.class);
		assertThat(audit).doesNotContain(newPassword).doesNotContain(oldPassword);
		assertThat(output.getAll()).doesNotContain(newPassword).doesNotContain(oldPassword);
	}

	private int resetRows() {
		return jdbc.queryForObject("""
				select count(*) from audit_log where area = 'Security' and changes->>'event' = 'reset'
				""", Integer.class);
	}

	static String passwordIn(Path file) throws IOException {
		Matcher matcher = Pattern.compile("(?m)^Password:\\s*(\\S+)\\s*$").matcher(Files.readString(file));
		assertThat(matcher.find()).as("a Password line in the credentials file").isTrue();
		return matcher.group(1);
	}

}
