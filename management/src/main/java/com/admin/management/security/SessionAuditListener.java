package com.admin.management.security;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import com.admin.management.audit.AuditService;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

/**
 * Records an automatic sign-out when a session expires after the idle timeout (FR-008). No request is active
 * then, so the user comes from the security context stored in the session itself.
 *
 * <p>
 * A logout marks its session first, so it is not counted a second time here. Stopping the server destroys
 * live sessions too; those are not idle sign-outs and are skipped.
 */
@Component
class SessionAuditListener implements HttpSessionListener, ApplicationListener<ContextClosedEvent> {

	static final String LOGGED_OUT = "campusflow.loggedOut";

	private static final Logger log = LoggerFactory.getLogger(SessionAuditListener.class);

	private final UserRepository users;

	private final AuditService audit;

	private volatile boolean shuttingDown;

	SessionAuditListener(UserRepository users, AuditService audit) {
		this.users = users;
		this.audit = audit;
	}

	@Override
	public void onApplicationEvent(ContextClosedEvent event) {
		this.shuttingDown = true;
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent event) {
		if (shuttingDown) {
			return;
		}
		try {
			HttpSession session = event.getSession();
			if (session.getAttribute(LOGGED_OUT) != null) {
				return;
			}
			Object stored = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
			if (!(stored instanceof SecurityContext context)) {
				return;
			}
			Authentication authentication = context.getAuthentication();
			if (authentication == null) {
				return;
			}
			users.findByUsername(authentication.getName())
				.ifPresent((user) -> audit.recordIndependent(user.getId(), null, "Security", "Login", "Session",
						Map.of("event", "idle"), null));
		}
		catch (RuntimeException ex) {
			// An audit problem must never break the container's session cleanup.
			log.warn("Could not record the idle sign-out", ex);
		}
	}

}
