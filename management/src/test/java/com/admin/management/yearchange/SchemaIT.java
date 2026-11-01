package com.admin.management.yearchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;

/**
 * Validates the schema alterations and new tables introduced in V9__year_change.sql:
 * stream on student_enrollments, uses_stream on standards, promotion_runs, promotion_run_items,
 * promotion_corrections, admission_import_batches, and admission_requests.
 */
@Covers("FR-016")
@Covers("FR-017")
@Covers("FR-025")
@Covers("FR-030")
@Covers("FR-031")
@Covers("FR-035")
class SchemaIT extends IntegrationTest {

	private static final String CHECK_VIOLATION = "23514";
	private static final String UNIQUE_VIOLATION = "23505";

	@Autowired
	JdbcTemplate jdbc;

	Setup setup;
	long adminId;
	int studentSeq = 1000;

	private java.sql.SQLException refused(Runnable statement) {
		DataAccessException ex = assertThrows(DataAccessException.class, statement::run);
		Throwable root = ex;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		return (java.sql.SQLException) root;
	}

	private void assertRefused(String sqlState, String text, Runnable statement) {
		java.sql.SQLException ex = refused(statement);
		assertThat(ex.getSQLState()).as(ex.getMessage()).isEqualTo(sqlState);
		assertThat(ex.getMessage()).contains(text);
	}

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "SchemaY9", LocalDate.of(2030, 4, 1), 1);
		adminId = jdbc.queryForObject("select id from users order by id limit 1", Long.class);
	}

	record StudentFixture(long studentId, long enrollmentId) {}

	private StudentFixture createStudent() {
		int n = ++studentSeq;
		long enrollmentId = FeeTestData.student(jdbc, setup, 80000 + n, 0, 0);
		long studentId = jdbc.queryForObject(
				"select student_id from student_enrollments where id = ?", Long.class, enrollmentId);
		return new StudentFixture(studentId, enrollmentId);
	}

	@Test
	void streamColumnOnStudentEnrollments() {
		StudentFixture s = createStudent();

		// NULL stream is valid
		String stream = jdbc.queryForObject(
				"select stream from student_enrollments where id = ?", String.class, s.enrollmentId());
		assertThat(stream).isNull();

		// Science and Commerce are valid
		jdbc.update("update student_enrollments set stream = 'Science' where id = ?", s.enrollmentId());
		assertThat(jdbc.queryForObject("select stream from student_enrollments where id = ?", String.class, s.enrollmentId()))
				.isEqualTo("Science");

		jdbc.update("update student_enrollments set stream = 'Commerce' where id = ?", s.enrollmentId());
		assertThat(jdbc.queryForObject("select stream from student_enrollments where id = ?", String.class, s.enrollmentId()))
				.isEqualTo("Commerce");

		// Other values violate CHECK
		assertRefused(CHECK_VIOLATION, "ck_student_enrollments_stream", () ->
				jdbc.update("update student_enrollments set stream = 'Arts' where id = ?", s.enrollmentId()));
	}

	@Test
	void usesStreamColumnOnStandards() {
		long stdId = setup.standard(0);
		Boolean usesStream = jdbc.queryForObject(
				"select uses_stream from standards where id = ?", Boolean.class, stdId);
		assertThat(usesStream).isFalse();

		jdbc.update("update standards set uses_stream = true where id = ?", stdId);
		assertThat(jdbc.queryForObject("select uses_stream from standards where id = ?", Boolean.class, stdId))
				.isTrue();

		jdbc.update("update standards set uses_stream = false where id = ?", stdId);
	}

	@Test
	void promotionRunsAndItemsEnforceChecksAndUniqueness() {
		StudentFixture s1 = createStudent();

		long runId = jdbc.queryForObject(
				"insert into promotion_runs (from_year_id, to_year_id, standard_id, done_by, promoted_count, carried_total) " +
				"values (?, ?, ?, ?, 1, 1500.00) returning id",
				Long.class, setup.yearId(), setup.yearId(), setup.standard(0), adminId);
		assertThat(runId).isPositive();

		// Insert valid item
		long itemId = jdbc.queryForObject(
				"insert into promotion_run_items (run_id, student_id, from_enrollment_id, outcome, carried_amount, brought_forward_before, brought_forward_after) " +
				"values (?, ?, ?, 'PROMOTED', 1500.00, 0.00, 1500.00) returning id",
				Long.class, runId, s1.studentId(), s1.enrollmentId());
		assertThat(itemId).isPositive();

		// Outcome CHECK violation
		StudentFixture s2 = createStudent();
		assertRefused(CHECK_VIOLATION, "ck_promotion_run_items_outcome", () ->
				jdbc.update("insert into promotion_run_items (run_id, student_id, from_enrollment_id, outcome) " +
						"values (?, ?, ?, 'SKIPPED')", runId, s2.studentId(), s2.enrollmentId()));

		// Duplicate from_enrollment_id violation
		assertRefused(UNIQUE_VIOLATION, "uq_promotion_run_items_from_enrollment", () ->
				jdbc.update("insert into promotion_run_items (run_id, student_id, from_enrollment_id, outcome) " +
						"values (?, ?, ?, 'DETAINED')", runId, s1.studentId(), s1.enrollmentId()));
	}

	@Test
	void promotionTablesAreAppendOnly() {
		StudentFixture s = createStudent();
		long runId = jdbc.queryForObject(
				"insert into promotion_runs (from_year_id, to_year_id, standard_id, done_by) " +
				"values (?, ?, ?, ?) returning id",
				Long.class, setup.yearId(), setup.yearId(), setup.standard(0), adminId);
		long itemId = jdbc.queryForObject(
				"insert into promotion_run_items (run_id, student_id, from_enrollment_id, outcome) " +
				"values (?, ?, ?, 'DETAINED') returning id",
				Long.class, runId, s.studentId(), s.enrollmentId());
		long corrId = jdbc.queryForObject(
				"insert into promotion_corrections (item_id, reason, done_by, amount_reversed) " +
				"values (?, 'Error fixed', 'dev_user', 0.00) returning id",
				Long.class, itemId);

		// UPDATE refused on all three
		assertThrows(DataAccessException.class, () ->
				jdbc.update("update promotion_runs set promoted_count = 5 where id = ?", runId));
		assertThrows(DataAccessException.class, () ->
				jdbc.update("update promotion_run_items set outcome = 'LEFT' where id = ?", itemId));
		assertThrows(DataAccessException.class, () ->
				jdbc.update("update promotion_corrections set reason = 'Changed' where id = ?", corrId));

		// DELETE refused on all three
		assertThrows(DataAccessException.class, () ->
				jdbc.update("delete from promotion_corrections where id = ?", corrId));
		assertThrows(DataAccessException.class, () ->
				jdbc.update("delete from promotion_run_items where id = ?", itemId));
		assertThrows(DataAccessException.class, () ->
				jdbc.update("delete from promotion_runs where id = ?", runId));
	}

	@Test
	void promotionCorrectionsItemUniqueAndNonBlankReason() {
		StudentFixture s = createStudent();
		long runId = jdbc.queryForObject(
				"insert into promotion_runs (from_year_id, to_year_id, standard_id, done_by) " +
				"values (?, ?, ?, ?) returning id",
				Long.class, setup.yearId(), setup.yearId(), setup.standard(0), adminId);
		long itemId = jdbc.queryForObject(
				"insert into promotion_run_items (run_id, student_id, from_enrollment_id, outcome) " +
				"values (?, ?, ?, 'LEFT') returning id",
				Long.class, runId, s.studentId(), s.enrollmentId());

		jdbc.update("insert into promotion_corrections (item_id, reason, done_by, amount_reversed) " +
				"values (?, 'Reversed Left', 'admin', 0.00)", itemId);

		// Duplicate item_id refused
		assertRefused(UNIQUE_VIOLATION, "uq_promotion_corrections_item", () ->
				jdbc.update("insert into promotion_corrections (item_id, reason, done_by) " +
						"values (?, 'Again', 'admin')", itemId));

		// Blank reason refused
		StudentFixture s2 = createStudent();
		long itemId2 = jdbc.queryForObject(
				"insert into promotion_run_items (run_id, student_id, from_enrollment_id, outcome) " +
				"values (?, ?, ?, 'PROMOTED') returning id",
				Long.class, runId, s2.studentId(), s2.enrollmentId());

		assertRefused(CHECK_VIOLATION, "ck_promotion_corrections_reason", () ->
				jdbc.update("insert into promotion_corrections (item_id, reason, done_by) " +
						"values (?, '   ', 'admin')", itemId2));
	}

	@Test
	void admissionRequestsLifecycleAndGuards() {
		long batchId = jdbc.queryForObject(
				"insert into admission_import_batches (file_name, uploaded_by, rows_total, rows_added) " +
				"values ('sample.xlsx', ?, 10, 10) returning id",
				Long.class, adminId);

		// Insert valid pending request
		long reqId = jdbc.queryForObject(
				"insert into admission_requests (form_data, import_batch_id, import_key, status) " +
				"values ('{\"name\":\"John\"}'::jsonb, ?, 'key-101', 'Pending') returning id",
				Long.class, batchId);
		assertThat(reqId).isPositive();

		// Unique import_key
		assertRefused(UNIQUE_VIOLATION, "uq_admission_requests_import_key", () ->
				jdbc.update("insert into admission_requests (form_data, import_batch_id, import_key) " +
						"values ('{\"name\":\"Jane\"}'::jsonb, ?, 'key-101')", batchId));

		// Status check
		assertRefused(CHECK_VIOLATION, "ck_admission_requests_status", () ->
				jdbc.update("update admission_requests set status = 'Unknown' where id = ?", reqId));

		// Approved requires student_id and reviewed_by
		assertRefused(CHECK_VIOLATION, "ck_admission_requests_approved", () ->
				jdbc.update("update admission_requests set status = 'Approved' where id = ?", reqId));

		// Rejected requires non-blank remarks
		assertRefused(CHECK_VIOLATION, "ck_admission_requests_rejected", () ->
				jdbc.update("update admission_requests set status = 'Rejected', remarks = '   ' where id = ?", reqId));

		// Transition to Approved is valid with student_id and reviewed_by
		StudentFixture s = createStudent();
		jdbc.update("update admission_requests set status = 'Approved', student_id = ?, reviewed_by = ?, reviewed_at = now() where id = ?",
				s.studentId(), adminId, reqId);

		// Approved entry is immutable (trigger refuses further UPDATE or DELETE)
		assertThrows(DataAccessException.class, () ->
				jdbc.update("update admission_requests set remarks = 'Trying to change' where id = ?", reqId));
		assertThrows(DataAccessException.class, () ->
				jdbc.update("delete from admission_requests where id = ?", reqId));
	}
}
