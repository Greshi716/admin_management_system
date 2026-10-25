package com.admin.management.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

@Covers("FR-021")
@Covers("FR-023")
class MasterDataAuditIT extends IntegrationTest {

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

	private List<Map<String, Object>> entries(String entityPrefix) {
		return jdbc.queryForList("""
				select a.area, a.action, a.entity, a.changes::text as changes, a.user_id
				from audit_log a where a.entity like ? order by a.id
				""", entityPrefix + "#%");
	}

	@Test
	void createEditAndDeleteAYearEachWriteOneEntryWithOldAndNewValues() throws Exception {
		api.post("/api/academic-years", "{\"name\":\"2026-27\",\"startDate\":\"2026-04-01\",\"endDate\":\"2027-03-31\"}")
			.andExpect(status().isCreated());
		long id = jdbc.queryForObject("select id from academic_years", Long.class);
		assertThat(entries("AcademicYear")).hasSize(1);
		assertThat(entries("AcademicYear").get(0)).containsEntry("area", "Master data")
			.containsEntry("action", "Create")
			.containsEntry("entity", "AcademicYear#" + id);
		assertThat(entries("AcademicYear").get(0).get("user_id")).isNotNull();

		api.put("/api/academic-years/" + id,
				"{\"name\":\"2026-27x\",\"startDate\":\"2026-04-01\",\"endDate\":\"2027-03-31\",\"version\":0}")
			.andExpect(status().isOk());
		Map<String, Object> edit = entries("AcademicYear").get(1);
		assertThat(edit).containsEntry("action", "Edit");
		assertThat((String) edit.get("changes")).contains("2026-27x").contains("2026-27").contains("old").contains("new");

		api.delete("/api/academic-years/" + id).andExpect(status().isNoContent());
		assertThat(entries("AcademicYear")).hasSize(3);
		assertThat(entries("AcademicYear").get(2)).containsEntry("action", "Delete");
		assertThat((String) entries("AcademicYear").get(2).get("changes")).contains("2026-27x");
	}

	@Test
	void standardsAndDivisionsAreAuditedIncludingInactive() throws Exception {
		api.post("/api/standards", "{\"name\":\"Standard 1\",\"displayOrder\":1}").andExpect(status().isCreated());
		long standard = jdbc.queryForObject("select id from standards", Long.class);
		long year = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values ('2026-27', '2026-04-01', '2027-03-31') returning id",
				Long.class);
		api.post("/api/divisions", "{\"academicYearId\":%d,\"standardId\":%d,\"name\":\"A\"}".formatted(year, standard))
			.andExpect(status().isCreated());
		long division = jdbc.queryForObject("select id from divisions", Long.class);

		api.put("/api/standards/" + standard + "/active", "{\"active\":false,\"version\":0}").andExpect(status().isOk());
		api.put("/api/divisions/" + division + "/active", "{\"active\":false,\"version\":0}").andExpect(status().isOk());

		assertThat(entries("Standard")).extracting((e) -> e.get("action")).containsExactly("Create", "Edit");
		assertThat((String) entries("Standard").get(1).get("changes")).contains("active");
		assertThat(entries("Division")).extracting((e) -> e.get("action")).containsExactly("Create", "Edit");
		assertThat(entries("Division")).allSatisfy((e) -> assertThat(e).containsEntry("area", "Master data"));
	}

	@Test
	void aRefusedChangeLeavesNoEntry() throws Exception {
		api.post("/api/standards", "{\"name\":\"Standard 1\",\"displayOrder\":1}").andExpect(status().isCreated());
		long standard = jdbc.queryForObject("select id from standards", Long.class);
		long year = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values ('2026-27', '2026-04-01', '2027-03-31') returning id",
				Long.class);
		api.post("/api/divisions", "{\"academicYearId\":%d,\"standardId\":%d,\"name\":\"A\"}".formatted(year, standard))
			.andExpect(status().isCreated());
		int before = jdbc.queryForObject("select count(*) from audit_log where area = 'Master data'", Integer.class);

		api.post("/api/standards", "{\"name\":\"Standard 1\",\"displayOrder\":2}").andExpect(status().isConflict());
		api.delete("/api/standards/" + standard).andExpect(status().isConflict());
		api.put("/api/standards/" + standard, "{\"name\":\"X\",\"displayOrder\":1,\"version\":99}")
			.andExpect(status().isConflict());

		assertThat(jdbc.queryForObject("select count(*) from audit_log where area = 'Master data'", Integer.class))
			.isEqualTo(before);
	}

}
