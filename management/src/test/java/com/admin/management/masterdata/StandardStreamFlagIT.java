package com.admin.management.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.masterdata.api.MasterDataLookup.StandardOption;
import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

@Covers("FR-012")
@Covers("FR-005")
class StandardStreamFlagIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MasterDataLookup lookup;

	SignedInClient api;

	@BeforeEach
	void signInAndEmpty() throws Exception {
		jdbc.update("delete from promotion_corrections");
		jdbc.update("delete from promotion_run_items");
		jdbc.update("delete from promotion_runs");
		jdbc.update("delete from student_enrollments");
		jdbc.update("delete from fee_accounts");
		jdbc.update("delete from fee_rows");
		jdbc.update("delete from fee_year_status");
		jdbc.update("delete from divisions");
		jdbc.update("delete from standards");
		jdbc.update("delete from academic_years");
		api = SignedInClient.of(mvc);
	}

	@Test
	void createStandardWithUsesStreamFlag() throws Exception {
		api.post("/api/standards", "{\"name\":\"Standard 11\",\"displayOrder\":11,\"usesStream\":true}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Standard 11"))
			.andExpect(jsonPath("$.displayOrder").value(11))
			.andExpect(jsonPath("$.usesStream").value(true))
			.andExpect(jsonPath("$.active").value(true));

		Boolean inDb = jdbc.queryForObject("select uses_stream from standards where name = 'Standard 11'", Boolean.class);
		assertThat(inDb).isTrue();
	}

	@Test
	void toggleSavedAndAudited() throws Exception {
		api.post("/api/standards", "{\"name\":\"Standard 10\",\"displayOrder\":10,\"usesStream\":false}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.usesStream").value(false));

		long id = jdbc.queryForObject("select id from standards where name = 'Standard 10'", Long.class);

		api.put("/api/standards/" + id, "{\"name\":\"Standard 10\",\"displayOrder\":10,\"usesStream\":true,\"version\":0}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.usesStream").value(true));

		Boolean inDb = jdbc.queryForObject("select uses_stream from standards where id = ?", Boolean.class, id);
		assertThat(inDb).isTrue();

		String auditChanges = jdbc.queryForObject(
				"select changes::text from audit_log where area = 'Master data' and action = 'Edit' and entity = ? order by id desc limit 1",
				String.class, "Standard#" + id);
		assertThat(auditChanges).contains("usesStream");
	}

	@Test
	void inUseStandardCanStillChangeTheFlag() throws Exception {
		long yearId = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values ('2026-27', '2026-06-01', '2027-04-30') returning id",
				Long.class);
		long stdId = jdbc.queryForObject(
				"insert into standards (name, display_order, uses_stream, active) values ('Standard 9', 9, false, true) returning id",
				Long.class);
		jdbc.update("insert into divisions (academic_year_id, standard_id, name, active) values (?, ?, 'A', true)",
				yearId, stdId);

		api.put("/api/standards/" + stdId, "{\"name\":\"Standard 9\",\"displayOrder\":9,\"usesStream\":true,\"version\":0}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.usesStream").value(true));

		Boolean inDb = jdbc.queryForObject("select uses_stream from standards where id = ?", Boolean.class, stdId);
		assertThat(inDb).isTrue();
	}

	@Test
	void masterDataLookupStandardOptionCarriesUsesStream() {
		long yearId = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values ('2026-27', '2026-06-01', '2027-04-30') returning id",
				Long.class);
		long std10 = jdbc.queryForObject(
				"insert into standards (name, display_order, uses_stream, active) values ('Standard 10', 10, false, true) returning id",
				Long.class);
		long std11 = jdbc.queryForObject(
				"insert into standards (name, display_order, uses_stream, active) values ('Standard 11', 11, true, true) returning id",
				Long.class);

		List<StandardOption> pickListStandards = lookup.pickLists(yearId).standards();
		StandardOption opt10 = pickListStandards.stream().filter(s -> s.id().equals(std10)).findFirst().orElseThrow();
		StandardOption opt11 = pickListStandards.stream().filter(s -> s.id().equals(std11)).findFirst().orElseThrow();

		assertThat(opt10.usesStream()).isFalse();
		assertThat(opt11.usesStream()).isTrue();

		List<StandardOption> filterStandards = lookup.filterOptions().standards();
		StandardOption f10 = filterStandards.stream().filter(s -> s.id().equals(std10)).findFirst().orElseThrow();
		StandardOption f11 = filterStandards.stream().filter(s -> s.id().equals(std11)).findFirst().orElseThrow();

		assertThat(f10.usesStream()).isFalse();
		assertThat(f11.usesStream()).isTrue();
	}

}
