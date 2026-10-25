package com.admin.management.security;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.admin.management.audit.AuditService;
import com.admin.management.shared.CurrentAcademicYearProvider;
import com.admin.management.shared.web.ApiProblem;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/** Sign in, sign out, who am I, and change password (FR-001 to FR-006, FR-009, FR-044). */
@RestController
@RequestMapping("/api/auth")
class AuthController {

	private static final ApiProblem BAD_CREDENTIALS = ApiProblem
		.of(401, "Sign-in failed", "The username or password is not correct.")
		.withCode("bad_credentials");

	/** Computed once so an unknown username takes as long to refuse as a wrong password does. */
	private final String unknownUserHash;

	private final UserRepository users;

	private final PasswordEncoder encoder;

	private final LoginAttemptService attempts;

	private final AccountService accounts;

	private final AuditService audit;

	private final CredentialsFile credentials;

	private final SecurityContextRepository contextRepository;

	private final ObjectProvider<CurrentAcademicYearProvider> academicYear;

	private final Duration idleTimeout;

	AuthController(UserRepository users, PasswordEncoder encoder, LoginAttemptService attempts,
			AccountService accounts, AuditService audit, CredentialsFile credentials,
			SecurityContextRepository contextRepository, ObjectProvider<CurrentAcademicYearProvider> academicYear,
			@Value("${server.servlet.session.timeout}") Duration idleTimeout) {
		this.users = users;
		this.encoder = encoder;
		this.attempts = attempts;
		this.accounts = accounts;
		this.audit = audit;
		this.credentials = credentials;
		this.contextRepository = contextRepository;
		this.academicYear = academicYear;
		this.idleTimeout = idleTimeout;
		this.unknownUserHash = encoder.encode("not-a-real-password");
	}

	@PostMapping("/login")
	ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		String username = body.username().trim();
		Optional<Duration> paused = attempts.pauseRemaining(username);
		if (paused.isPresent()) {
			attempts.recordRefusedDuringPause(username);
			return pausedResponse(paused.get());
		}
		Optional<User> found = users.findByUsername(username);
		boolean passwordMatches = encoder.matches(body.password(),
				found.map(User::getPasswordHash).orElse(unknownUserHash));
		if (found.isEmpty() || !found.get().isActive() || !passwordMatches) {
			return attempts.recordFailure(username)
				.<ResponseEntity<?>>map(AuthController::pausedResponse)
				.orElseGet(AuthController::badCredentials);
		}
		attempts.recordSuccess(username);
		startSession(found.get(), request, response);
		return ResponseEntity.ok(me(users.findByUsername(username).orElseThrow()));
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			// Tells SessionAuditListener this end of session is a logout, not an idle timeout.
			session.setAttribute(SessionAuditListener.LOGGED_OUT, Boolean.TRUE);
		}
		audit.recordIndependent("Security", "Login", "Session", Map.of("event", "logout"), null);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	Me me(Authentication authentication) {
		return me(users.findByUsername(authentication.getName()).orElseThrow());
	}

	@PostMapping("/change-password")
	ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body,
			Authentication authentication) {
		accounts.changePassword(authentication.getName(), body.currentPassword(), body.newPassword());
		credentials.deleteIfExists();
		return ResponseEntity.noContent().build();
	}

	private void startSession(User user, HttpServletRequest request, HttpServletResponse response) {
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(user.getUsername(), null,
				AuthorityUtils.createAuthorityList("ROLE_" + user.getRole()));
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);
	}

	private Me me(User user) {
		CurrentAcademicYearProvider provider = academicYear.getIfAvailable();
		return new Me(user.getName(), user.getUsername(), user.getRole(), user.isPasswordChanged(),
				provider == null ? null : provider.currentName(), (int) idleTimeout.toSeconds());
	}

	private static ResponseEntity<?> badCredentials() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(BAD_CREDENTIALS);
	}

	private static ResponseEntity<?> pausedResponse(Duration remaining) {
		int seconds = (int) Math.max(1, remaining.plusMillis(999).toSeconds());
		long minutes = (seconds + 59) / 60;
		ApiProblem problem = ApiProblem
			.of(423, "Sign-in paused",
					"Too many failed sign-ins. Try again in " + minutes + (minutes == 1 ? " minute." : " minutes."))
			.withCode("paused")
			.withRetryAfterSeconds(seconds);
		return ResponseEntity.status(HttpStatus.LOCKED)
			.header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(problem);
	}

}
