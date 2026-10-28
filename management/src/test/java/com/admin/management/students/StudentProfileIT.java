package com.admin.management.students;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/** The profile and its History block: masking, class history, and the audit trail of a student. */
@Covers("FR-007")
@Covers("FR-009")
@Covers("FR-010")
@Covers("FR-019")
class StudentProfileIT extends IntegrationTest {

	static final String AADHAAR = "987654321098";

	static final String CASTE = "Test caste value 4242";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	StudentAudit studentAudit;

	@Autowired
	TransactionTemplate transactions;

	SignedInClient api;

	long currentYear;

	long earlierYear;

	long standardId;

	long divisionId;

	long categoryId;

	@BeforeEach
	void setUp() throws Exception {
		clock.setDate(LocalDate.of(2026, 10, 25));
		jdbc.update("delete from student_photos");
		jdbc.update("delete from student_enrollments");
		jdbc.update("delete from student_gr_numbers");
		jdbc.update("delete from students");
		jdbc.update("delete from divisions");
		jdbc.update("delete from standards");
		jdbc.update("delete from academic_years");
		jdbc.update("update student_categories set active = true");
		earlierYear = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values ('2025-26', '2025-04-01', '2026-03-31') returning id",
				Long.class);
		currentYear = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values ('2026-27', '2026-04-01', '2027-03-31') returning id",
				Long.class);
		standardId = jdbc.queryForObject("insert into standards (name, display_order) values ('Standard 1', 1) returning id",
				Long.class);
		divisionId = jdbc.queryForObject(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A') returning id", Long.class,
				currentYear, standardId);
		categoryId = jdbc.queryForObject("select min(id) from student_categories", Long.class);
		api = SignedInClient.of(mvc);
	}

	private long register(String grNo) throws Exception {
		String body = """
				{"grNo":"%s","surname":"Testsurname","firstName":"Alpha","middleName":"Middle","gender":"Male",
				 "dob":"2012-01-01","mobile1":"9000000001","mobile2":"9000000002","address":"Test address",
				 "aadhaar":"%s","religionCaste":"%s","categoryId":%d,"admissionDate":"2020-06-01",
				 "admissionStandardId":%d,"standardId":%d,"divisionId":%d}"""
			.formatted(grNo, AADHAAR, CASTE, categoryId, standardId, standardId, divisionId);
		String json = api.post("/api/students", body).andReturn().getResponse().getContentAsString();
		return Long.parseLong(json.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
	}

	@Test
	void theProfileShowsFullMobilesMaskedSensitiveValuesAndNoRollNumberOrFee() throws Exception {
		long id = register("PR-1");

		api.get("/api/students/" + id)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.grNo").value("PR-1"))
			.andExpect(jsonPath("$.mobile1").value("9000000001"))
			.andExpect(jsonPath("$.mobile2").value("9000000002"))
			.andExpect(jsonPath("$.aadhaar").value("********1098"))
			.andExpect(jsonPath("$.religionCaste").value("*****************4242"))
			.andExpect(jsonPath("$.source").value("Manual"))
			.andExpect(jsonPath("$.classHistory[0].academicYearName").value("2026-27"))
			.andExpect(jsonPath("$.classHistory[0].stdDiv").value("Standard 1-A"))
			.andExpect(jsonPath("$.classHistory[0].categoryName").isString())
			.andExpect(jsonPath("$.classHistory[0].yearStatus").value("Studying"))
			.andExpect(content().string(not(containsString(AADHAAR))))
			.andExpect(content().string(not(containsString(CASTE))))
			.andExpect(content().string(not(containsString("rollNo"))))
			// The fees live on /fees; the only fee word here is the class record flag feeLocked (F2 FR-008).
			.andExpect(content().string(not(containsString("\"fee\""))))
			.andExpect(content().string(not(containsString("totalFee"))))
			.andExpect(content().string(not(containsString("receipt"))));
	}

	@Test
	void anUnknownStudentIs404() throws Exception {
		api.get("/api/students/999999").andExpect(status().isNotFound());
		api.get("/api/students/999999/history").andExpect(status().isNotFound());
	}

	@Test
	void theClassHistoryIsNewestYearFirst() throws Exception {
		long id = register("PR-2");
		long earlierDivision = jdbc.queryForObject(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'B') returning id", Long.class,
				earlierYear, standardId);
		jdbc.update("""
				insert into student_enrollments (student_id, academic_year_id, standard_id, division_id, category_id,
				    year_start_date, year_status)
				values (?, ?, ?, ?, ?, '2025-04-01', 'Promoted')
				""", id, earlierYear, standardId, earlierDivision, categoryId);

		api.get("/api/students/" + id)
			.andExpect(jsonPath("$.classHistory", hasSize(2)))
			.andExpect(jsonPath("$.classHistory[0].academicYearName").value("2026-27"))
			.andExpect(jsonPath("$.classHistory[1].academicYearName").value("2025-26"))
			.andExpect(jsonPath("$.classHistory[1].stdDiv").value("Standard 1-B"))
			.andExpect(jsonPath("$.classHistory[1].yearStatus").value("Promoted"));
	}

	@Test
	void aStudentNeverEditedShowsOnlyTheRegistrationEntries() throws Exception {
		long id = register("PR-3");

		api.get("/api/students/" + id + "/history")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.truncated").value(false))
			.andExpect(jsonPath("$.entries", hasSize(2)))
			.andExpect(jsonPath("$.entries[*].action").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("Create"))))
			.andExpect(jsonPath("$.entries[?(@.record =~ /Student#.*/)]").exists());
	}

	@Test
	void laterEditsComeFirstWithOldAndNewValuesAndMaskedSensitiveFields() throws Exception {
		long id = register("PR-4");
		transactions.executeWithoutResult((tx) -> studentAudit.edited(StudentAudit.STUDENT, id,
				Map.of("address", "Test address", "aadhaar", AADHAAR),
				Map.of("address", "Another test address", "aadhaar", "111122223333")));

		api.get("/api/students/" + id + "/history")
			.andExpect(jsonPath("$.entries", hasSize(3)))
			.andExpect(jsonPath("$.entries[0].action").value("Edit"))
			.andExpect(jsonPath("$.entries[0].record").value("Student#" + id))
			.andExpect(jsonPath("$.entries[0].changes.old.address").value("Test address"))
			.andExpect(jsonPath("$.entries[0].changes.new.address").value("Another test address"))
			.andExpect(jsonPath("$.entries[0].changes.new.aadhaar").value("********3333"))
			.andExpect(jsonPath("$.entries[2].userName").isString())
			.andExpect(content().string(not(containsString(AADHAAR))))
			.andExpect(content().string(not(containsString("111122223333"))));
	}

	@Test
	void standardAndDivisionIdsInTheHistoryAreShownAsNames() throws Exception {
		long id = register("PR-4B");
		transactions.executeWithoutResult((tx) -> studentAudit.edited(StudentAudit.STUDENT, id,
				Map.of("standardId", standardId, "note", "kept"), Map.of("standardId", 987654L, "note", "kept too")));

		api.get("/api/students/" + id + "/history")
			.andExpect(jsonPath("$.entries[0].changes.old.standardId").value("Standard 1"))
			.andExpect(jsonPath("$.entries[0].changes.new.standardId").value("987654"))
			.andExpect(jsonPath("$.entries[0].changes.new.note").value("kept too"));
	}

	@Test
	void moreThan200EntriesReturnTheNewest200AndTruncated() throws Exception {
		long id = register("PR-5");
		jdbc.update("""
				insert into audit_log (area, action, entity, changes)
				select 'Students', 'Edit', 'Student#' || ?, '{"old":{},"new":{}}'::jsonb from generate_series(1, 205)
				""", id);

		api.get("/api/students/" + id + "/history")
			.andExpect(jsonPath("$.entries", hasSize(200)))
			.andExpect(jsonPath("$.truncated").value(true))
			.andExpect(jsonPath("$.entries[0].action").value("Edit"));
	}

	@Test
	void anotherStudentsEntriesAreNotMixedIn() throws Exception {
		long first = register("PR-6");
		long second = register("PR-7");

		api.get("/api/students/" + first + "/history")
			.andExpect(jsonPath("$.entries", hasSize(2)))
			.andExpect(content().string(not(containsString("Student#" + second + "\""))));
	}

}
