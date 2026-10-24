package com.admin.management.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.support.IntegrationTest;

class AuditRepositoryIT extends IntegrationTest {

	@Autowired
	AuditRepository repository;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void savesAnEntryWithJsonChangesAndDatabaseTimestamp() {
		AuditEntry saved = repository.saveAndFlush(new AuditEntry(null, "nobody", "Security", "Login", "Session",
				Map.of("event", "failed", "attempts", 2), null));

		AuditEntry loaded = repository.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getAt()).isNotNull();
		assertThat(loaded.getUserId()).isNull();
		assertThat(loaded.getUsernameAttempted()).isEqualTo("nobody");
		assertThat(loaded.getChanges()).containsEntry("event", "failed").containsEntry("attempts", 2);
		String stored = jdbc.queryForObject("select changes::text from audit_log where id = ?", String.class,
				saved.getId());
		assertThat(stored).contains("\"event\"");
	}

}
