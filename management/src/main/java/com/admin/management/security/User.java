package com.admin.management.security;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** The sign-in account. There is one Admin and no other role (FR-003). Users are never deleted. */
@Entity
@Table(name = "users")
public class User {

	public static final String ADMIN = "ADMIN";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String username;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private String role;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "last_login")
	private Instant lastLogin;

	@Column(name = "password_changed", nullable = false)
	private boolean passwordChanged;

	@Column(name = "failed_attempts", nullable = false)
	private int failedAttempts;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Version
	private Long version;

	protected User() {
	}

	public User(String name, String username, String passwordHash, boolean passwordChanged) {
		this.name = name;
		this.username = username;
		this.passwordHash = passwordHash;
		this.role = ADMIN;
		this.passwordChanged = passwordChanged;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getUsername() {
		return username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getRole() {
		return role;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getLastLogin() {
		return lastLogin;
	}

	public boolean isPasswordChanged() {
		return passwordChanged;
	}

	public int getFailedAttempts() {
		return failedAttempts;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}

	public Long getVersion() {
		return version;
	}

	/** Sets a new hash. {@code changedByAdmin} is false when the system generated it (first run or reset). */
	public void setPassword(String newHash, boolean changedByAdmin) {
		this.passwordHash = newHash;
		this.passwordChanged = changedByAdmin;
	}

	public void signedInAt(Instant when) {
		this.lastLogin = when;
		this.failedAttempts = 0;
		this.lockedUntil = null;
	}

	public void failedOnce() {
		this.failedAttempts++;
	}

	/** Starts the pause and gives a fresh set of attempts once it ends. */
	public void pauseUntil(Instant until) {
		this.lockedUntil = until;
		this.failedAttempts = 0;
	}

	public void clearFailures() {
		this.failedAttempts = 0;
		this.lockedUntil = null;
	}

}
