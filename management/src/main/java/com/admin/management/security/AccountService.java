package com.admin.management.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.audit.AuditService;
import com.admin.management.shared.web.ApiProblem;
import com.admin.management.shared.web.ValidationException;

/** Changes the signed-in admin's own password (FR-009). */
@Service
public class AccountService {

	static final int MIN_LENGTH = 8;

	/** BCrypt ignores everything after 72 bytes, so a longer password would be silently cut. */
	static final int MAX_LENGTH = 72;

	private final UserRepository users;

	private final PasswordEncoder encoder;

	private final AuditService audit;

	AccountService(UserRepository users, PasswordEncoder encoder, AuditService audit) {
		this.users = users;
		this.encoder = encoder;
		this.audit = audit;
	}

	/** Changes the password; the audit entry says only that it changed, never the value (FR-025). */
	@Transactional
	public void changePassword(String username, String currentPassword, String newPassword) {
		User user = users.findByUsername(username).orElseThrow();
		if (!encoder.matches(currentPassword, user.getPasswordHash())) {
			throw new ValidationException("currentPassword", "The current password is not correct.");
		}
		List<ApiProblem.FieldError> errors = newPasswordErrors(currentPassword, newPassword);
		if (!errors.isEmpty()) {
			throw new ValidationException(errors);
		}
		user.setPassword(encoder.encode(newPassword), true);
		users.save(user);
		audit.record("Security", "Edit", "User", Map.of("event", "password", "detail", "changed"), null);
	}

	/**
	 * Creates an Admin account for the demo signup (F6). The audit entry holds the username and name only, never
	 * the password, and is saved in the same transaction (FR-009).
	 */
	@Transactional
	public User register(String name, String username, String password, String confirmPassword) {
		String cleanName = name.trim();
		String cleanUsername = username.trim();
		List<ApiProblem.FieldError> errors = new ArrayList<>();
		if (cleanName.isEmpty()) {
			errors.add(new ApiProblem.FieldError("name", "Enter your name."));
		}
		if (cleanUsername.isEmpty()) {
			errors.add(new ApiProblem.FieldError("username", "Choose a username."));
		}
		else if (users.findByUsername(cleanUsername).isPresent()) {
			errors.add(new ApiProblem.FieldError("username", "This username is taken."));
		}
		if (password.length() < MIN_LENGTH) {
			errors.add(new ApiProblem.FieldError("password", "Use at least " + MIN_LENGTH + " characters."));
		}
		else if (password.length() > MAX_LENGTH) {
			errors.add(new ApiProblem.FieldError("password", "Use at most " + MAX_LENGTH + " characters."));
		}
		else if (!password.equals(confirmPassword)) {
			errors.add(new ApiProblem.FieldError("confirmPassword", "The passwords do not match."));
		}
		if (!errors.isEmpty()) {
			throw new ValidationException(errors);
		}
		User user;
		try {
			// Flushed here so a duplicate created at the same moment is reported as "taken", not as a server error.
			user = users.saveAndFlush(new User(cleanName, cleanUsername, encoder.encode(password), true));
		}
		catch (DataIntegrityViolationException ex) {
			throw new ValidationException("username", "This username is taken.");
		}
		audit.recordFor(user.getId(), "Security", "Create", "User",
				Map.of("event", "signup", "username", cleanUsername, "name", cleanName), null);
		return user;
	}

	private static List<ApiProblem.FieldError> newPasswordErrors(String current, String proposed) {
		if (proposed.length() < MIN_LENGTH) {
			return List.of(new ApiProblem.FieldError("newPassword", "Use at least " + MIN_LENGTH + " characters."));
		}
		if (proposed.length() > MAX_LENGTH) {
			return List.of(new ApiProblem.FieldError("newPassword", "Use at most " + MAX_LENGTH + " characters."));
		}
		if (proposed.equals(current)) {
			return List.of(new ApiProblem.FieldError("newPassword",
					"Choose a password that is different from the current one."));
		}
		return List.of();
	}

}
