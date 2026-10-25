package com.admin.management.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/** Test plan FR-011-B/B2/F and FR-012-F: date boundaries between years, and "current" is never stored. */
@Covers("FR-011")
@Covers("FR-012")
class AcademicYearBoundaryIT extends IntegrationTest {

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

	private static String year(String name, String start, String end) {
		return """
				{"name":"%s","startDate":"%s","endDate":"%s"}""".formatted(name, start, end);
	}

	@Test
	void endOneDayAfterStartIsAccepted() throws Exception {
		api.post("/api/academic-years", year("one-day", "2026-06-01", "2026-06-02")).andExpect(status().isCreated());
	}

	@Test
	void startingTheDayAfterAnotherYearEndsIsAllowed() throws Exception {
		api.post("/api/academic-years", year("2025-26", "2025-06-01", "2026-05-31")).andExpect(status().isCreated());

		api.post("/api/academic-years", year("2026-27", "2026-06-01", "2027-05-31")).andExpect(status().isCreated());
	}

	@Test
	void startingOnTheLastDayOfAnotherYearOverlapsAndNamesIt() throws Exception {
		api.post("/api/academic-years", year("2025-26", "2025-06-01", "2026-05-31")).andExpect(status().isCreated());

		api.post("/api/academic-years", year("2026-27", "2026-05-31", "2027-05-30"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("2025-26")));
	}

	@Test
	void aYearThatEnclosesOrCrossesAnExistingYearIsRefusedAndNothingIsStored() throws Exception {
		api.post("/api/academic-years", year("2026-27", "2026-06-01", "2027-05-31")).andExpect(status().isCreated());

		api.post("/api/academic-years", year("crossStart", "2026-01-01", "2026-06-15"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("2026-27")));
		api.post("/api/academic-years", year("encloses", "2026-01-01", "2028-12-31"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("2026-27")));

		assertThat(jdbc.queryForObject("select count(*) from academic_years", Integer.class)).isEqualTo(1);
	}

	@Test
	void aCurrentFlagSentByTheClientIsNeverStored() throws Exception {
		// 2020-21 is long past on the test clock, so it can never be the current year.
		api.post("/api/academic-years",
				"{\"name\":\"2020-21\",\"startDate\":\"2020-06-01\",\"endDate\":\"2021-05-31\",\"current\":true}")
			.andExpect(status().is2xxSuccessful());

		api.get("/api/academic-years").andExpect(jsonPath("$[?(@.current == true)]").isEmpty());
		assertThat(jdbc.queryForObject("""
				select count(*) from information_schema.columns
				where table_name = 'academic_years' and column_name like '%current%'
				""", Integer.class)).isZero();
	}

}
