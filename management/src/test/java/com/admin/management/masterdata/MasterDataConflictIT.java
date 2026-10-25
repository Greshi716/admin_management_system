package com.admin.management.masterdata;

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

/** Two windows edit the same record: the second save is refused and the latest values come back (FR-046). */
@Covers("FR-046")
class MasterDataConflictIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	SignedInClient api;

	@BeforeEach
	void signInAndEmpty() throws Exception {
		jdbc.update("delete from divisions");
		jdbc.update("delete from academic_years");
		jdbc.update("delete from standards");
		api = SignedInClient.of(mvc);
	}

	@Test
	void aStaleYearSaveIsRefusedWithTheLatestRecord() throws Exception {
		long id = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values ('2026-27', '2026-04-01', '2027-03-31') returning id",
				Long.class);
		api.put("/api/academic-years/" + id,
				"{\"name\":\"First window\",\"startDate\":\"2026-04-01\",\"endDate\":\"2027-03-31\",\"version\":0}")
			.andExpect(status().isOk());

		api.put("/api/academic-years/" + id,
				"{\"name\":\"Second window\",\"startDate\":\"2026-04-01\",\"endDate\":\"2027-03-31\",\"version\":0}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"))
			.andExpect(jsonPath("$.current.name").value("First window"))
			.andExpect(jsonPath("$.current.version").value(1));
	}

	@Test
	void aStaleStandardSaveIsRefusedWithTheLatestRecord() throws Exception {
		long id = jdbc.queryForObject("insert into standards (name, display_order) values ('Standard 1', 1) returning id",
				Long.class);
		api.put("/api/standards/" + id, "{\"name\":\"First window\",\"displayOrder\":1,\"version\":0}")
			.andExpect(status().isOk());

		api.put("/api/standards/" + id, "{\"name\":\"Second window\",\"displayOrder\":1,\"version\":0}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"))
			.andExpect(jsonPath("$.current.name").value("First window"));
	}

	@Test
	void aStaleDivisionSaveIsRefusedWithTheLatestRecord() throws Exception {
		long year = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values ('2026-27', '2026-04-01', '2027-03-31') returning id",
				Long.class);
		long standard = jdbc.queryForObject("insert into standards (name, display_order) values ('Standard 1', 1) returning id",
				Long.class);
		long id = jdbc.queryForObject("insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A') returning id",
				Long.class, year, standard);
		String body = "{\"academicYearId\":%d,\"standardId\":%d,\"name\":\"%s\",\"version\":0}";
		api.put("/api/divisions/" + id, body.formatted(year, standard, "B")).andExpect(status().isOk());

		api.put("/api/divisions/" + id, body.formatted(year, standard, "C"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"))
			.andExpect(jsonPath("$.current.name").value("B"));
	}

	@Test
	void aStaleActiveChangeIsRefusedToo() throws Exception {
		long id = jdbc.queryForObject("insert into standards (name, display_order) values ('Standard 1', 1) returning id",
				Long.class);
		api.put("/api/standards/" + id + "/active", "{\"active\":false,\"version\":0}").andExpect(status().isOk());

		api.put("/api/standards/" + id + "/active", "{\"active\":true,\"version\":0}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"))
			.andExpect(jsonPath("$.current.active").value(false));
	}

	@Test
	void anEditWithoutAVersionIsRefused() throws Exception {
		long id = jdbc.queryForObject("insert into standards (name, display_order) values ('Standard 1', 1) returning id",
				Long.class);

		api.put("/api/standards/" + id, "{\"name\":\"Standard 1\",\"displayOrder\":1}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("version"));
	}

}
