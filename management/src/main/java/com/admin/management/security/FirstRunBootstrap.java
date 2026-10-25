package com.admin.management.security;

import java.security.SecureRandom;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.admin.management.audit.AuditService;

/**
 * Creates the default Admin on a fresh database with a random password (FR-003, FR-009) and, when
 * {@code campusflow.reset-admin-password=true}, gives the existing Admin a new random password (FR-045).
 * The password goes only to the credentials file; the log gets the file's path.
 */
@Component
public class FirstRunBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(FirstRunBootstrap.class);

	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

	private static final int PASSWORD_LENGTH = 20;

	private static final String DEFAULT_USERNAME = "admin";

	private final UserRepository users;

	private final PasswordEncoder encoder;

	private final AuditService audit;

	private final CredentialsFile credentials;

	private final TransactionTemplate transaction;

	private final boolean reset;

	private final String testUsername;

	private final String testPassword;

	private final SecureRandom random = new SecureRandom();

	FirstRunBootstrap(UserRepository users, PasswordEncoder encoder, AuditService audit, CredentialsFile credentials,
			PlatformTransactionManager transactionManager, Environment environment,
			@Value("${campusflow.reset-admin-password:false}") boolean reset,
			@Value("${campusflow.test-admin.username:}") String testUsername,
			@Value("${campusflow.test-admin.password:}") String testPassword) {
		this.users = users;
		this.encoder = encoder;
		this.audit = audit;
		this.credentials = credentials;
		this.transaction = new TransactionTemplate(transactionManager);
		this.reset = reset;
		// A fixed password is honoured only under the test profile, never in a real installation.
		boolean testProfile = environment.acceptsProfiles(Profiles.of("test"));
		this.testUsername = testProfile ? testUsername : "";
		this.testPassword = testProfile ? testPassword : "";
	}

	@Override
	public void run(ApplicationArguments args) {
		if (users.count() == 0) {
			createAdmin();
		}
		else if (reset) {
			resetAdminPassword();
		}
	}

	private void createAdmin() {
		if (!testUsername.isBlank() && !testPassword.isBlank()) {
			users.save(new User("Test Admin", testUsername, encoder.encode(testPassword), true));
			log.info("Created the test Admin user '{}'.", testUsername);
			return;
		}
		String password = randomPassword();
		transaction.executeWithoutResult((status) -> {
			users.save(new User("Admin", DEFAULT_USERNAME, encoder.encode(password), false));
			credentials.write(DEFAULT_USERNAME, password);
		});
		log.info("Created the Admin user. Its one-time password is in {}", credentials.path());
	}

	/**
	 * Replaces the Admin's password with a new random one and marks it as not yet changed (FR-045). The audit
	 * entry is saved in the same transaction as the new password (FR-023).
	 */
	public void resetAdminPassword() {
		String password = randomPassword();
		transaction.executeWithoutResult((status) -> {
			User found = users.findFirstByRoleOrderByIdAsc(User.ADMIN)
				.orElseThrow(() -> new IllegalStateException("There is no Admin user to reset."));
			found.setPassword(encoder.encode(password), false);
			found.clearFailures();
			users.save(found);
			// Both written inside the transaction: if the entry or the file cannot be saved, the old password stays
			// valid (FR-023).
			audit.recordFor(found.getId(), "Security", "Edit", "User", Map.of("event", "reset"), null);
			credentials.write(found.getUsername(), password);
		});
		log.info("The Admin password was reset. The new one-time password is in {}", credentials.path());
	}

	private String randomPassword() {
		StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
		for (int i = 0; i < PASSWORD_LENGTH; i++) {
			password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return password.toString();
	}

}
