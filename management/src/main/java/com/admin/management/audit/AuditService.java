package com.admin.management.audit;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.shared.CurrentUserProvider;
import com.admin.management.shared.masking.SensitiveMasker;

/**
 * Public API of the audit module. Other modules write audit entries only through this class.
 *
 * <p>
 * {@link #record} must run inside the caller's transaction, so a change and its audit entry are saved
 * together or not at all (FR-023). {@link #recordIndependent} runs in its own transaction, for events
 * that have no business transaction to join, such as sign-in attempts and exports.
 */
@Service
public class AuditService {

	private final AuditRepository repository;

	private final CurrentUserProvider currentUser;

	AuditService(AuditRepository repository, CurrentUserProvider currentUser) {
		this.repository = repository;
		this.currentUser = currentUser;
	}

	/** Records a change in the caller's transaction; fails when there is none. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(String area, String action, String entity, Map<String, Object> changes, String reason) {
		save(currentUserId(), null, area, action, entity, changes, reason);
	}

	/** Records an event in its own transaction, so it survives a rollback of the caller. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordIndependent(String area, String action, String entity, Map<String, Object> changes,
			String reason) {
		save(currentUserId(), null, area, action, entity, changes, reason);
	}

	/**
	 * Records an event in its own transaction for a user who is not signed in, such as a failed sign-in.
	 * {@code userId} is null when the username does not exist.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordIndependent(Long userId, String usernameAttempted, String area, String action, String entity,
			Map<String, Object> changes, String reason) {
		save(userId, usernameAttempted, area, action, entity, changes, reason);
	}

	private void save(Long userId, String usernameAttempted, String area, String action, String entity,
			Map<String, Object> changes, String reason) {
		repository.save(new AuditEntry(userId, usernameAttempted, area, action, entity,
				SensitiveMasker.sanitize(changes, SensitiveMasker.MASKED), reason));
	}

	/** The signed-in user's id, or null when nobody is signed in. The audit log keeps a foreign key to users. */
	private Long currentUserId() {
		return currentUser.currentUserId().orElse(null);
	}


}
