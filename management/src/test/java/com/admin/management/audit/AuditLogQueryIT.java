package com.admin.management.audit;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

@Covers("FR-026")
class AuditLogQueryIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	SignedInClient api;

	/** Audit rows cannot be deleted, so every test marks its own rows and searches for that mark. */
	String run;

	@BeforeEach
	void signInAndSeed() throws Exception {
		api = SignedInClient.of(mvc);
		run = "run" + UUID.randomUUID().toString().replace("-", "");
		// Times are India time (+05:30): the last minutes of December, a mid-January day, the last and first
		// minutes around the month change, and a February day.
		insert("2025-12-31 23:30:00+05:30", "Fees", "Create", "dec-2025");
		insert("2026-01-15 10:00:00+05:30", "Fees", "Edit", "jan-15");
		insert("2026-01-31 23:59:00+05:30", "Master data", "Delete", "jan-31");
		insert("2026-02-01 00:00:00+05:30", "Fees", "Cancel", "feb-01");
		insert("2026-02-10 09:00:00+05:30", "Security", "Login", "feb-10");
	}

	@Test
	void aDateRangeKeepsOnlyRowsInsideItIncludingBothDays() throws Exception {
		search("\"from\":\"2026-01-15\",\"to\":\"2026-02-01\"")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(3))
			.andExpect(jsonPath("$.rows[*].entity")
				.value(containsInAnyOrder(name("jan-15"), name("jan-31"), name("feb-01"))));
	}

	@Test
	void aMonthKeepsOnlyThatSchoolMonth() throws Exception {
		search("\"month\":\"2026-01\"")
			.andExpect(jsonPath("$.total").value(2))
			.andExpect(jsonPath("$.rows[*].entity").value(containsInAnyOrder(name("jan-15"), name("jan-31"))));
	}

	@Test
	void aYearKeepsOnlyThatCalendarYear() throws Exception {
		search("\"year\":2025")
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.rows[0].entity").value(name("dec-2025")));
	}

	@Test
	void anAreaKeepsOnlyThatArea() throws Exception {
		search("\"area\":\"Fees\"")
			.andExpect(jsonPath("$.total").value(3))
			.andExpect(jsonPath("$.rows[*].area").value(everyItem(is("Fees"))));
	}

	@Test
	void anActionKeepsOnlyThatAction() throws Exception {
		search("\"action\":\"Cancel\"")
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.rows[0].entity").value(name("feb-01")));
	}

	@Test
	void theFiltersCombineWithAnd() throws Exception {
		search("\"area\":\"Fees\",\"action\":\"Edit\",\"month\":\"2026-01\",\"year\":2026")
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.rows[0].entity").value(name("jan-15")));
	}

	@Test
	void theNewestEntryComesFirstByDefault() throws Exception {
		search("")
			.andExpect(jsonPath("$.rows[0].entity").value(name("feb-10")))
			.andExpect(jsonPath("$.rows[4].entity").value(name("dec-2025")));
	}

	@Test
	void theUserColumnShowsTheSignedInNameOrTheNameTypedInAFailedSignIn() throws Exception {
		jdbc.update(
				"insert into audit_log (user_id, area, action, entity) select id, 'Fees', 'Create', ? from users limit 1",
				name("with-user"));
		jdbc.update(
				"insert into audit_log (username_attempted, area, action, entity) values ('someone', 'Security', 'Login', ?)",
				name("attempt"));

		api.post("/api/audit-log/search", "{\"globalFilter\":\"" + name("with-user") + "\"}")
			.andExpect(jsonPath("$.rows[0].user").value("testadmin"));
		api.post("/api/audit-log/search", "{\"globalFilter\":\"" + name("attempt") + "\"}")
			.andExpect(jsonPath("$.rows[0].user").value("someone"));
	}

	@Test
	void theWhatChangedColumnIsSearchable() throws Exception {
		jdbc.update("insert into audit_log (area, action, entity, changes) values ('Fees', 'Edit', ?, ?::jsonb)",
				name("changes"), "{\"new\":{\"label\":\"zebra-" + run + "\"}}");

		api.post("/api/audit-log/search", "{\"globalFilter\":\"zebra-" + run + "\"}")
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.rows[0].entity").value(name("changes")))
			.andExpect(jsonPath("$.rows[0].changes").value(containsString("zebra-" + run)));
	}

	@Test
	void aStartDateAfterTheEndDateIsRefused() throws Exception {
		search("\"from\":\"2026-02-10\",\"to\":\"2026-01-01\"")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("from"));
	}

	@Test
	void aBadMonthIsRefused() throws Exception {
		search("\"month\":\"January\"")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("month"));
	}

	private String name(String entity) {
		return run + "-" + entity;
	}

	/** Searches for this test's rows only, with the given extra JSON fields. */
	private ResultActions search(String extraFields) throws Exception {
		return api.post("/api/audit-log/search",
				"{\"globalFilter\":\"" + run + "\"" + (extraFields.isEmpty() ? "" : "," + extraFields) + "}");
	}

	private void insert(String at, String area, String action, String entity) {
		jdbc.update("insert into audit_log (at, area, action, entity) values (?::timestamptz, ?, ?, ?)", at, area,
				action, name(entity));
	}

}
