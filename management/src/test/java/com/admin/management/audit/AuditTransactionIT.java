package com.admin.management.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;

@Covers("FR-023")
class AuditTransactionIT extends IntegrationTest {

	@Autowired
	AuditService audit;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Test
	void aChangeThatRollsBackLeavesNoEntry() {
		String entity = "rollback-" + UUID.randomUUID();

		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			jdbc.update("insert into standards (name, display_order) values (?, 1)", entity);
			audit.record("Master data", "Create", entity, Map.of("name", entity), null);
			throw new IllegalStateException("the change failed after the entry was written");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(count("select count(*) from audit_log where entity = ?", entity)).isZero();
		assertThat(count("select count(*) from standards where name = ?", entity)).isZero();
	}

	@Test
	void aCommittedChangeKeepsItsEntry() {
		String entity = "commit-" + UUID.randomUUID();

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			jdbc.update("insert into standards (name, display_order) values (?, 1)", entity);
			audit.record("Master data", "Create", entity, Map.of("name", entity), null);
		});

		assertThat(count("select count(*) from audit_log where entity = ?", entity)).isEqualTo(1);
		assertThat(count("select count(*) from standards where name = ?", entity)).isEqualTo(1);
	}

	@Test
	void anEntryThatCannotBeSavedStopsTheChange() {
		String entity = "bad-action-" + UUID.randomUUID();

		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			jdbc.update("insert into standards (name, display_order) values (?, 1)", entity);
			audit.record("Master data", "NotAnAction", entity, Map.of(), null);
		})).isInstanceOf(RuntimeException.class);

		assertThat(count("select count(*) from standards where name = ?", entity)).isZero();
	}

	@Test
	void recordOutsideATransactionFails() {
		assertThatThrownBy(() -> audit.record("Master data", "Create", "no-transaction", Map.of(), null))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

	private long count(String sql, String value) {
		return jdbc.queryForObject(sql, Long.class, value);
	}

}
