package com.admin.management.support;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.FeeTestData.Setup;

/**
 * Synthetic test data for Year Change and Admissions tests (constitution VI, FR-040):
 * consecutive academic years, finalised fee structures, standards with stream flags,
 * students with part-paid and unpaid fees, and clock helpers.
 */
@Covers("FR-040")
public final class YearChangeTestData {

	private YearChangeTestData() {
	}

	public record TwoYears(Setup oldYear, Setup newYear) {
	}

	/**
	 * Creates two consecutive academic years sharing standards, with divisions for each year.
	 * Standards with display order >= 11 have {@code uses_stream} set to true.
	 * The old year has fee rows and is marked Finalised.
	 */
	public static TwoYears twoConsecutiveYears(JdbcTemplate jdbc, String prefix, LocalDate oldStart, int standardsCount) {
		jdbc.update("update standards set active = false");
		LocalDate oldEnd = oldStart.plusYears(1).minusDays(1);
		LocalDate newStart = oldStart.plusYears(1);
		LocalDate newEnd = newStart.plusYears(1).minusDays(1);

		long oldYearId = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values (?, ?, ?) returning id",
				Long.class, prefix + " " + oldStart.getYear() + "-" + (oldStart.getYear() + 1),
				Date.valueOf(oldStart), Date.valueOf(oldEnd));

		long newYearId = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values (?, ?, ?) returning id",
				Long.class, prefix + " " + newStart.getYear() + "-" + (newStart.getYear() + 1),
				Date.valueOf(newStart), Date.valueOf(newEnd));

		List<Long> standardIds = new ArrayList<>();
		Map<Long, Long> oldDivisions = new LinkedHashMap<>();
		Map<Long, Long> newDivisions = new LinkedHashMap<>();

		for (int s = 1; s <= standardsCount; s++) {
			boolean usesStream = s >= 11;
			long standardId = jdbc.queryForObject(
					"insert into standards (name, display_order, uses_stream) values (?, ?, ?) returning id",
					Long.class, prefix + " Std " + s, s, usesStream);
			standardIds.add(standardId);

			oldDivisions.put(standardId, jdbc.queryForObject(
					"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A') returning id",
					Long.class, oldYearId, standardId));
			newDivisions.put(standardId, jdbc.queryForObject(
					"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A') returning id",
					Long.class, newYearId, standardId));
		}

		List<Long> categories = jdbc.queryForList("select id from student_categories order by id", Long.class);

		Setup oldYear = new Setup(oldYearId, oldStart, standardIds, oldDivisions, categories);
		Setup newYear = new Setup(newYearId, newStart, standardIds, newDivisions, categories);

		// Seed fee rows for old year and finalise it
		for (Long standardId : standardIds) {
			FeeTestData.addRow(jdbc, oldYearId, standardId, null, "12000.00", "500.00", oldStart.plusMonths(2));
		}
		FeeTestData.setStatus(jdbc, oldYearId, true);

		return new TwoYears(oldYear, newYear);
	}

	/** Finalises the fee structure for the new year with a default fee row per standard. */
	public static void finaliseNewYear(JdbcTemplate jdbc, Setup newYear, String feeAmount) {
		for (Long standardId : newYear.standardIds()) {
			FeeTestData.addRow(jdbc, newYear.yearId(), standardId, null, feeAmount, "500.00", newYear.yearStart().plusMonths(2));
		}
		FeeTestData.setStatus(jdbc, newYear.yearId(), true);
	}

	/**
	 * Creates a student with a class record in {@code setup}, an account of {@code yearFee},
	 * and if {@code paidFee} > 0, an initial cash receipt for {@code paidFee}.
	 */
	public static Fx studentWithFeeStatus(JdbcTemplate jdbc, Setup setup, int n, int standardIndex, int categoryIndex,
			String yearFee, String paidFee) {
		long standardId = setup.standard(standardIndex);
		long categoryId = setup.category(categoryIndex);
		long enrollmentId = FeeTestData.student(jdbc, setup, n, standardIndex, categoryIndex);
		long studentId = jdbc.queryForObject(
				"select student_id from student_enrollments where id = ?", Long.class, enrollmentId);

		jdbc.update("insert into fee_accounts (enrollment_id, academic_year_id, amount, registration_fee, updated_at) " +
				"values (?, ?, ?, 500.00, now())", enrollmentId, setup.yearId(), new BigDecimal(yearFee));

		Fx fx = new Fx(studentId, enrollmentId, "GR-" + n);
		if (paidFee != null && new BigDecimal(paidFee).compareTo(BigDecimal.ZERO) > 0) {
			FeeTestData.cashReceipt(jdbc, setup, fx, "R-" + n, setup.yearStart().plusDays(10), "0.00", paidFee);
		}
		return fx;
	}

	/** Sets the mutable test clock to the start date of the new academic year. */
	public static void startNewYear(MutableClock clock, Setup newYear) {
		clock.setDate(newYear.yearStart());
	}

}
