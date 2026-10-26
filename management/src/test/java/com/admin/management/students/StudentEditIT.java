package com.admin.management.students;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/** Edit details, GR No. correction, Mark as left and class record changes: audit, reserved numbers, stale saves. */
@Covers("FR-004")
@Covers("FR-006")
@Covers("FR-012")
@Covers("FR-017")
@Covers("FR-018")
class StudentEditIT extends IntegrationTest {

	static final String AADHAAR = "987654321098";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	SignedInClient api;

	long currentYear;

	long earlierYear;

	long standardId;

	long otherStandardId;

	long divisionId;

	long otherDivisionId;

	long earlierDivisionId;

	long categoryId;

	long otherCategoryId;

	@BeforeEach
	void setUp() throws Exception {
		clock.setDate(LocalDate.of(2026, 10, 25));
		restoreAuditWrites();
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
		otherStandardId = jdbc.queryForObject(
				"insert into standards (name, display_order) values ('Standard 2', 2) returning id", Long.class);
		divisionId = division(currentYear, standardId, "A");
		otherDivisionId = division(currentYear, otherStandardId, "B");
		earlierDivisionId = division(earlierYear, standardId, "C");
		categoryId = jdbc.queryForObject("select min(id) from student_categories", Long.class);
		otherCategoryId = jdbc.queryForObject("select max(id) from student_categories", Long.class);
		api = SignedInClient.of(mvc);
	}

	@AfterEach
	void tearDown() {
		restoreAuditWrites();
	}

	private long division(long year, long standard, String name) {
		return jdbc.queryForObject(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, ?) returning id", Long.class,
				year, standard, name);
	}

	private void breakAuditWrites() {
		jdbc.execute("create or replace function fail_audit_insert() returns trigger as $$ begin raise exception 'audit write failed'; end; $$ language plpgsql");
		jdbc.execute("create trigger trg_fail_audit_insert before insert on audit_log for each row execute function fail_audit_insert()");
	}

	private void restoreAuditWrites() {
		jdbc.execute("drop trigger if exists trg_fail_audit_insert on audit_log");
	}

	private long register(String grNo) throws Exception {
		String body = """
				{"grNo":"%s","surname":"Testsurname","firstName":"Alpha","middleName":"Middle","gender":"Male",
				 "dob":"2012-01-01","mobile1":"9000000001","address":"Test address","aadhaar":"%s",
				 "categoryId":%d,"admissionDate":"2020-06-01","admissionStandardId":%d,"standardId":%d,"divisionId":%d}"""
			.formatted(grNo, AADHAAR, categoryId, standardId, standardId, divisionId);
		String json = api.post("/api/students", body).andReturn().getResponse().getContentAsString();
		return Long.parseLong(json.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
	}

	private long version(long id) {
		return jdbc.queryForObject("select version from students where id = ?", Long.class, id);
	}

	private long recordVersion(long studentId, long yearId) {
		return jdbc.queryForObject("select version from student_enrollments where student_id = ? and academic_year_id = ?",
				Long.class, studentId, yearId);
	}

	private String edit(String grNo, String address, String aadhaar, long version) {
		return """
				{"version":%d,"grNo":"%s","surname":"Testsurname","firstName":"Alpha","middleName":"Middle",
				 "gender":"Male","dob":"2012-01-01","mobile1":"9000000001","address":"%s","aadhaar":%s,
				 "admissionDate":"2020-06-01","admissionStandardId":%d}"""
			.formatted(version, grNo, address, aadhaar == null ? "null" : "\"" + aadhaar + "\"", standardId);
	}

	private List<Map<String, Object>> auditOf(String entity) {
		return jdbc.queryForList("select action, changes::text c from audit_log where entity = ? order by id", entity);
	}

	@Test
	void anEditSavesTheDetailsAndWritesOneAuditEntryWithBeforeAndAfter() throws Exception {
		long id = register("ED-1");

		api.put("/api/students/" + id, edit("ED-1", "New test address", AADHAAR, version(id)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.address").value("New test address"));

		List<Map<String, Object>> entries = auditOf("Student#" + id);
		assertThat(entries).hasSize(2);
		assertThat(entries.get(1)).containsEntry("action", "Edit");
		assertThat(entries.get(1).get("c").toString()).contains("Test address").contains("New test address");
	}

	@Test
	void aSaveThatChangesNothingWritesNoEntry() throws Exception {
		long id = register("ED-2");

		api.put("/api/students/" + id, edit("ED-2", "Test address", AADHAAR, version(id))).andExpect(status().isOk());

		assertThat(auditOf("Student#" + id)).hasSize(1);
	}

	@Test
	void anAadhaarEditIsAuditedMaskedAndNeverInFull() throws Exception {
		long id = register("ED-3");

		api.put("/api/students/" + id, edit("ED-3", "Test address", "111122223333", version(id)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.aadhaar").value("********3333"));

		String changes = auditOf("Student#" + id).get(1).get("c").toString();
		assertThat(changes).contains("********3333").contains("********1098").doesNotContain("111122223333")
			.doesNotContain(AADHAAR);
	}

	@Test
	void aGrNoCorrectionReservesTheNewNumberAndKeepsTheOldOneReservedForEveryone() throws Exception {
		long id = register("ED-4");
		long other = register("ED-5");

		api.put("/api/students/" + id, edit("ED-4B", "Test address", AADHAAR, version(id)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.grNo").value("ED-4B"));

		assertThat(jdbc.queryForObject("select count(*) from student_gr_numbers where student_id = ?", Integer.class, id))
			.isEqualTo(2);
		// the old number is refused for a new student, for another student's correction and for the same student again
		api.post("/api/students", """
				{"grNo":"ed-4","surname":"X","firstName":"Y","gender":"Male","dob":"2012-01-01","mobile1":"9000000001",
				 "address":"A","categoryId":%d,"admissionDate":"2020-06-01","admissionStandardId":%d,"standardId":%d,
				 "divisionId":%d}""".formatted(categoryId, standardId, standardId, divisionId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("grNo"));
		api.put("/api/students/" + other, edit("ED-4", "Test address", AADHAAR, version(other)))
			.andExpect(status().isConflict());
		api.put("/api/students/" + id, edit("ED-4", "Test address", AADHAAR, version(id)))
			.andExpect(status().isConflict());
	}

	@Test
	void aChangeOfCapitalsInTheOwnGrNoNeedsNoNewReservation() throws Exception {
		long id = register("ED-6");

		api.put("/api/students/" + id, edit("ed-6", "Test address", AADHAAR, version(id)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.grNo").value("ed-6"));
	}

	@Test
	void anEditWithAnInvalidValueIsRefusedWithFieldErrorsAndSavesNothing() throws Exception {
		long id = register("ED-7");

		api.put("/api/students/" + id, edit("ED-7", "Test address", "12345", version(id)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("aadhaar"))
			.andExpect(content().string(not(containsString("12345"))));

		assertThat(auditOf("Student#" + id)).hasSize(1);
	}

	@Test
	void markLeftChangesActiveToLeftOnceAndNeverBack() throws Exception {
		long id = register("ED-8");

		api.put("/api/students/" + id + "/left", "{\"version\":" + version(id) + "}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("Left"));

		assertThat(auditOf("Student#" + id).get(1).get("c").toString()).contains("Left").contains("Active");
		api.put("/api/students/" + id + "/left", "{\"version\":" + version(id) + "}")
			.andExpect(status().isUnprocessableEntity());
		// an edit cannot set the status back
		api.put("/api/students/" + id,
				edit("ED-8", "Test address", AADHAAR, version(id)).replace("\"gender\"", "\"status\":\"Active\",\"gender\""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("Left"));
		assertThat(jdbc.queryForObject("select count(*) from students where id = ?", Integer.class, id)).isEqualTo(1);
	}

	@Test
	void aStaleSaveOnTheStudentIsRefusedWithTheLatestRecordAndOverwritesNothing() throws Exception {
		long id = register("ED-9");
		long first = version(id);
		api.put("/api/students/" + id, edit("ED-9", "First window address", AADHAAR, first)).andExpect(status().isOk());

		api.put("/api/students/" + id, edit("ED-9", "Second window address", AADHAAR, first))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"))
			.andExpect(jsonPath("$.current.address").value("First window address"));

		assertThat(jdbc.queryForObject("select address from students where id = ?", String.class, id))
			.isEqualTo("First window address");
	}

	@Test
	void aStaleMarkAsLeftIsRefused() throws Exception {
		long id = register("ED-10");
		long first = version(id);
		api.put("/api/students/" + id, edit("ED-10", "Changed address", AADHAAR, first)).andExpect(status().isOk());

		api.put("/api/students/" + id + "/left", "{\"version\":" + first + "}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"));

		assertThat(jdbc.queryForObject("select status from students where id = ?", String.class, id)).isEqualTo("Active");
	}

	@Test
	void aClassRecordChangeSavesAnAuditEntryAndNeverAddsASecondRow() throws Exception {
		long id = register("ED-11");

		api.put("/api/students/" + id + "/enrollments/" + currentYear, """
				{"version":%d,"standardId":%d,"divisionId":%d,"categoryId":%d}"""
			.formatted(recordVersion(id, currentYear), otherStandardId, otherDivisionId, otherCategoryId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stdDiv").value("Standard 2-B"));

		assertThat(jdbc.queryForObject("select count(*) from student_enrollments where student_id = ?", Integer.class, id))
			.isEqualTo(1);
		long recordId = jdbc.queryForObject("select id from student_enrollments where student_id = ?", Long.class, id);
		List<Map<String, Object>> entries = auditOf("StudentEnrollment#" + recordId);
		assertThat(entries).hasSize(2);
		assertThat(entries.get(1)).containsEntry("action", "Edit");
	}

	@Test
	void aDivisionOfAnotherStandardOrYearIsRefused() throws Exception {
		long id = register("ED-12");
		long version = recordVersion(id, currentYear);

		api.put("/api/students/" + id + "/enrollments/" + currentYear, """
				{"version":%d,"standardId":%d,"divisionId":%d,"categoryId":%d}"""
			.formatted(version, standardId, otherDivisionId, categoryId))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("divisionId"));
		api.put("/api/students/" + id + "/enrollments/" + currentYear, """
				{"version":%d,"standardId":%d,"divisionId":%d,"categoryId":%d}"""
			.formatted(version, standardId, earlierDivisionId, categoryId))
			.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void anInactiveValueIsRefusedUnlessTheRecordAlreadyHoldsIt() throws Exception {
		long id = register("ED-13");
		jdbc.update("update standards set active = false where id = ?", otherStandardId);
		long version = recordVersion(id, currentYear);

		api.put("/api/students/" + id + "/enrollments/" + currentYear, """
				{"version":%d,"standardId":%d,"divisionId":%d,"categoryId":%d}"""
			.formatted(version, otherStandardId, otherDivisionId, categoryId))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("standardId"));

		jdbc.update("update standards set active = false where id = ?", standardId);
		api.put("/api/students/" + id + "/enrollments/" + currentYear, """
				{"version":%d,"standardId":%d,"divisionId":%d,"categoryId":%d}"""
			.formatted(version, standardId, divisionId, otherCategoryId))
			.andExpect(status().isOk());
	}

	@Test
	void aPastYearClassRecordCanBeChangedAndAStaleOneIsRefused() throws Exception {
		long id = register("ED-14");
		jdbc.update("""
				insert into student_enrollments (student_id, academic_year_id, standard_id, division_id, category_id,
				    year_start_date, year_status)
				values (?, ?, ?, ?, ?, '2025-04-01', 'Promoted')
				""", id, earlierYear, standardId, earlierDivisionId, categoryId);
		long version = recordVersion(id, earlierYear);

		api.put("/api/students/" + id + "/enrollments/" + earlierYear, """
				{"version":%d,"standardId":%d,"divisionId":%d,"categoryId":%d}"""
			.formatted(version, standardId, earlierDivisionId, otherCategoryId))
			.andExpect(status().isOk());
		api.put("/api/students/" + id + "/enrollments/" + earlierYear, """
				{"version":%d,"standardId":%d,"divisionId":%d,"categoryId":%d}"""
			.formatted(version, standardId, earlierDivisionId, categoryId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"));

		assertThat(jdbc.queryForObject(
				"select category_id from student_enrollments where student_id = ? and academic_year_id = ?", Long.class,
				id, earlierYear)).isEqualTo(otherCategoryId);
		api.put("/api/students/" + id + "/enrollments/999999", """
				{"version":1,"standardId":%d,"divisionId":%d,"categoryId":%d}"""
			.formatted(standardId, divisionId, categoryId))
			.andExpect(status().isNotFound());
	}

	@Test
	void whenTheAuditWriteFailsNothingIsSaved() throws Exception {
		long id = register("ED-15");
		long studentVersion = version(id);
		long recordVersion = recordVersion(id, currentYear);
		breakAuditWrites();

		assertThatThrownBy(() -> api.put("/api/students/" + id, edit("ED-15B", "Changed address", AADHAAR, studentVersion)))
			.hasMessageContaining("audit write failed");
		assertThatThrownBy(() -> api.put("/api/students/" + id + "/left", "{\"version\":" + studentVersion + "}"))
			.hasMessageContaining("audit write failed");
		String change = "{\"version\":%d,\"standardId\":%d,\"divisionId\":%d,\"categoryId\":%d}"
			.formatted(recordVersion, otherStandardId, otherDivisionId, categoryId);
		assertThatThrownBy(() -> api.put("/api/students/" + id + "/enrollments/" + currentYear, change))
			.hasMessageContaining("audit write failed");

		Map<String, Object> row = jdbc.queryForMap("select gr_no, address, status, version from students where id = ?", id);
		assertThat(row).containsEntry("gr_no", "ED-15").containsEntry("address", "Test address")
			.containsEntry("status", "Active");
		assertThat(version(id)).isEqualTo(studentVersion);
		assertThat(jdbc.queryForObject("select count(*) from student_gr_numbers where student_id = ?", Integer.class, id))
			.isEqualTo(1);
		assertThat(recordVersion(id, currentYear)).isEqualTo(recordVersion);
	}

}
