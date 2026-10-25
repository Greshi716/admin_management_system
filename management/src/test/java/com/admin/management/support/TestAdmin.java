package com.admin.management.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Known admin for tests. Uses JDBC and BCrypt directly so it has no dependency on entities or beans
 * that do not exist yet. The password is a test-only value, not a real credential.
 */
public final class TestAdmin {

	public static final String USERNAME = "testadmin";

	public static final String PASSWORD = "Test-Admin-Password-1";

	private TestAdmin() {
	}

	public static void create(JdbcTemplate jdbc) {
		Boolean usersTableExists = jdbc.queryForObject("select to_regclass('public.users') is not null",
				Boolean.class);
		if (!Boolean.TRUE.equals(usersTableExists)) {
			return;
		}
		jdbc.update("""
				insert into users (name, username, password_hash, role, active, password_changed)
				values (?, ?, ?, 'ADMIN', true, true)
				""", "Test Admin", USERNAME, new BCryptPasswordEncoder().encode(PASSWORD));
	}

}
