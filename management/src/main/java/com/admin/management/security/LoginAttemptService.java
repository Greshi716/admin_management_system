package com.admin.management.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.audit.AuditService;
import com.admin.management.audit.SensitiveMasker;

/**
 * Counts failed sign-ins, pauses sign-in after 5 in a row for 5 minutes, and audits every attempt (FR-022, FR-044).
 * Every method runs in its own transaction so a refused sign-in keeps its counter and its audit entry.
 */
@Service
public class LoginAttemptService {

	static final int MAX_FAILURES = 5;

	static final Duration PAUSE = Duration.ofMinutes(5);

	private static final String AREA = "Security";

	private static final String ENTITY = "Session";

	private static final int ATTEMPTED_USERNAME_LIMIT = 50;

	private final UserRepository users;

	private final AuditService audit;

	private final Clock clock;

	LoginAttemptService(UserRepository users, AuditService audit, Clock clock) {
		this.users = users;
		this.audit = audit;
		this.clock = clock;
	}

	/** How much longer sign-in is paused for this username, if it is. */
	@Transactional(readOnly = true)
	public Optional<Duration> pauseRemaining(String username) {
		return users.findByUsername(username).flatMap(this::remaining);
	}

	/**
	 * Notes a sign-in attempt made during a pause. It is audited but does not extend the pause.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordRefusedDuringPause(String username) {
		users.findByUsername(username)
			.ifPresent((user) -> audit.recordIndependent(user.getId(), null, AREA, "Login", ENTITY,
					Map.of("event", "refused", "reason", "paused"), null));
	}

	/**
	 * Records a failed sign-in. Returns the length of the pause when this was the failure that starts one.
	 * An unknown username is stored masked and cut to 50 characters, because people sometimes type their
	 * password into the username box (FR-025).
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<Duration> recordFailure(String attemptedUsername) {
		Optional<User> found = users.findByUsername(attemptedUsername);
		if (found.isEmpty()) {
			audit.recordIndependent(null, maskedAttempt(attemptedUsername), AREA, "Login", ENTITY,
					Map.of("event", "failed"), null);
			return Optional.empty();
		}
		User user = found.get();
		user.failedOnce();
		audit.recordIndependent(user.getId(), null, AREA, "Login", ENTITY, Map.of("event", "failed"), null);
		if (user.getFailedAttempts() < MAX_FAILURES) {
			users.save(user);
			return Optional.empty();
		}
		user.pauseUntil(clock.instant().plus(PAUSE));
		users.save(user);
		audit.recordIndependent(user.getId(), null, AREA, "Login", ENTITY,
				Map.of("event", "paused", "minutes", PAUSE.toMinutes()), null);
		return Optional.of(PAUSE);
	}

	/** Records a successful sign-in: clears the counter and stamps the last login time (FR-005). */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordSuccess(String username) {
		User user = users.findByUsername(username).orElseThrow();
		user.signedInAt(clock.instant());
		users.save(user);
		audit.recordIndependent(user.getId(), null, AREA, "Login", ENTITY, Map.of("event", "login"), null);
	}

	private Optional<Duration> remaining(User user) {
		Instant until = user.getLockedUntil();
		Instant now = clock.instant();
		if (until == null || !until.isAfter(now)) {
			return Optional.empty();
		}
		return Optional.of(Duration.between(now, until));
	}

	private static String maskedAttempt(String attempted) {
		String cut = attempted.length() > ATTEMPTED_USERNAME_LIMIT ? attempted.substring(0, ATTEMPTED_USERNAME_LIMIT)
				: attempted;
		return SensitiveMasker.mask(cut);
	}

}
