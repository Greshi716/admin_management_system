package com.admin.management.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HTTP Basic for {@code /api} calls from curl or Postman (F6 FR-011 to FR-015). It signs the request in for that
 * request only (no session) and uses the same pause and audit as the sign-in form. A refusal is JSON, with no
 * {@code WWW-Authenticate} header, so a browser never shows its own password prompt.
 */
final class BasicAuthFilter extends OncePerRequestFilter {

	private static final String PREFIX = "Basic ";

	private static final String BAD_CREDENTIALS = """
			{"title":"Sign-in failed","status":401,"detail":"The username or password is not correct.","code":"bad_credentials"}""";

	private final UserRepository users;

	private final PasswordEncoder encoder;

	private final LoginAttemptService attempts;

	/** Computed once so an unknown username takes as long to refuse as a wrong password does. */
	private final String unknownUserHash;

	BasicAuthFilter(UserRepository users, PasswordEncoder encoder, LoginAttemptService attempts) {
		this.users = users;
		this.encoder = encoder;
		this.attempts = attempts;
		this.unknownUserHash = encoder.encode("not-a-real-password");
	}

	/** True when the request carries a Basic header; the CSRF check is skipped for exactly these requests. */
	static boolean hasBasicHeader(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		return header != null && header.regionMatches(true, 0, PREFIX, 0, PREFIX.length());
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/") || !hasBasicHeader(request);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String[] credentials = decode(request.getHeader(HttpHeaders.AUTHORIZATION));
		if (credentials == null) {
			refuse(response, 401, BAD_CREDENTIALS, null);
			return;
		}
		String username = credentials[0].trim();
		Optional<Duration> paused = attempts.pauseRemaining(username);
		if (paused.isPresent()) {
			attempts.recordRefusedDuringPause(username);
			refusePaused(response, paused.get());
			return;
		}
		Optional<User> found = users.findByUsername(username);
		boolean matches = encoder.matches(credentials[1], found.map(User::getPasswordHash).orElse(unknownUserHash));
		if (found.isEmpty() || !found.get().isActive() || !matches) {
			Optional<Duration> pause = attempts.recordFailure(username);
			if (pause.isPresent()) {
				refusePaused(response, pause.get());
			}
			else {
				refuse(response, 401, BAD_CREDENTIALS, null);
			}
			return;
		}
		attempts.recordSuccess(username);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(username, null,
				AuthorityUtils.createAuthorityList("ROLE_" + found.get().getRole())));
		SecurityContextHolder.setContext(context);
		// Not saved to a session: the next request must send the header again.
		chain.doFilter(request, response);
	}

	/** Returns {username, password}, or null when the header is not valid Basic. */
	private static String[] decode(String header) {
		try {
			String decoded = new String(Base64.getDecoder().decode(header.substring(PREFIX.length()).trim()),
					StandardCharsets.UTF_8);
			int colon = decoded.indexOf(':');
			if (colon <= 0) {
				return null;
			}
			return new String[] { decoded.substring(0, colon), decoded.substring(colon + 1) };
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static void refusePaused(HttpServletResponse response, Duration remaining) throws IOException {
		int seconds = (int) Math.max(1, remaining.plusMillis(999).toSeconds());
		long minutes = (seconds + 59) / 60;
		String body = "{\"title\":\"Sign-in paused\",\"status\":423,\"detail\":\"Too many failed sign-ins. Try again in "
				+ minutes + (minutes == 1 ? " minute." : " minutes.") + "\",\"code\":\"paused\",\"retryAfterSeconds\":"
				+ seconds + "}";
		refuse(response, 423, body, String.valueOf(seconds));
	}

	private static void refuse(HttpServletResponse response, int status, String body, String retryAfter)
			throws IOException {
		response.setStatus(status);
		if (retryAfter != null) {
			response.setHeader(HttpHeaders.RETRY_AFTER, retryAfter);
		}
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(body);
	}

}
