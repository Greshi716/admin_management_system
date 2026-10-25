package com.admin.management.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

@Covers("FR-010")
@Covers("FR-011")
@Covers("FR-012")
@Covers("FR-013")
@Covers("FR-020")
class AcademicYearIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	SignedInClient api;

	@BeforeEach
	void signInAndEmpty() throws Exception {
		clock.reset();
		jdbc.update("delete from divisions");
		jdbc.update("delete from academic_years");
		api = SignedInClient.of(mvc);
	}

	static String year(String name, String start, String end) {
		return """
				{"name":"%s","startDate":"%s","endDate":"%s"}""".formatted(name, start, end);
	}

	@Test
	void createViewAndEditAYear() throws Exception {
		api.post("/api/academic-years", year("2026-27", "2026-04-01", "2027-03-31"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.name").value("2026-27"))
			.andExpect(jsonPath("$.version").value(0));
		long id = jdbc.queryForObject("select id from academic_years", Long.class);

		api.get("/api/academic-years/" + id)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.startDate").value("2026-04-01"))
			.andExpect(jsonPath("$.endDate").value("2027-03-31"));

		api.put("/api/academic-years/" + id, """
				{"name":"2026-27 (renamed)","startDate":"2026-04-01","endDate":"2027-03-31","version":0}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("2026-27 (renamed)"))
			.andExpect(jsonPath("$.version").value(1));
	}

	@Test
	void nameAndBothDatesAreRequired() throws Exception {
		api.post("/api/academic-years", "{}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.hasItems("name", "startDate",
					"endDate")));
	}

	@Test
	void theNameMustBeUnique() throws Exception {
		api.post("/api/academic-years", year("2026-27", "2026-04-01", "2027-03-31")).andExpect(status().isCreated());

		api.post("/api/academic-years", year("2026-27", "2030-04-01", "2031-03-31"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
	}

	@Test
	void theEndDateMustBeAfterTheStartDate() throws Exception {
		api.post("/api/academic-years", year("bad", "2026-04-01", "2026-04-01"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("endDate"));
		api.post("/api/academic-years", year("worse", "2026-04-02", "2026-04-01"))
			.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void overlappingYearsAreRefusedWithAMessageNamingTheOtherYear() throws Exception {
		api.post("/api/academic-years", year("2026-27", "2026-04-01", "2027-03-31")).andExpect(status().isCreated());

		api.post("/api/academic-years", year("2026-27b", "2027-03-31", "2028-03-30"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("2026-27")));
		api.post("/api/academic-years", year("inside", "2026-06-01", "2026-07-01"))
			.andExpect(status().isConflict());
		api.post("/api/academic-years", year("2027-28", "2027-04-01", "2028-03-31")).andExpect(status().isCreated());
	}

	@Test
	void anEditThatOverlapsAnotherYearIsRefusedButTheYearMayOverlapItself() throws Exception {
		api.post("/api/academic-years", year("2026-27", "2026-04-01", "2027-03-31")).andExpect(status().isCreated());
		api.post("/api/academic-years", year("2027-28", "2027-04-01", "2028-03-31")).andExpect(status().isCreated());
		long second = jdbc.queryForObject("select id from academic_years where name = '2027-28'", Long.class);

		api.put("/api/academic-years/" + second, year("2027-28", "2027-03-01", "2028-03-31").replace("}",
				",\"version\":0}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("2026-27")));
		api.put("/api/academic-years/" + second, year("2027-28", "2027-04-01", "2028-04-30").replace("}",
				",\"version\":0}"))
			.andExpect(status().isOk());
	}

	@Test
	void theCurrentYearIsTheOneContainingTodayIncludingTheFirstAndLastDay() throws Exception {
		api.post("/api/academic-years", year("2026-27", "2026-04-01", "2027-03-31")).andExpect(status().isCreated());
		api.post("/api/academic-years", year("2027-28", "2027-04-01", "2028-03-31")).andExpect(status().isCreated());

		clock.setDate(LocalDate.of(2026, 4, 1));
		assertThat(currentYearInMe()).isEqualTo("2026-27");
		clock.setDate(LocalDate.of(2027, 3, 31));
		assertThat(currentYearInMe()).isEqualTo("2026-27");
		clock.setDate(LocalDate.of(2027, 4, 1));
		assertThat(currentYearInMe()).isEqualTo("2027-28");
		api.get("/api/academic-years")
			.andExpect(jsonPath("$[?(@.current == true)].name").value(org.hamcrest.Matchers.contains("2027-28")));
	}

	@Test
	void theSchoolDateInIndiaDecidesNotUtc() throws Exception {
		api.post("/api/academic-years", year("2027-28", "2027-04-01", "2028-03-31")).andExpect(status().isCreated());

		// 00:30 on 1 April in India is still 31 March in UTC.
		clock.set(LocalDate.of(2027, 4, 1).atTime(0, 30).atZone(com.admin.management.shared.time.ClockConfig.SCHOOL_ZONE)
			.toInstant());

		assertThat(currentYearInMe()).isEqualTo("2027-28");
	}

	@Test
	void betweenTwoYearsThereIsNoCurrentYearAndNoFallback() throws Exception {
		api.post("/api/academic-years", year("2026-27", "2026-04-01", "2027-03-31")).andExpect(status().isCreated());
		api.post("/api/academic-years", year("2028-29", "2028-04-01", "2029-03-31")).andExpect(status().isCreated());

		clock.setDate(LocalDate.of(2027, 9, 1));

		assertThat(currentYearInMe()).isNull();
		api.get("/api/academic-years").andExpect(jsonPath("$[?(@.current == true)]").isEmpty());
	}

	@Test
	void academicYearsHaveNoInactiveStatus() throws Exception {
		api.post("/api/academic-years", year("2026-27", "2026-04-01", "2027-03-31")).andExpect(status().isCreated());
		long id = jdbc.queryForObject("select id from academic_years", Long.class);

		api.put("/api/academic-years/" + id + "/active", "{\"active\":false,\"version\":0}")
			.andExpect(status().is4xxClientError());
		api.get("/api/academic-years/" + id).andExpect(jsonPath("$.active").doesNotExist());
	}

	@Test
	void anUnknownYearIsNotFound() throws Exception {
		api.get("/api/academic-years/999999").andExpect(status().isNotFound());
	}

	private String currentYearInMe() throws Exception {
		String body = api.get("/api/auth/me").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return com.jayway.jsonpath.JsonPath.read(body, "$.currentAcademicYear");
	}

}
