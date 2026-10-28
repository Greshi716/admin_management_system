package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/** Brought-forward note in Gujarati and a first save of 0.00 (test plan FR-007-B1, FR-007-B3, X-GUJ-1). */
@Covers("FR-007")
class BroughtForwardNoteIT extends IntegrationTest {

	private static final String GUJARATI_NOTE = "ગયા વર્ષની બાકી ફી";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	private long student(String prefix, int yearNumber) {
		Setup setup = FeeTestData.year(jdbc, prefix, LocalDate.of(yearNumber, 4, 1), 1);
		return jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class,
				FeeTestData.student(jdbc, setup, yearNumber * 100 + 1, 0, 0));
	}

	@Test
	void aGujaratiNoteIsSavedAndReturnedUnchangedAndNeverWrittenToTheAuditLog() throws Exception {
		long student = student("BfGuj", 3791);
		SignedInClient client = SignedInClient.of(mvc);

		String saved = client.put("/api/students/" + student + "/brought-forward",
				"{\"version\":null,\"amount\":150.50,\"note\":\"" + GUJARATI_NOTE + "\"}")
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		String read = client.get("/api/students/" + student + "/fees").andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(saved).contains(GUJARATI_NOTE);
		assertThat(read).contains(GUJARATI_NOTE);
		assertThat(jdbc.queryForObject("select note from fee_brought_forward where student_id = ?", String.class, student))
			.isEqualTo(GUJARATI_NOTE);
		assertThat(jdbc.queryForObject("""
				select string_agg(coalesce(changes::text, ''), ' ') from audit_log where entity = ?
				""", String.class, "BroughtForward#" + student)).doesNotContain(GUJARATI_NOTE)
			.contains("\"noteChanged\": true");
	}

	@Test
	void aFirstSaveOfZeroKeepsTheTotalDueUnchanged() throws Exception {
		long student = student("BfZero", 3792);
		SignedInClient client = SignedInClient.of(mvc);

		client.put("/api/students/" + student + "/brought-forward", "{\"version\":null,\"amount\":0.00}")
			.andExpect(status().isOk());

		// Whether a row or an audit entry is written for a first save of 0.00 is open (plan Q8): not asserted.
		String fees = client.get("/api/students/" + student + "/fees").andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat(fees).contains("\"totalDue\":0.00");
	}

}
