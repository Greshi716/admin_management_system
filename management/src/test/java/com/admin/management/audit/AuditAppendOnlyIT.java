package com.admin.management.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

@Covers("FR-024")
class AuditAppendOnlyIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	RequestMappingHandlerMapping mappings;

	@Test
	void theApiOffersOnlySearchAndExportForTheAuditLog() {
		List<String> methodsAndPaths = mappings.getHandlerMethods()
			.keySet()
			.stream()
			.flatMap(info -> info.getPathPatternsCondition()
				.getPatternValues()
				.stream()
				.filter(path -> path.startsWith("/api/audit-log"))
				.flatMap(path -> info.getMethodsCondition().getMethods().stream().map(method -> method + " " + path)))
			.sorted()
			.toList();

		assertThat(methodsAndPaths).containsExactly("POST /api/audit-log/export", "POST /api/audit-log/search");
	}

	@Test
	void editAndDeleteRequestsAreRefused() throws Exception {
		insertOne();
		SignedInClient api = SignedInClient.of(mvc);

		api.put("/api/audit-log/1", "{}").andExpect(status().is4xxClientError());
		api.delete("/api/audit-log/1").andExpect(status().is4xxClientError());
		api.delete("/api/audit-log").andExpect(status().is4xxClientError());
		api.post("/api/audit-log", "{}").andExpect(status().is4xxClientError());
	}

	@Test
	void theDatabaseRejectsUpdateAndDelete() {
		String entity = insertOne();

		assertThatThrownBy(() -> jdbc.update("update audit_log set reason = 'x' where entity = ?", entity))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("append-only");
		assertThatThrownBy(() -> jdbc.update("delete from audit_log where entity = ?", entity))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("append-only");
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity = ?", Long.class, entity))
			.isEqualTo(1);
	}

	@Test
	void theDatabaseRejectsTruncate() {
		String entity = insertOne();

		assertThatThrownBy(() -> jdbc.execute("truncate table audit_log"))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("append-only");
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity = ?", Long.class, entity))
			.isEqualTo(1);
	}

	private String insertOne() {
		String entity = "append-only-" + UUID.randomUUID();
		jdbc.update("insert into audit_log (area, action, entity) values ('Fees', 'Create', ?)", entity);
		return entity;
	}

}
