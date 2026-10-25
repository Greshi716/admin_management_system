package com.admin.management.masterdata;

import static org.hamcrest.Matchers.hasItems;
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

@Covers("FR-016")
@Covers("FR-017")
class DivisionIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	SignedInClient api;

	long year1;

	long year2;

	long std1;

	long std2;

	@BeforeEach
	void seed() throws Exception {
		jdbc.update("delete from divisions");
		jdbc.update("delete from academic_years");
		jdbc.update("delete from standards");
		year1 = insertYear("2026-27", "2026-04-01", "2027-03-31");
		year2 = insertYear("2027-28", "2027-04-01", "2028-03-31");
		std1 = insertStandard("Standard 1", 1);
		std2 = insertStandard("Standard 2", 2);
		api = SignedInClient.of(mvc);
	}

	private long insertYear(String name, String start, String end) {
		return jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values (?, ?::date, ?::date) returning id",
				Long.class, name, start, end);
	}

	private long insertStandard(String name, int order) {
		return jdbc.queryForObject("insert into standards (name, display_order) values (?, ?) returning id",
				Long.class, name, order);
	}

	private String division(Long yearId, Long standardId, String name) {
		return """
				{"academicYearId":%s,"standardId":%s,"name":"%s"}""".formatted(yearId, standardId, name);
	}

	@Test
	void createLinksToAnExistingYearAndStandardAndShowsTheirNames() throws Exception {
		api.post("/api/divisions", division(year1, std1, "A"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.academicYearName").value("2026-27"))
			.andExpect(jsonPath("$.standardName").value("Standard 1"))
			.andExpect(jsonPath("$.name").value("A"))
			.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void yearStandardAndNameAreRequired() throws Exception {
		api.post("/api/divisions", "{}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[*].field").value(hasItems("academicYearId", "standardId", "name")));
	}

	@Test
	void aMissingYearOrStandardIsRefused() throws Exception {
		api.post("/api/divisions", division(999999L, std1, "A"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("academicYearId"));
		api.post("/api/divisions", division(year1, 999999L, "A"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("standardId"));
	}

	@Test
	void aDuplicateForTheSameYearAndStandardIsRefused() throws Exception {
		api.post("/api/divisions", division(year1, std1, "A")).andExpect(status().isCreated());

		api.post("/api/divisions", division(year1, std1, "A"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
	}

	@Test
	void theSameNameIsAllowedInAnotherYearOrStandard() throws Exception {
		api.post("/api/divisions", division(year1, std1, "A")).andExpect(status().isCreated());

		api.post("/api/divisions", division(year2, std1, "A")).andExpect(status().isCreated());
		api.post("/api/divisions", division(year1, std2, "A")).andExpect(status().isCreated());
	}

	@Test
	void searchShowsDivisionsWithYearAndStandardNamesAndFiltersOnThem() throws Exception {
		api.post("/api/divisions", division(year1, std1, "A")).andExpect(status().isCreated());
		api.post("/api/divisions", division(year2, std2, "B")).andExpect(status().isCreated());

		api.post("/api/divisions/search", "{\"filters\":{\"academicYearName\":{\"value\":\"2027\",\"matchMode\":\"contains\"}}}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.rows[0].name").value("B"))
			.andExpect(jsonPath("$.rows[0].standardName").value("Standard 2"));
		api.post("/api/divisions/search", "{\"globalFilter\":\"Standard 1\"}")
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.rows[0].name").value("A"));
	}

	@Test
	void anInactiveStandardCannotGetNewDivisions() throws Exception {
		jdbc.update("update standards set active = false where id = ?", std2);

		api.post("/api/divisions", division(year1, std2, "A"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("standardId"));
	}

}
