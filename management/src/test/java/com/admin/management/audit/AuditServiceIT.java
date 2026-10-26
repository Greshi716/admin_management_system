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
@Covers("FR-012")
@Covers("FR-019")
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

	@Test
	void historyIsNewestFirstFilteredByAreaAndEntityAndNamesTheUser() {
		String area = "HistA" + UUID.randomUUID();
		Long adminId = jdbc.queryForObject("select id from users where username = ?", Long.class, TestAdmin.USERNAME);
		insertAt(area, "Create", "Thing#1", "2026-03-01 10:00+05:30", adminId, "{\"name\": \"one\"}");
		insertAt(area, "Edit", "Thing#1", "2026-03-02 10:00+05:30", adminId, "{\"name\": {\"old\": \"one\", \"new\": \"two\"}}");
		insertAt(area, "Edit", "Part#9", "2026-03-03 10:00+05:30", null, "{\"x\": 1}");
		insertAt(area, "Edit", "Thing#2", "2026-03-04 10:00+05:30", adminId, "{}");
		insertAt("Other" + area, "Edit", "Thing#1", "2026-03-05 10:00+05:30", adminId, "{}");

		AuditService.History history = audit.history(area, java.util.List.of("Thing#1", "Part#9"), 200);

		assertThat(history.truncated()).isFalse();
		assertThat(history.entries()).extracting(AuditService.HistoryEntry::entity)
			.containsExactly("Part#9", "Thing#1", "Thing#1");
		assertThat(history.entries()).extracting(AuditService.HistoryEntry::action)
			.containsExactly("Edit", "Edit", "Create");
		assertThat(history.entries().get(1).userName()).isEqualTo(TestAdmin.USERNAME);
		assertThat(history.entries().get(0).userName()).isNull();
		assertThat(history.entries().get(1).changes()).containsKey("name");
		assertThat(history.entries().get(1).at()).isAfter(history.entries().get(2).at());
	}

	@Test
	void historyIsCappedAtTheLimitAndSaysWhenOlderEntriesExist() {
		String area = "HistB" + UUID.randomUUID();
		for (int i = 1; i <= 205; i++) {
			insertAt(area, "Edit", "Cap#1", "2026-04-01 00:00+05:30", null, "{\"n\": " + i + "}");
		}

		AuditService.History capped = audit.history(area, java.util.List.of("Cap#1"), 200);
		assertThat(capped.entries()).hasSize(200);
		assertThat(capped.truncated()).isTrue();
		assertThat(capped.entries().get(0).changes()).containsEntry("n", 205);

		AuditService.History exact = audit.history(area, java.util.List.of("Cap#1"), 205);
		assertThat(exact.entries()).hasSize(205);
		assertThat(exact.truncated()).isFalse();
	}

	@Test
	void historyOfNoEntitiesIsEmptyAndABadLimitIsRefused() {
		assertThat(audit.history("Students", java.util.List.of(), 200).entries()).isEmpty();
		assertThat(audit.history("Students", java.util.List.of("Student#0"), 200).truncated()).isFalse();
		assertThatThrownBy(() -> audit.history("Students", java.util.List.of("Student#1"), 0))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void historyKeepsMaskedValuesMasked() {
		String entity = unique();
		audit.recordIndependent("Students", "Create", entity,
				Map.of("aadhaar", "123456789012", "religionCaste", "Test religion 77", "name", "Alpha"), null);

		AuditService.History history = audit.history("Students", java.util.List.of(entity), 10);

		assertThat(history.entries()).hasSize(1);
		assertThat(history.entries().get(0).changes()).containsEntry("aadhaar", "********9012")
			.containsEntry("religionCaste", "************n 77")
			.containsEntry("name", "Alpha");
	}

	private void insertAt(String area, String action, String entity, String at, Long userId, String changesJson) {
		jdbc.update("insert into audit_log (at, user_id, area, action, entity, changes) values (?::timestamptz, ?, ?, ?, ?, ?::jsonb)",
				at, userId, area, action, entity, changesJson);
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
