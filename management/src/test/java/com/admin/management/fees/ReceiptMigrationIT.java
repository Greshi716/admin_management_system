package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.StudentTestData;
import com.admin.management.support.StudentTestData.YearSetup;

/**
 * V8 applies on a database that is already at V7 and holds students, a fee account and a brought-forward amount; their
 * data is not touched (constitution V). Applying it on an empty database is what every test class does.
 */
@Covers("FR-009")
class ReceiptMigrationIT extends IntegrationTest {

	@Autowired
	Flyway flyway;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void v8AppliesOnAVersion7DatabaseWithFeeData() {
		flyway.clean();
		Flyway.configure().configuration(flyway.getConfiguration()).target("7").load().migrate();
		assertThat(exists("receipts")).isFalse();
		long year = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values ('2026-27', date '2026-04-01', date '2027-03-31') returning id",
				Long.class);
		long standard = jdbc.queryForObject(
				"insert into standards (name, display_order) values ('Migration Std', 1) returning id", Long.class);
		long division = jdbc.queryForObject(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A') returning id", Long.class,
				year, standard);
		long category = jdbc.queryForObject("select min(id) from student_categories", Long.class);
		long student = StudentTestData.insertStudent(jdbc, StudentTestData.valid(1, category, standard));
		StudentTestData.insertEnrollment(jdbc, student,
				new YearSetup(year, LocalDate.of(2026, 4, 1), standard, division), category);
		long enrollment = jdbc.queryForObject("select id from student_enrollments where student_id = ?", Long.class,
				student);
		jdbc.update("insert into fee_accounts (enrollment_id, academic_year_id, amount, registration_fee, updated_at)"
				+ " values (?, ?, 28000.00, 500.00, now())", enrollment, year);
		jdbc.update("insert into fee_brought_forward (student_id, amount, updated_at) values (?, 2500.00, now())",
				student);

		flyway.migrate();

		for (String name : new String[] { "receipts", "receipt_marks", "v_valid_receipts", "v_fee_position",
				"v_earlier_pending" }) {
			assertThat(exists(name)).as(name).isTrue();
		}
		assertThat(jdbc.queryForObject("select count(*) from pg_proc where proname = 'void_receipt'", Integer.class))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from students", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from student_enrollments", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select amount from fee_accounts where enrollment_id = ?", BigDecimal.class,
				enrollment))
			.isEqualByComparingTo("28000.00");
		// the views see the old data: nothing paid yet
		assertThat(jdbc.queryForObject("select pending_year_fee from v_fee_position where enrollment_id = ?",
				BigDecimal.class, enrollment))
			.isEqualByComparingTo("28000.00");
		assertThat(jdbc.queryForObject("select pending from v_earlier_pending where student_id = ?", BigDecimal.class,
				student))
			.isEqualByComparingTo("2500.00");
		assertThat(jdbc.queryForObject("select max(version::int) from flyway_schema_history where version ~ '^[0-9]+$'",
				Integer.class))
			.isGreaterThanOrEqualTo(8);
	}

	private boolean exists(String name) {
		return jdbc.queryForObject("select to_regclass(?) is not null", Boolean.class, "public." + name);
	}

}
