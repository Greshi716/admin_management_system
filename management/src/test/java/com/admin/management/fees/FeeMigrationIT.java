package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.StudentTestData;
import com.admin.management.support.StudentTestData.YearSetup;

/** V7 applies on a database that is already at V6 and holds students; their data is not touched (constitution V). */
@Covers("FR-006")
class FeeMigrationIT extends IntegrationTest {

	@Autowired
	Flyway flyway;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void v7AppliesOnAVersion6DatabaseWithStudents() {
		flyway.clean();
		Flyway.configure().configuration(flyway.getConfiguration()).target("6").load().migrate();
		assertThat(tableExists("fee_rows")).isFalse();
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

		flyway.migrate();

		for (String table : new String[] { "fee_year_status", "fee_rows", "fee_accounts", "fee_brought_forward" }) {
			assertThat(tableExists(table)).as(table).isTrue();
		}
		assertThat(jdbc.queryForObject("select count(*) from students", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from student_enrollments", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select max(version::int) from flyway_schema_history where version ~ '^[0-9]+$'",
				Integer.class)).isGreaterThanOrEqualTo(7);
	}

	private boolean tableExists(String name) {
		return jdbc.queryForObject("select to_regclass(?) is not null", Boolean.class, "public." + name);
	}

}
