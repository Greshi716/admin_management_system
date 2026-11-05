package com.admin.management.security;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Demo-only account creation (F6 FR-001 to FR-010). Off unless {@code campusflow.signup-enabled=true}
 * (environment variable CAMPUSFLOW_SIGNUP_ENABLED); then the sign-up request is answered as not found.
 */
@RestController
@RequestMapping("/api/auth")
class SignupController {

	private final boolean enabled;

	private final AccountService accounts;

	private final SessionStarter sessions;

	private final AuthController auth;

	SignupController(@Value("${campusflow.signup-enabled:false}") boolean enabled, AccountService accounts,
			SessionStarter sessions, AuthController auth) {
		this.enabled = enabled;
		this.accounts = accounts;
		this.sessions = sessions;
		this.auth = auth;
	}

	/** Public: the sign-in page asks this to decide whether to show "Create account" (FR-003). */
	@GetMapping("/config")
	Map<String, Boolean> config() {
		return Map.of("signupEnabled", enabled);
	}

	@PostMapping("/signup")
	ResponseEntity<?> signup(@Valid @RequestBody SignupRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		if (!enabled) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		User user = accounts.register(body.name(), body.username(), body.password(), body.confirmPassword());
		sessions.start(user, request, response);
		return ResponseEntity.status(HttpStatus.CREATED).body(auth.meOf(user));
	}

}
