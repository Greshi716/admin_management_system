package com.admin.management.audit;

import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.Formula;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row of the append-only audit log. Has no setters: an entry is created once and never changed (FR-024). */
@Entity
@Table(name = "audit_log")
public class AuditEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Set by the database default {@code now()}. */
	@Generated(event = EventType.INSERT)
	@Column(name = "at", insertable = false, updatable = false)
	private Instant at;

	@Column(name = "user_id", updatable = false)
	private Long userId;

	@Column(name = "username_attempted", updatable = false)
	private String usernameAttempted;

	@Column(nullable = false, updatable = false)
	private String area;

	@Column(nullable = false, updatable = false)
	private String action;

	@Column(nullable = false, updatable = false)
	private String entity;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb", updatable = false)
	private Map<String, Object> changes;

	@Column(updatable = false)
	private String reason;

	/** Read-only, for the audit log list: the user's sign-in name, so the list needs no other module's classes. */
	@Formula("(select u.username from users u where u.id = user_id)")
	private String userName;

	/** Read-only, for the audit log list: the changes as text, so the search box can look inside them. */
	@Formula("cast(changes as text)")
	private String changesText;

	protected AuditEntry() {
	}

	public AuditEntry(Long userId, String usernameAttempted, String area, String action, String entity,
			Map<String, Object> changes, String reason) {
		this.userId = userId;
		this.usernameAttempted = usernameAttempted;
		this.area = area;
		this.action = action;
		this.entity = entity;
		this.changes = changes;
		this.reason = reason;
	}

	public Long getId() {
		return id;
	}

	public Instant getAt() {
		return at;
	}

	public Long getUserId() {
		return userId;
	}

	public String getUsernameAttempted() {
		return usernameAttempted;
	}

	public String getArea() {
		return area;
	}

	public String getAction() {
		return action;
	}

	public String getEntity() {
		return entity;
	}

	public Map<String, Object> getChanges() {
		return changes;
	}

	public String getReason() {
		return reason;
	}

	public String getUserName() {
		return userName;
	}

	public String getChangesText() {
		return changesText;
	}

}
