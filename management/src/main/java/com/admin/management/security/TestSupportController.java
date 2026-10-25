package com.admin.management.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Puts the test Admin back in a known state between browser tests: the known password, no lock-out, and the
 * "password changed" flag as asked. It exists only under the {@code test} profile (never in a real
 * installation) and is open to callers without a session, because a locked-out admin cannot sign in.
 */
@RestController
@Profile("test")
@RequestMapping("/api/test-support")
class TestSupportController {

	static final String PATH = "/api/test-support/**";

	private final UserRepository users;

	private final PasswordEncoder encoder;

	private final String testPassword;

	TestSupportController(UserRepository users, PasswordEncoder encoder,
			@Value("${campusflow.test-admin.password}") String testPassword) {
		this.users = users;
		this.encoder = encoder;
		this.testPassword = testPassword;
	}

	@PostMapping("/reset-admin")
	@Transactional
	ResponseEntity<Void> resetAdmin(@RequestParam(defaultValue = "true") boolean passwordChanged) {
		User admin = users.findFirstByRoleOrderByIdAsc(User.ADMIN).orElseThrow();
		admin.setPassword(encoder.encode(testPassword), passwordChanged);
		admin.clearFailures();
		users.save(admin);
		return ResponseEntity.noContent().build();
	}

}
