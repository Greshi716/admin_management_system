package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.StudentTestData;
import com.admin.management.support.StudentTestData.YearSetup;

/**
 * The constraints of V7__fees.sql, each checked with a rejected insert (constitution V). A value with three decimals
 * is not tested here: PostgreSQL rounds it to fit numeric(12,2) instead of refusing it, so that refusal belongs to
 * the service (T012). Every test that stores fee rows uses a year of its own, because the tests share one database.
 */
@Covers("FR-001")
@Covers("FR-006")
@Covers("FR-007")
@Covers("FR-011")
class FeeSchemaIT extends IntegrationTest {

	private static final String CHECK_VIOLATION = "23514";

	private static final String FOREIGN_KEY_VIOLATION = "23503";

	private static final String NUMERIC_OVERFLOW = "22003";

	private static final String UNIQUE_VIOLATION = "23505";

	@Autowired
	JdbcTemplate jdbc;

	int years;

	long yearId;

	long standardId;

	long otherStandardId;

	long categoryId;

	long otherCategoryId;

	long studentId;

	long enrollmentId;

	@BeforeAll
	void seed() {
		yearId = insertYear("2026-27", "2026-04-01", "2027-03-31");
		standardId = insertStandard("Schema Std 10", 10);
		otherStandardId = insertStandard("Schema Std 9", 9);
		categoryId = jdbc.queryForObject("select min(id) from student_categories", Long.class);
		otherCategoryId = jdbc.queryForObject("select max(id) from student_categories", Long.class);
		long divisionId = jdbc.queryForObject(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A') returning id", Long.class,
				yearId, standardId);
		studentId = StudentTestData.insertStudent(jdbc, StudentTestData.valid(1, categoryId, standardId));
		StudentTestData.insertEnrollment(jdbc, studentId,
				new YearSetup(yearId, LocalDate.of(2026, 4, 1), standardId, divisionId), categoryId);
		enrollmentId = jdbc.queryForObject("select id from student_enrollments where student_id = ?", Long.class,
				studentId);
	}

	@Test
	void amountsAreKeptWithTwoDecimals() {
		long year = freshYear();
		jdbc.update("insert into fee_rows (academic_year_id, standard_id, category_id, amount, registration_fee) "
				+ "values (?, ?, ?, 28000, 500.5)", year, standardId, categoryId);

		BigDecimal amount = jdbc.queryForObject("select amount from fee_rows where academic_year_id = ?",
				BigDecimal.class, year);
		BigDecimal registration = jdbc.queryForObject(
				"select registration_fee from fee_rows where academic_year_id = ?", BigDecimal.class, year);

		assertThat(amount).isEqualTo(new BigDecimal("28000.00"));
		assertThat(registration).isEqualTo(new BigDecimal("500.50"));
	}

	@Test
	void aNegativeAmountIsRefusedInEveryTable() {
		assertRefused(CHECK_VIOLATION, "ck_fee_rows_amount",
				"insert into fee_rows (academic_year_id, standard_id, amount) values (?, ?, -1)", yearId, otherStandardId);
		assertRefused(CHECK_VIOLATION, "ck_fee_rows_registration_fee",
				"insert into fee_rows (academic_year_id, standard_id, amount, registration_fee) values (?, ?, 1, -0.01)",
				yearId, otherStandardId);
		assertRefused(CHECK_VIOLATION, "ck_fee_accounts_amount",
				"insert into fee_accounts (enrollment_id, academic_year_id, amount, updated_at) values (?, ?, -1, now())",
				enrollmentId, yearId);
		assertRefused(CHECK_VIOLATION, "ck_fee_accounts_registration_fee",
				"insert into fee_accounts (enrollment_id, academic_year_id, amount, registration_fee, updated_at) "
						+ "values (?, ?, 1, -1, now())",
				enrollmentId, yearId);
		assertRefused(CHECK_VIOLATION, "ck_fee_brought_forward_amount",
				"insert into fee_brought_forward (student_id, amount, updated_at) values (?, -1, now())", studentId);
	}

	@Test
	void aValueTooBigForTwelveDigitsIsRefused() {
		assertRefused(NUMERIC_OVERFLOW, "numeric field overflow",
				"insert into fee_rows (academic_year_id, standard_id, amount) values (?, ?, 10000000000.00)", yearId,
				otherStandardId);
		jdbc.update("insert into fee_rows (academic_year_id, standard_id, amount) values (?, ?, 9999999999.99)",
				freshYear(), otherStandardId);
	}

	@Test
	void anAmountIsRequiredOnARow() {
		assertRefused("23502", "amount",
				"insert into fee_rows (academic_year_id, standard_id, amount) values (?, ?, null)", yearId,
				otherStandardId);
	}

	@Test
	void aNoteOfMoreThan200CharactersIsRefused() {
		assertRefused(CHECK_VIOLATION, "ck_fee_brought_forward_note",
				"insert into fee_brought_forward (student_id, amount, note, updated_at) values (?, 1, ?, now())",
				studentId, "x".repeat(201));
		jdbc.update("insert into fee_brought_forward (student_id, amount, note, updated_at) values (?, 1, ?, now())",
				studentId, "x".repeat(200));
		assertThat(jdbc.queryForObject("select char_length(note) from fee_brought_forward where student_id = ?",
				Integer.class, studentId)).isEqualTo(200);
	}

	@Test
	void aSecondRowForTheSameStandardAndCategoryIsRefused() {
		long year = freshYear();
		String withCategory = "insert into fee_rows (academic_year_id, standard_id, category_id, amount) "
				+ "values (?, ?, ?, 100)";
		jdbc.update(withCategory, year, standardId, categoryId);
		assertRefused(UNIQUE_VIOLATION, "uq_fee_rows_category", withCategory, year, standardId, categoryId);
		// another category, another standard and another year are different rows
		jdbc.update(withCategory, year, standardId, otherCategoryId);
		jdbc.update(withCategory, year, otherStandardId, categoryId);
		jdbc.update(withCategory, freshYear(), standardId, categoryId);
	}

	@Test
	void aSecondRowWithNoCategoryForTheSameStandardIsRefused() {
		long year = freshYear();
		String withoutCategory = "insert into fee_rows (academic_year_id, standard_id, amount) values (?, ?, 100)";
		jdbc.update(withoutCategory, year, otherStandardId);
		assertRefused(UNIQUE_VIOLATION, "uq_fee_rows_no_category", withoutCategory, year, otherStandardId);
		// a row with a category beside it is fine
		jdbc.update("insert into fee_rows (academic_year_id, standard_id, category_id, amount) values (?, ?, ?, 100)",
				year, otherStandardId, categoryId);
	}

	@Test
	void theStatusIsDraftOrFinalisedAndFinalisedNeedsItsTime() {
		long year = freshYear();
		assertRefused(CHECK_VIOLATION, "ck_fee_year_status_status",
				"insert into fee_year_status (academic_year_id, status) values (?, 'Bogus')", year);
		assertRefused(CHECK_VIOLATION, "ck_fee_year_status_finalised_at",
				"insert into fee_year_status (academic_year_id, status) values (?, 'Finalised')", year);
		assertRefused(CHECK_VIOLATION, "ck_fee_year_status_finalised_at",
				"insert into fee_year_status (academic_year_id, status, finalised_at) values (?, 'Draft', now())", year);

		jdbc.update("insert into fee_year_status (academic_year_id) values (?)", year);
		assertThat(jdbc.queryForObject("select status from fee_year_status where academic_year_id = ?", String.class,
				year)).isEqualTo("Draft");
		assertThat(jdbc.queryForObject("select version from fee_year_status where academic_year_id = ?", Long.class,
				year)).isZero();
		jdbc.update("update fee_year_status set status = 'Finalised', finalised_at = now() where academic_year_id = ?",
				year);
	}

	@Test
	void everyReferenceMustPointToAnExistingRecord() {
		assertRefused(FOREIGN_KEY_VIOLATION, "fk_fee_year_status_year",
				"insert into fee_year_status (academic_year_id) values (?)", -1L);
		assertRefused(FOREIGN_KEY_VIOLATION, "fk_fee_rows_year",
				"insert into fee_rows (academic_year_id, standard_id, amount) values (?, ?, 1)", -1L, standardId);
		assertRefused(FOREIGN_KEY_VIOLATION, "fk_fee_rows_standard",
				"insert into fee_rows (academic_year_id, standard_id, amount) values (?, ?, 1)", yearId, -1L);
		assertRefused(FOREIGN_KEY_VIOLATION, "fk_fee_rows_category",
				"insert into fee_rows (academic_year_id, standard_id, category_id, amount) values (?, ?, ?, 1)", yearId,
				otherStandardId, -1L);
		assertRefused(FOREIGN_KEY_VIOLATION, "fk_fee_accounts_enrollment",
				"insert into fee_accounts (enrollment_id, academic_year_id, updated_at) values (?, ?, now())", -1L, yearId);
		assertRefused(FOREIGN_KEY_VIOLATION, "fk_fee_accounts_year",
				"insert into fee_accounts (enrollment_id, academic_year_id, updated_at) values (?, ?, now())",
				enrollmentId, -1L);
		assertRefused(FOREIGN_KEY_VIOLATION, "fk_fee_brought_forward_student",
				"insert into fee_brought_forward (student_id, amount, updated_at) values (?, 1, now())", -1L);
	}

	@Test
	void aRecordUsedByAFeeTableCannotBeDeleted() {
		long year = freshYear();
		long standard = insertStandard("Schema Std 12", 12);
		long category = jdbc.queryForObject(
				"insert into student_categories (code, name, affects_fees) values ('SCH', 'Schema category', true) returning id",
				Long.class);
		jdbc.update("insert into fee_rows (academic_year_id, standard_id, category_id, amount) values (?, ?, ?, 1)", year,
				standard, category);

		String inFeeRows = "is still referenced from table \"fee_rows\"";
		assertRefused(FOREIGN_KEY_VIOLATION, inFeeRows, "delete from academic_years where id = ?", year);
		assertRefused(FOREIGN_KEY_VIOLATION, inFeeRows, "delete from standards where id = ?", standard);
		assertRefused(FOREIGN_KEY_VIOLATION, inFeeRows, "delete from student_categories where id = ?", category);

		jdbc.update("insert into fee_accounts (enrollment_id, academic_year_id, amount, updated_at) "
				+ "values (?, ?, 1, now())", enrollmentId, yearId);
		assertRefused(FOREIGN_KEY_VIOLATION, "is still referenced from table \"fee_accounts\"",
				"delete from student_enrollments where id = ?", enrollmentId);
	}

	/** A student has older references (GR numbers) that stop a delete first, so the rule is read from the catalog. */
	@Test
	void everyForeignKeyOfTheFeeTablesRestrictsDeletes() {
		var rules = jdbc.queryForList("""
				select conname, confdeltype::text as rule from pg_constraint
				where contype = 'f' and conrelid::regclass::text like 'fee\\_%'
				""");
		assertThat(rules).hasSize(7); // status 1, rows 3, accounts 2, brought forward 1
		assertThat(rules).allSatisfy(row -> assertThat(row.get("rule")).as(String.valueOf(row.get("conname")))
			.isEqualTo("r"));
	}

	private void assertRefused(String sqlState, String expected, String sql, Object... args) {
		Throwable thrown = null;
		try {
			jdbc.update(sql, args);
		}
		catch (RuntimeException ex) {
			thrown = ex;
		}
		assertThat(thrown).as("the statement must be refused: " + sql).isNotNull();
		Throwable root = thrown;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		assertThat(root).isInstanceOf(SQLException.class);
		assertThat(((SQLException) root).getSQLState()).isEqualTo(sqlState);
		assertThat(root.getMessage()).contains(expected);
	}

	/** A year no other test has put fee rows in; the years are 3000-01, 3001-02 and so on, which never overlap. */
	private long freshYear() {
		int from = 3000 + years++;
		return insertYear(from + "-" + (from + 1), from + "-04-01", (from + 1) + "-03-31");
	}

	private long insertYear(String name, String from, String to) {
		return jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values (?, ?::date, ?::date) returning id",
				Long.class, name, from, to);
	}

	private long insertStandard(String name, int order) {
		return jdbc.queryForObject("insert into standards (name, display_order) values (?, ?) returning id", Long.class,
				name, order);
	}

}
