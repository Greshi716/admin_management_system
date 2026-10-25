package com.admin.management.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.TestAdmin;

@Covers("FR-023")
@Covers("FR-025")
class AuditServiceIT extends IntegrationTest {

	@Autowired
	AuditService audit;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PlatformTransactionManager transactionManager;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void recordWithoutATransactionIsRefused() {
		assertThatThrownBy(() -> audit.record("Master data", "Create", unique(), Map.of(), null))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	void recordJoinsTheCallersTransactionAndIsKeptOnCommit() {
		signInAsTestAdmin();
		String entity = unique();

		new TransactionTemplate(transactionManager)
			.executeWithoutResult(status -> audit.record("Master data", "Create", entity, Map.of("name", "2026-27"),
					"because"));

		Map<String, Object> row = jdbc.queryForMap("select * from audit_log where entity = ?", entity);
		assertThat(row.get("user_id")).isNotNull();
		assertThat(row.get("area")).isEqualTo("Master data");
		assertThat(row.get("action")).isEqualTo("Create");
		assertThat(row.get("reason")).isEqualTo("because");
		assertThat(row.get("at")).isNotNull();
	}

	@Test
	void recordIsRolledBackWithTheCallersTransaction() {
		String entity = unique();

		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			audit.record("Master data", "Edit", entity, Map.of(), null);
			throw new IllegalStateException("business change failed");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(count(entity)).isZero();
	}

	@Test
	void recordIndependentSurvivesARollbackOfTheCaller() {
		String entity = unique();

		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			audit.recordIndependent("Security", "Login", entity, Map.of("event", "failed"), null);
			throw new IllegalStateException("caller failed");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(count(entity)).isEqualTo(1);
	}

	@Test
	void recordIndependentWorksWithoutAnyTransaction() {
		String entity = unique();

		audit.recordIndependent("Security", "Export", entity, Map.of("rows", 3), null);

		assertThat(count(entity)).isEqualTo(1);
	}

	@Test
	void failedSignInKeepsAttemptedUsernameAndNoUser() {
		String entity = unique();

		audit.recordIndependent(null, "ghost", "Security", "Login", entity, Map.of("event", "failed"), null);

		Map<String, Object> row = jdbc.queryForMap("select * from audit_log where entity = ?", entity);
		assertThat(row.get("user_id")).isNull();
		assertThat(row.get("username_attempted")).isEqualTo("ghost");
	}

	@Test
	void passwordAndHashNeverReachTheLog() {
		String entity = unique();

		audit.recordIndependent("Security", "Edit", entity,
				Map.of("password", "plain", "passwordHash", "$2a$10$abc", "name", "Test Admin"), null);

		String changes = jdbc.queryForObject("select changes::text from audit_log where entity = ?", String.class,
				entity);
		assertThat(changes).contains("name").doesNotContain("plain").doesNotContain("$2a$").doesNotContain("password");
	}

	@Test
	void unauthenticatedCallerGivesNullUser() {
		String entity = unique();

		audit.recordIndependent("Security", "Login", entity, Map.of(), null);

		assertThat(jdbc.queryForObject("select user_id from audit_log where entity = ?", Long.class, entity)).isNull();
	}

	private void signInAsTestAdmin() {
		SecurityContextHolder.getContext()
			.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(TestAdmin.USERNAME, "n/a",
					java.util.List.of()));
	}

	private int count(String entity) {
		return jdbc.queryForObject("select count(*) from audit_log where entity = ?", Integer.class, entity);
	}

	private static String unique() {
		return "test#" + UUID.randomUUID();
	}

}
