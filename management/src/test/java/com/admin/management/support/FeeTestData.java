package com.admin.management.support;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Synthetic fee data for tests (constitution VI): a year with standards and divisions, fee rows, a year status and
 * class records in bulk. Writes with plain SQL so it works before the fee services exist and stays fast for the
 * 2,000 and 10,000 record tests.
 */
public final class FeeTestData {

	/** One year with its standards (numbered from 1, in display order), a division A of each, and the seeded categories. */
	public record Setup(long yearId, LocalDate yearStart, List<Long> standardIds, Map<Long, Long> divisionByStandard,
			List<Long> categoryIds) {

		public long standard(int index) {
			return standardIds.get(index);
		}

		public long category(int index) {
			return categoryIds.get(index);
		}

	}

	private FeeTestData() {
	}

	/**
	 * Creates an academic year from {@code start} for one year and {@code standards} standards named
	 * "Fee Std 1" and so on, with a division A for each. Names must not exist yet, so give each call its own
	 * {@code namePrefix}.
	 */
	public static Setup year(JdbcTemplate jdbc, String namePrefix, LocalDate start, int standards) {
		long yearId = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values (?, ?, ?) returning id", Long.class,
				namePrefix + " " + start.getYear() + "-" + (start.getYear() + 1), Date.valueOf(start),
				Date.valueOf(start.plusYears(1).minusDays(1)));
		List<Long> standardIds = new ArrayList<>();
		Map<Long, Long> divisions = new LinkedHashMap<>();
		for (int s = 1; s <= standards; s++) {
			long standardId = jdbc.queryForObject(
					"insert into standards (name, display_order) values (?, ?) returning id", Long.class,
					namePrefix + " Std " + s, s);
			standardIds.add(standardId);
			divisions.put(standardId, jdbc.queryForObject(
					"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A') returning id",
					Long.class, yearId, standardId));
		}
		List<Long> categories = jdbc.queryForList("select id from student_categories order by id", Long.class);
		return new Setup(yearId, start, standardIds, divisions, categories);
	}

	/** Adds a fee row. {@code categoryId}, {@code registrationFee} and {@code due} may be null. Returns the row id. */
	public static long addRow(JdbcTemplate jdbc, long yearId, long standardId, Long categoryId, String amount,
			String registrationFee, LocalDate due) {
		return jdbc.queryForObject("""
				insert into fee_rows (academic_year_id, standard_id, category_id, amount, registration_fee, due_date)
				values (?, ?, ?, ?, ?, ?) returning id
				""", Long.class, yearId, standardId, categoryId, new BigDecimal(amount),
				registrationFee == null ? null : new BigDecimal(registrationFee), due == null ? null : Date.valueOf(due));
	}

	/** Gives the year a status row: Draft, or Finalised with the time of the call. Does not create any account. */
	public static void setStatus(JdbcTemplate jdbc, long yearId, boolean finalised) {
		jdbc.update("delete from fee_year_status where academic_year_id = ?", yearId);
		jdbc.update("insert into fee_year_status (academic_year_id, status, finalised_at) values (?, ?, "
				+ (finalised ? "now()" : "null") + ")", yearId, finalised ? "Finalised" : "Draft");
	}

	/**
	 * Registers student number {@code n} (GR No. {@code GR-n}) in the year, in the standard and category with the
	 * given indexes of the setup. Returns the id of the class record.
	 */
	public static long student(JdbcTemplate jdbc, Setup setup, int n, int standardIndex, int categoryIndex) {
		long standardId = setup.standard(standardIndex);
		long categoryId = setup.category(categoryIndex);
		long studentId = StudentTestData.insertStudent(jdbc, StudentTestData.valid(n, categoryId, standardId));
		StudentTestData.insertEnrollment(jdbc, studentId, new StudentTestData.YearSetup(setup.yearId(),
				setup.yearStart(), standardId, setup.divisionByStandard().get(standardId)), categoryId);
		return jdbc.queryForObject("select id from student_enrollments where student_id = ? and academic_year_id = ?",
				Long.class, studentId, setup.yearId());
	}

	/**
	 * Inserts {@code count} students (GR No. {@code prefix-1} to {@code prefix-count}) with one class record each in
	 * the year, spread evenly over the standards and the categories of the setup. Uses set-based SQL, so 10,000
	 * records take a second or two. Returns the number of class records made.
	 */
	public static int seedClassRecords(JdbcTemplate jdbc, Setup setup, String prefix, int count) {
		String standardCase = caseOf("standard", setup.standardIds());
		String categoryCase = caseOf("category", setup.categoryIds());
		String sql = """
				with created as (
				    insert into students (gr_no, surname, first_name, gender, dob, mobile_1, address, category_id,
				        admission_date, admission_standard_id)
				    select ? || '-' || g.n, 'Seedsurname' || g.n, 'Seed', case when g.n %% 2 = 0 then 'Male' else 'Female' end,
				        date '2012-01-01', '9' || lpad(g.n::text, 9, '0'), 'Seed address ' || g.n || ', Test Town',
				        %s, date '2020-06-01', %s
				    from (select n, n %% %d as standard, n %% %d as category from generate_series(1, ?) as s(n)) g
				    returning id, gr_no, category_id, admission_standard_id
				), keys as (
				    insert into student_gr_numbers (gr_key, student_id, assigned_at)
				    select lower(gr_no), id, now() from created
				)
				insert into student_enrollments (student_id, academic_year_id, standard_id, division_id, category_id,
				    year_start_date)
				select id, ?, admission_standard_id,
				    (select d.id from divisions d where d.academic_year_id = ? and d.standard_id = created.admission_standard_id
				     and d.name = 'A'),
				    category_id, ?
				from created
				""".formatted(categoryCase, standardCase, setup.standardIds().size(), setup.categoryIds().size());
		return jdbc.update(sql, prefix, count, setup.yearId(), setup.yearId(), Date.valueOf(setup.yearStart()));
	}

	/** {@code case g.<column> when 0 then id0 when 1 then id1 ... end}: ids are our own numbers, never user input. */
	private static String caseOf(String column, List<Long> ids) {
		StringBuilder sql = new StringBuilder("case g.").append(column);
		for (int i = 0; i < ids.size(); i++) {
			sql.append(" when ").append(i).append(" then ").append(ids.get(i));
		}
		return sql.append(" end").toString();
	}

}
