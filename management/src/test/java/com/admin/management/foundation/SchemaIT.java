package com.admin.management.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;

@Covers("FR-001")
@Covers("FR-002")
@Covers("FR-006")
@Covers("FR-011")
@Covers("FR-017")
@Covers("FR-018")
@Covers("FR-024")
class SchemaIT extends IntegrationTest {

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void clearMasterData() {
		jdbc.update("delete from student_photos");
		jdbc.update("delete from student_enrollments");
		jdbc.update("delete from student_gr_numbers");
		jdbc.update("delete from students");
		jdbc.update("delete from divisions");
		jdbc.update("delete from standards");
		jdbc.update("delete from academic_years");
	}

	@Test
	void migrationSeedsSevenCategoriesWithAffectsFeesFalse() {
		List<String> codes = jdbc.queryForList("select code from student_categories order by id", String.class);
		Integer affecting = jdbc.queryForObject("select count(*) from student_categories where affects_fees",
				Integer.class);
		assertThat(codes).containsExactly("O/S", "CEW", "W", "STAFF", "S STAFF", "RTE", "CASUAL");
		assertThat(affecting).isZero();
	}

	@Test
	void academicYearWithEndNotAfterStartIsRejected() {
		assertSqlState("23514", () -> insertYear("bad-equal", "2026-04-01", "2026-04-01"));
		assertSqlState("23514", () -> insertYear("bad-before", "2026-04-02", "2026-04-01"));
	}

	@Test
	void overlappingAcademicYearsAreRejectedButAdjacentOnesAreAllowed() {
		insertYear("2026-27", "2026-04-01", "2027-03-31");
		assertSqlState("23P01", () -> insertYear("overlap", "2027-03-31", "2028-03-30"));
		insertYear("2027-28", "2027-04-01", "2028-03-31");
		assertThat(jdbc.queryForObject("select count(*) from academic_years", Integer.class)).isEqualTo(2);
	}

	@Test
	void duplicateDivisionIsRejected() {
		long year = insertYear("2026-27", "2026-04-01", "2027-03-31");
		long standard = jdbc.queryForObject(
				"insert into standards (name, display_order) values ('Standard 1', 1) returning id", Long.class);
		jdbc.update("insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A')", year, standard);
		assertSqlState("23505", () -> jdbc.update(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A')", year, standard));
	}

	@Test
	void referencedYearOrStandardCannotBeDeleted() {
		long year = insertYear("2026-27", "2026-04-01", "2027-03-31");
		long standard = jdbc.queryForObject(
				"insert into standards (name, display_order) values ('Standard 1', 1) returning id", Long.class);
		jdbc.update("insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A')", year, standard);
		assertSqlState("23503", () -> jdbc.update("delete from academic_years where id = ?", year));
		assertSqlState("23503", () -> jdbc.update("delete from standards where id = ?", standard));
	}

	@Test
	void standardStreamMustBeScienceOrCommerce() {
		jdbc.update("insert into standards (name, display_order, stream) values ('Std 11 Sci', 11, 'Science')");
		jdbc.update("insert into standards (name, display_order) values ('Std 1', 1)");
		assertSqlState("23514", () -> jdbc
				.update("insert into standards (name, display_order, stream) values ('Std 12 Arts', 12, 'Arts')"));
	}

	@Test
	void auditLogRowsCannotBeUpdatedOrDeleted() {
		jdbc.update("insert into audit_log (area, action, entity) values ('Security', 'Login', 'Session')");
		Integer before = jdbc.queryForObject("select count(*) from audit_log", Integer.class);
		assertThatThrownBy(() -> jdbc.update("update audit_log set area = 'Other'")).hasMessageContaining("append-only");
		assertThatThrownBy(() -> jdbc.update("delete from audit_log")).hasMessageContaining("append-only");
		assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isEqualTo(before);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where area = 'Other'", Integer.class)).isZero();
	}

	@Test
	void auditLogRejectsUnknownAction() {
		assertSqlState("23514",
				() -> jdbc.update("insert into audit_log (area, action, entity) values ('Security', 'Peek', 'x')"));
	}

	@Test
	void studentCheckConstraintsRejectBadValuesAndAcceptGoodOnes() {
		long standard = insertStandard();
		long category = firstCategory();
		long ok = insertStudent("GR-OK", standard, category, "Male", "9000000001", null, null, "2012-01-01", "2020-06-01");
		assertThat(ok).isPositive();
		// optional mobile 2 and Aadhaar accepted when well formed
		insertStudent("GR-OK2", standard, category, "Other", "9000000002", "9000000003", "123456789012", "2012-01-01",
				"2012-01-01");

		assertSqlState("23514", () -> insertStudent("GR-G", standard, category, "Boy", "9000000004", null, null,
				"2012-01-01", "2020-06-01"));
		assertSqlState("23514", () -> insertStudent("GR-M1", standard, category, "Male", "900000000", null, null,
				"2012-01-01", "2020-06-01"));
		assertSqlState("23514", () -> insertStudent("GR-M1b", standard, category, "Male", "90000000AB", null, null,
				"2012-01-01", "2020-06-01"));
		assertSqlState("23514", () -> insertStudent("GR-M2", standard, category, "Male", "9000000005", "12345", null,
				"2012-01-01", "2020-06-01"));
		assertSqlState("23514", () -> insertStudent("GR-A", standard, category, "Male", "9000000006", null,
				"12345678901", "2012-01-01", "2020-06-01"));
		assertSqlState("23514", () -> insertStudent("GR-D", standard, category, "Male", "9000000007", null, null,
				"2012-01-02", "2012-01-01"));
		assertSqlState("23514", () -> jdbc.update("update students set status = 'Gone' where id = ?", ok));
		assertSqlState("23514", () -> jdbc.update("update students set source = 'Paper' where id = ?", ok));
		assertThat(jdbc.queryForObject("select status || '/' || source from students where id = ?", String.class, ok))
				.isEqualTo("Active/Manual");
	}

	@Test
	void grNoIsUniqueIgnoringCaseAndSrNoIsUniqueAndNeverReused() {
		long standard = insertStandard();
		long category = firstCategory();
		long first = insertStudent("Gr-100", standard, category, "Male", "9000000011", null, null, "2012-01-01",
				"2020-06-01");
		assertSqlState("23505", () -> insertStudent("gr-100", standard, category, "Male", "9000000012", null, null,
				"2012-01-01", "2020-06-01"));
		long second = insertStudent("GR-101", standard, category, "Male", "9000000013", null, null, "2012-01-01",
				"2020-06-01");
		long srFirst = srNo(first);
		long srSecond = srNo(second);
		assertThat(srSecond).isGreaterThan(srFirst);
		assertSqlState("23505", () -> jdbc.update("update students set sr_no = ? where id = ?", srFirst, second));

		// a failed insert burns a number but the next student never gets an earlier one again
		assertSqlState("23505", () -> insertStudent("GR-101", standard, category, "Male", "9000000014", null, null,
				"2012-01-01", "2020-06-01"));
		long third = insertStudent("GR-102", standard, category, "Male", "9000000015", null, null, "2012-01-01",
				"2020-06-01");
		assertThat(srNo(third)).isGreaterThan(srSecond);

		jdbc.update("insert into student_gr_numbers (gr_key, student_id, assigned_at) values ('gr-100', ?, now())", first);
		assertSqlState("23505", () -> jdbc
				.update("insert into student_gr_numbers (gr_key, student_id, assigned_at) values ('gr-100', ?, now())", second));
	}

	@Test
	void enrollmentIsUniquePerStudentAndYearAndYearStatusIsChecked() {
		long standard = insertStandard();
		long category = firstCategory();
		long year = insertYear("2026-27", "2026-04-01", "2027-03-31");
		long year2 = insertYear("2027-28", "2027-04-01", "2028-03-31");
		long division = insertDivision(year, standard);
		long division2 = insertDivision(year2, standard);
		long student = insertStudent("GR-E1", standard, category, "Female", "9000000021", null, null, "2012-01-01",
				"2020-06-01");
		long enrollment = insertEnrollment(student, year, standard, division, category, "2026-04-01");
		insertEnrollment(student, year2, standard, division2, category, "2027-04-01");
		assertSqlState("23505", () -> insertEnrollment(student, year, standard, division, category, "2026-04-01"));
		assertSqlState("23514",
				() -> jdbc.update("update student_enrollments set year_status = 'Gone' where id = ?", enrollment));
		jdbc.update("update student_enrollments set year_status = 'Detained' where id = ?", enrollment);
		assertThat(jdbc.queryForObject("select year_status from student_enrollments where id = ?", String.class,
				enrollment)).isEqualTo("Detained");
		assertThat(jdbc.queryForObject("select year_status from student_enrollments where student_id = ? and academic_year_id = ?",
				String.class, student, year2)).isEqualTo("Studying");
	}

	@Test
	void foreignKeysRestrictAndNoStudentTableCascades() {
		long standard = insertStandard();
		long category = firstCategory();
		long year = insertYear("2026-27", "2026-04-01", "2027-03-31");
		long division = insertDivision(year, standard);
		long student = insertStudent("GR-F1", standard, category, "Male", "9000000031", null, null, "2012-01-01",
				"2020-06-01");
		insertEnrollment(student, year, standard, division, category, "2026-04-01");
		jdbc.update("insert into student_gr_numbers (gr_key, student_id, assigned_at) values ('gr-f1', ?, now())", student);
		jdbc.update("insert into student_photos (student_id, content, content_type, size_bytes, updated_at)"
				+ " values (?, ?, 'image/jpeg', 3, now())", student, new byte[] { 1, 2, 3 });

		assertSqlState("23503", () -> jdbc.update("delete from students where id = ?", student));
		assertSqlState("23503", () -> jdbc.update("delete from divisions where id = ?", division));
		assertSqlState("23503", () -> jdbc.update("delete from academic_years where id = ?", year));
		assertSqlState("23503", () -> jdbc.update("delete from standards where id = ?", standard));
		assertSqlState("23503", () -> jdbc.update("delete from student_categories where id = ?", category));
		// children with a missing parent are refused too
		assertSqlState("23503", () -> insertEnrollment(student + 1000, year, standard, division, category, "2026-04-01"));
		assertSqlState("23503", () -> jdbc.update(
				"insert into student_gr_numbers (gr_key, student_id, assigned_at) values ('gr-none', ?, now())", student + 1000));

		Integer cascades = jdbc.queryForObject("""
				select count(*) from pg_constraint c
				where c.contype = 'f' and c.confdeltype = 'c'
				  and c.conrelid::regclass::text in ('students', 'student_gr_numbers', 'student_enrollments', 'student_photos')
				""", Integer.class);
		assertThat(cascades).isZero();
		Integer restricting = jdbc.queryForObject("""
				select count(*) from pg_constraint c
				where c.contype = 'f' and c.confdeltype <> 'r'
				  and c.conrelid::regclass::text in ('students', 'student_gr_numbers', 'student_enrollments', 'student_photos')
				""", Integer.class);
		assertThat(restricting).isZero();
	}

	@Test
	void auditLogHasEntityAtIndex() {
		Integer indexes = jdbc.queryForObject("""
				select count(*) from pg_indexes
				where tablename = 'audit_log' and indexdef like '%(entity, at)%'
				""", Integer.class);
		assertThat(indexes).isEqualTo(1);
	}

	private long insertStandard() {
		return jdbc.queryForObject(
				"insert into standards (name, display_order) values ('Standard 1', 1) returning id",
				Long.class);
	}

	private long firstCategory() {
		return jdbc.queryForObject("select min(id) from student_categories", Long.class);
	}

	private long insertDivision(long year, long standard) {
		return jdbc.queryForObject(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A') returning id", Long.class,
				year, standard);
	}

	private long insertStudent(String grNo, long standard, long category, String gender, String mobile1, String mobile2,
			String aadhaar, String dob, String admission) {
		return jdbc.queryForObject("""
				insert into students (gr_no, surname, first_name, gender, dob, mobile_1, mobile_2, address, aadhaar,
				    category_id, admission_date, admission_standard_id)
				values (?, 'Testsurname', 'Alpha', ?, ?::date, ?, ?, 'Test address', ?, ?, ?::date, ?) returning id
				""", Long.class, grNo, gender, dob, mobile1, mobile2, aadhaar, category, admission, standard);
	}

	private long insertEnrollment(long student, long year, long standard, long division, long category, String start) {
		return jdbc.queryForObject("""
				insert into student_enrollments (student_id, academic_year_id, standard_id, division_id, category_id,
				    year_start_date)
				values (?, ?, ?, ?, ?, ?::date) returning id
				""", Long.class, student, year, standard, division, category, start);
	}

	private long srNo(long student) {
		return jdbc.queryForObject("select sr_no from students where id = ?", Long.class, student);
	}

	private long insertYear(String name, String start, String end) {
		return jdbc.queryForObject("""
				insert into academic_years (name, start_date, end_date)
				values (?, ?::date, ?::date) returning id
				""", Long.class, name, start, end);
	}

	private static void assertSqlState(String expected, Runnable action) {
		assertThatThrownBy(action::run).isInstanceOf(DataAccessException.class).satisfies(e -> {
			Throwable cause = ((DataAccessException) e).getMostSpecificCause();
			assertThat(cause).isInstanceOf(SQLException.class);
			assertThat(((SQLException) cause).getSQLState()).isEqualTo(expected);
		});
	}

}
