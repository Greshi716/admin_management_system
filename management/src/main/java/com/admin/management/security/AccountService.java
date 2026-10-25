package com.admin.management.security;

import java.util.List;
import java.util.Map;

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
