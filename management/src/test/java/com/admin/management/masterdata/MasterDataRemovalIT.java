package com.admin.management.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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

@Covers("FR-020")
class MasterDataRemovalIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	SignedInClient api;

	long year;

	long standard;

	long division;

	@BeforeEach
	void seed() throws Exception {
		jdbc.update("delete from divisions");
		jdbc.update("delete from academic_years");
		jdbc.update("delete from standards");
		year = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values ('2026-27', '2026-04-01', '2027-03-31') returning id",
				Long.class);
		standard = jdbc.queryForObject("insert into standards (name, display_order) values ('Standard 1', 1) returning id",
				Long.class);
		division = jdbc.queryForObject("""
				insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A') returning id
				""", Long.class, year, standard);
		api = SignedInClient.of(mvc);
	}

	@Test
	void aYearUsedByADivisionCannotBeDeleted() throws Exception {
		api.delete("/api/academic-years/" + year)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("in use")));

		assertThat(jdbc.queryForObject("select count(*) from academic_years", Integer.class)).isEqualTo(1);
	}

	@Test
	void aStandardUsedByADivisionCannotBeDeleted() throws Exception {
		api.delete("/api/standards/" + standard)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("in use")));

		assertThat(jdbc.queryForObject("select count(*) from standards", Integer.class)).isEqualTo(1);
	}

	@Test
	void anUnreferencedRecordCanBeDeleted() throws Exception {
		long freeStandard = jdbc.queryForObject(
				"insert into standards (name, display_order) values ('Standard 2', 2) returning id", Long.class);
		long freeYear = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values ('2030-31', '2030-04-01', '2031-03-31') returning id",
				Long.class);

		api.delete("/api/standards/" + freeStandard).andExpect(status().isNoContent());
		api.delete("/api/academic-years/" + freeYear).andExpect(status().isNoContent());
		api.delete("/api/divisions/" + division).andExpect(status().isNoContent());
		api.delete("/api/academic-years/" + year).andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("select count(*) from academic_years", Integer.class)).isZero();
	}

	@Test
	void deletingSomethingThatIsGoneIsNotFound() throws Exception {
		api.delete("/api/standards/999999").andExpect(status().isNotFound());
	}

	@Test
	void aStandardMarkedInactiveLeavesPickListsButStaysOnItsDivisions() throws Exception {
		api.put("/api/standards/" + standard + "/active", "{\"active\":false,\"version\":0}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false))
			.andExpect(jsonPath("$.version").value(1));

		api.get("/api/standards").andExpect(jsonPath("$[*].id", not(hasItem((int) standard))));
		api.post("/api/divisions/search", "{}")
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.rows[0].standardName").value("Standard 1"));
		api.post("/api/standards/search", "{}").andExpect(jsonPath("$.rows[0].active").value(false));
	}

	@Test
	void aDivisionMarkedInactiveStaysInTheListAndCanBeActivatedAgain() throws Exception {
		api.put("/api/divisions/" + division + "/active", "{\"active\":false,\"version\":0}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));
		api.post("/api/divisions/search", "{}").andExpect(jsonPath("$.rows[0].active").value(false));

		api.put("/api/divisions/" + division + "/active", "{\"active\":true,\"version\":1}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void aCategoryMarkedInactiveLeavesPickListsButStaysInTheList() throws Exception {
		long category = jdbc.queryForObject("select id from student_categories where code = 'RTE'", Long.class);

		api.put("/api/student-categories/" + category + "/active", "{\"active\":false,\"version\":0}")
			.andExpect(status().isOk());

		api.get("/api/student-categories").andExpect(jsonPath("$[*].code", not(hasItem("RTE"))));
		api.post("/api/student-categories/search", "{\"filters\":{\"code\":{\"value\":\"RTE\",\"matchMode\":\"equals\"}}}")
			.andExpect(jsonPath("$.rows[*].code").value(contains("RTE")));
	}

}
