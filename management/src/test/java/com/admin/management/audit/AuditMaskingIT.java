package com.admin.management.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

@Covers("FR-025")
class AuditMaskingIT extends IntegrationTest {

	@Autowired
	AuditService audit;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	MockMvc mvc;

	@Test
	void aSensitiveValueIsStoredAndListedMaskedWithTheLastFourCharactersVisible() throws Exception {
		String entity = "masking-" + UUID.randomUUID();
		String secretNumber = "123456789012";

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> audit.record("Master data", "Edit",
				entity, Map.of("new", Map.of("aadhaar", secretNumber)), null));

		String stored = jdbc.queryForObject("select changes::text from audit_log where entity = ?", String.class,
				entity);
		assertThat(stored).contains("********9012").doesNotContain(secretNumber);

		SignedInClient.of(mvc)
			.post("/api/audit-log/search", "{\"globalFilter\":\"" + entity + "\"}")
			.andExpect(jsonPath("$.rows[0].changes").value(containsString("********9012")))
			.andExpect(content().string(not(containsString(secretNumber))));
	}

	@Test
	void aPasswordNestedInOldOrNewIsNeverStored() {
		String entity = "nested-" + UUID.randomUUID();

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> audit.record("Security", "Edit",
				entity, Map.of("old", Map.of("passwordHash", "$2a$10$oldhashvalue", "name", "a"), "new",
						Map.of("password", "nested-plain-pw", "name", "b")),
				null));

		String stored = jdbc.queryForObject("select changes::text from audit_log where entity = ?", String.class,
				entity);
		assertThat(stored).contains("\"name\"")
			.doesNotContain("nested-plain-pw")
			.doesNotContain("$2a$")
			.doesNotContain("password");
	}

	@Test
	void aPasswordOrHashIsNeverStored() throws Exception {
		String entity = "never-" + UUID.randomUUID();

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> audit.record("Security", "Edit",
				entity, Map.of("password", "plain-text-pw", "passwordHash", "$2a$10$abcdefghijk", "note", "changed"),
				null));

		String stored = jdbc.queryForObject("select changes::text from audit_log where entity = ?", String.class,
				entity);
		assertThat(stored).contains("changed")
			.doesNotContain("plain-text-pw")
			.doesNotContain("$2a$")
			.doesNotContain("password");

		SignedInClient.of(mvc)
			.post("/api/audit-log/search", "{\"globalFilter\":\"" + entity + "\"}")
			.andExpect(content().string(not(containsString("plain-text-pw"))))
			.andExpect(content().string(not(containsString("$2a$"))));
	}

}
