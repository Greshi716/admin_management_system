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

@Covers("FR-011")
@Covers("FR-017")
@Covers("FR-018")
@Covers("FR-024")
class SchemaIT extends IntegrationTest {

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void clearMasterData() {
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
