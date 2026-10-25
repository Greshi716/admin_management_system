package com.admin.management.students;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/** The registration endpoints against contracts/openapi.yaml, and what they must never leak. */
@Covers("FR-001")
@Covers("FR-002")
@Covers("FR-003")
@Covers("FR-010")
@Covers("FR-017")
@ExtendWith(OutputCaptureExtension.class)
class StudentApiIT extends IntegrationTest {

	static final String AADHAAR = "987654321098";

	static final String CASTE = "Test caste value 4242";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	SignedInClient api;

	long yearId;

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
		yearId = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values ('2026-27', '2026-04-01', '2027-03-31') returning id",
				Long.class);
		standardId = jdbc.queryForObject("insert into standards (name, display_order) values ('Standard 1', 1) returning id",
				Long.class);
		divisionId = jdbc.queryForObject(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A') returning id", Long.class,
				yearId, standardId);
		categoryId = jdbc.queryForObject("select min(id) from student_categories", Long.class);
		api = SignedInClient.of(mvc);
	}

	private String body(String grNo) {
		return """
				{"grNo":"%s","surname":"Testsurname","firstName":"Alpha","middleName":"Middle","gender":"Male",
				 "dob":"2012-01-01","mobile1":"9000000001","mobile2":"9000000002","address":"Test address",
				 "aadhaar":"%s","religionCaste":"%s","categoryId":%d,"admissionDate":"2020-06-01",
				 "admissionStandardId":%d,"standardId":%d,"divisionId":%d}"""
			.formatted(grNo, AADHAAR, CASTE, categoryId, standardId, standardId, divisionId);
	}

	@Test
	void postCreatesAndAnswers201WithMaskedValuesAndTheClassHistory() throws Exception {
		api.post("/api/students", body("API-1"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.version").value(0))
			.andExpect(jsonPath("$.grNo").value("API-1"))
			.andExpect(jsonPath("$.srNo").isNumber())
			.andExpect(jsonPath("$.status").value("Active"))
			.andExpect(jsonPath("$.source").value("Manual"))
			.andExpect(jsonPath("$.aadhaar").value("********1098"))
			.andExpect(jsonPath("$.religionCaste").value("*****************4242"))
			.andExpect(jsonPath("$.admissionStandardName").value("Standard 1"))
			.andExpect(jsonPath("$.classHistory", hasSize(1)))
			.andExpect(jsonPath("$.classHistory[0].stdDiv").value("Standard 1-A"))
			.andExpect(jsonPath("$.classHistory[0].yearStatus").value("Studying"))
			.andExpect(content().string(not(containsString(AADHAAR))))
			.andExpect(content().string(not(containsString(CASTE))));
	}

	@Test
	void aUsedGrNoIsA409OnTheGrNoField() throws Exception {
		api.post("/api/students", body("API-2")).andExpect(status().isCreated());

		api.post("/api/students", body(" api-2 "))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("grNo"));
	}

	@Test
	void fieldProblemsAre422WithTheFieldAndTheMessageNeverRepeatsTheAadhaar() throws Exception {
		String bad = body("API-3").replace(AADHAAR, "1234 5678 90");

		api.post("/api/students", bad)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[?(@.field=='aadhaar')]").exists())
			.andExpect(content().string(not(containsString("1234 5678 90"))))
			.andExpect(content().string(not(containsString("1234567890"))));
		api.post("/api/students", "{}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[?(@.field=='grNo')]").exists())
			.andExpect(jsonPath("$.fieldErrors[?(@.field=='mobile1')]").exists());
	}

	@Test
	void noCurrentYearIs422() throws Exception {
		clock.setDate(LocalDate.of(2031, 1, 1));

		api.post("/api/students", body("API-4")).andExpect(status().isUnprocessableEntity());
	}

	@Test
	void nameMatchesListsStudentsOfAnyStatus() throws Exception {
		api.post("/api/students", body("API-5")).andExpect(status().isCreated());
		jdbc.update("update students set status = 'Left'");

		api.get("/api/students/name-matches?surname=testsurname&firstName=ALPHA&middleName=middle")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].grNo").value("API-5"))
			.andExpect(jsonPath("$[0].name").value("Testsurname Alpha Middle"))
			.andExpect(jsonPath("$[0].status").value("Left"));
		api.get("/api/students/name-matches?surname=Nobody&firstName=Here")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void theEditEndpointReturnsTheFullValuesWithNoStoreAndOnlyIt() throws Exception {
		api.post("/api/students", body("API-6")).andExpect(status().isCreated());
		long id = jdbc.queryForObject("select id from students", Long.class);

		api.get("/api/students/" + id + "/edit")
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", containsString("no-store")))
			.andExpect(jsonPath("$.aadhaar").value(AADHAAR))
			.andExpect(jsonPath("$.religionCaste").value(CASTE))
			.andExpect(jsonPath("$.version").value(0))
			.andExpect(jsonPath("$.grNo").value("API-6"));
		api.get("/api/students/999999/edit").andExpect(status().isNotFound());
	}

	@Test
	void pickListsHaveTheCurrentYearAndTheActiveChoices() throws Exception {
		jdbc.update("insert into standards (name, display_order, active) values ('Retired', 9, false)");

		api.get("/api/student-pick-lists")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currentYear.name").value("2026-27"))
			.andExpect(jsonPath("$.standards", hasSize(1)))
			.andExpect(jsonPath("$.standards[0].name").value("Standard 1"))
			.andExpect(jsonPath("$.divisions", hasSize(1)))
			.andExpect(jsonPath("$.divisions[0].standardId").value(standardId))
			.andExpect(jsonPath("$.categories", hasSize(7)));
	}

	@Test
	void pickListsWithAStudentKeepTheStudentsOwnInactiveValues() throws Exception {
		api.post("/api/students", body("API-7")).andExpect(status().isCreated());
		long id = jdbc.queryForObject("select id from students", Long.class);
		jdbc.update("update divisions set active = false");
		jdbc.update("update standards set active = false");

		api.get("/api/student-pick-lists?yearId=" + yearId)
			.andExpect(jsonPath("$.divisions", hasSize(0)))
			.andExpect(jsonPath("$.standards", hasSize(0)));
		api.get("/api/student-pick-lists?yearId=" + yearId + "&studentId=" + id)
			.andExpect(jsonPath("$.divisions", hasSize(1)))
			.andExpect(jsonPath("$.standards", hasSize(1)));
	}

	@Test
	void pickListsWithoutACurrentYearAre409() throws Exception {
		clock.setDate(LocalDate.of(2031, 1, 1));

		api.get("/api/student-pick-lists").andExpect(status().isConflict());
	}

	@Test
	void unauthenticatedRequestsAreRefused() throws Exception {
		mvc.perform(post("/api/students").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("API-8")))
			.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/students/1/edit")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/students/name-matches?surname=a&firstName=b")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/student-pick-lists")).andExpect(status().isUnauthorized());
		assertThat(jdbc.queryForObject("select count(*) from students", Integer.class)).isZero();
	}

	@Test
	void theApplicationLogNeverHoldsAnAadhaarOrReligionCasteValue(CapturedOutput output) throws Exception {
		api.post("/api/students", body("API-9")).andExpect(status().isCreated());
		api.post("/api/students", body("API-9")).andExpect(status().isConflict());
		api.post("/api/students", body("API-10").replace("9000000001", "12")).andExpect(status().isUnprocessableEntity());
		long id = jdbc.queryForObject("select id from students where gr_no = 'API-9'", Long.class);
		api.get("/api/students/" + id + "/edit").andExpect(status().isOk());

		assertThat(output.getAll()).doesNotContain(AADHAAR).doesNotContain(CASTE);
	}

}
