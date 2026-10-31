package com.admin.management.students;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;

import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.shared.table.ColumnDef;
import com.admin.management.shared.table.ColumnDef.ExpressionFactory;
import com.admin.management.shared.table.TableDef;
import com.admin.management.shared.table.TableQueryService;
import com.admin.management.shared.web.ValidationException;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;

/**
 * The columns of the Students list (FR-008, FR-010), declared once for the page and the export.
 *
 * <p>
 * Std / Div and Category come from the class record of the current academic year, or from the student's most
 * recent record when there is none for the current year (research R5). The record is found with a correlated
 * subquery on {@code student_enrollments} (this module's own table). Names come from {@link MasterDataLookup}
 * maps, rebuilt for every request, and become a {@code CASE} over the few dozen ids; a renamed division shows at
 * once. The Std / Div column sorts by the standard's display order and then the division name.
 */
final class StudentTable {

	static final String STATUS_ACTIVE = "Active";

	static final String STATUS_LEFT = "Left";

	static final String STATUS_ALL = "All";

	private static final String SHOWN_RECORD = "(select e.%s from student_enrollments e where e.student_id = ? "
			+ "order by (e.academic_year_id = ?) desc, e.year_start_date desc limit 1)";

	private StudentTable() {
	}

	/** The list definition for one request. {@code currentYearId} is null when no academic year covers today. */
	static TableDef<Student> build(MasterDataLookup lookup) {
		Long currentYearId = lookup.currentYear().map(MasterDataLookup.YearRef::id).orElse(-1L);
		Long previousYearId = lookup.currentYear()
				.flatMap(cy -> lookup.earlierYears(cy.startDate()).stream().findFirst())
				.map(MasterDataLookup.YearDates::id)
				.orElse(-1L);
		Map<Long, String> standardNames = lookup.standardNames();
		Map<Long, Integer> standardOrders = lookup.standardDisplayOrders();
		Map<Long, String> divisionNames = lookup.divisionNames();
		Map<Long, String> categoryNames = lookup.categoryNames();
		Map<Long, String> yearNames = lookup.yearNames();
		Map<Long, MasterDataLookup.YearDates> yearDates = lookup.yearDatesByYear();

		ExpressionFactory<Student> standardId = record("standard_id", currentYearId);
		ExpressionFactory<Student> divisionId = record("division_id", currentYearId);
		ExpressionFactory<Student> categoryId = record("category_id", currentYearId);
		ExpressionFactory<Student> yearId = record("academic_year_id", currentYearId);

		StringFactory standardName = nameOf(standardId, standardNames);
		StringFactory divisionName = nameOf(divisionId, divisionNames);
		ExpressionFactory<Student> standardOrder = (root, cb) -> {
			if (standardOrders.isEmpty()) {
				return cb.coalesce(cb.nullLiteral(Integer.class), Integer.MAX_VALUE); // a CASE with no WHEN is not valid SQL, a bare constant in ORDER BY is a column position
			}
			var chosen = cb.<Long, Integer>selectCase(asLong(standardId.build(root, cb)));
			standardOrders.forEach((id, order) -> chosen.when(id, order));
			return chosen.otherwise(Integer.MAX_VALUE);
		};
		StringFactory stdDiv = (root, cb) -> cb.concat(cb.concat(standardName.build(root, cb), "-"),
				divisionName.build(root, cb));

		List<ColumnDef<Student>> columns = List.of(
				ColumnDef.<Student>number("id", "Id").asInternal(),
				ColumnDef.<Student>number("version", "Version").asInternal(),
				ColumnDef.<Student>number("photoVersion", "Photo").asInternal(),
				ColumnDef.<Student>text("grNo", "GR No."),
				ColumnDef.<Student>text("name", "Name").expression(StudentTable::fullName),
				ColumnDef.<Student>text("stdDiv", "Std / Div").expression(stdDiv::build).searchable(false)
					.sortedBy(standardOrder, divisionName::build),
				ColumnDef.<Student>text("category", "Category").expression(nameOf(categoryId, categoryNames)::build)
					.searchable(false),
				ColumnDef.<Student>text("status", "Status").searchable(false),
				ColumnDef.<Student>text("yearStatus", "Year status").expression(recordString("year_status", currentYearId))
					.searchable(false),
				ColumnDef.<Student>text("lastYearResult", "Last year result").expression(lastYearResult(previousYearId))
					.searchable(false),
				ColumnDef.<Student>bool("notPromotedYet", "Not promoted yet").expression(notPromotedYet(currentYearId))
					.searchable(false),
				ColumnDef.<Student>number("totalFee", "Total fee").expression(totalFee(currentYearId)),
				ColumnDef.<Student>text("feeState", "Fee state").expression(feeState(currentYearId))
					.searchable(false).asHidden(),
				ColumnDef.<Student>number("paid", "Paid").expression(position(currentYearId, "case when p.year_fee is null then null else p.paid_year_fee + p.paid_registration end"))
					.searchable(false),
				ColumnDef.<Student>number("pending", "Pending")
					.expression(position(currentYearId, "p.pending_year_fee + p.pending_registration")).searchable(false),
				ColumnDef.<Student>number("earlierPending", "Earlier years pending").expression(earlierPending())
					.searchable(false),
				ColumnDef.<Student>date("paidUpTo", "Paid up to").expression(paidUpTo(currentYearId, yearDates))
					.searchable(false),
				ColumnDef.<Student>text("paidUpToState", "Paid up to state")
					.expression(paidUpToState(currentYearId)).searchable(false).asHidden(),
				ColumnDef.<Student>text("feeStatus", "Fee status").expression(feeStatus(currentYearId)).searchable(false),
				ColumnDef.<Student>number("standardId", "Standard id").expression(standardId).asInternal(),
				ColumnDef.<Student>number("divisionId", "Division id").expression(divisionId).asInternal(),
				ColumnDef.<Student>number("categoryId", "Category id").expression(categoryId).asInternal(),
				ColumnDef.<Student>number("recordYearId", "Record year").expression(yearId).asInternal(),
				ColumnDef.<Student>text("recordYear", "Record year name").expression(nameOf(yearId, yearNames)::build)
					.searchable(false).asHidden(),
				ColumnDef.<Student>text("surname", "Surname").searchable(false).asHidden(),
				ColumnDef.<Student>text("firstName", "First name").searchable(false).asHidden(),
				ColumnDef.<Student>text("middleName", "Middle name").searchable(false).asHidden(),
				ColumnDef.<Student>text("gender", "Gender").searchable(false).asHidden(),
				ColumnDef.<Student>date("dob", "Date of birth").asHidden(),
				ColumnDef.<Student>text("birthPlace", "Place of birth").searchable(false).asHidden(),
				ColumnDef.<Student>text("fatherName", "Father").searchable(false).asHidden(),
				ColumnDef.<Student>text("motherName", "Mother").searchable(false).asHidden(),
				ColumnDef.<Student>text("mobile1", "Mobile 1").asHidden(),
				ColumnDef.<Student>text("mobile2", "Mobile 2").asHidden(),
				ColumnDef.<Student>text("address", "Address").searchable(false).asHidden(),
				ColumnDef.<Student>date("admissionDate", "Admission date").asHidden(),
				ColumnDef.<Student>text("admissionStandard", "Admission standard")
					.expression(nameOf((root, cb) -> root.get("admissionStandardId"), standardNames)::build)
					.searchable(false).asHidden(),
				ColumnDef.<Student>text("previousSchool", "Previous school").searchable(false).asHidden(),
				ColumnDef.<Student>text("source", "Entry source").searchable(false).asHidden(),
				ColumnDef.<Student>number("srNo", "Serial no.").asHidden(),
				ColumnDef.<Student>text("aadhaar", "Aadhaar").asSensitive().asHidden(),
				ColumnDef.<Student>text("religionCaste", "Religion / caste").asSensitive().asHidden());
		return TableDef.of(Student.class, columns).sortedBy("name", 1);
	}

	/** Active (the default), Left, or All. */
	static TableQueryService.Scope<Student> statusScope(String status) {
		String chosen = status == null || status.isBlank() ? STATUS_ACTIVE : status;
		return switch (chosen) {
			case STATUS_ACTIVE, STATUS_LEFT -> (root, cb) -> cb.equal(root.get("status"), chosen);
			case STATUS_ALL -> null;
			default -> throw new ValidationException("status", "The status must be Active, Left or All.");
		};
	}

	/** A column of the record shown for the student: the current year's, else the most recent one. */
	private static ExpressionFactory<Student> record(String column, Long currentYearId) {
		return (root, cb) -> ((HibernateCriteriaBuilder) cb).sql(SHOWN_RECORD.formatted(column), Long.class,
				root.get("id"), cb.literal(currentYearId));
	}

	private static ExpressionFactory<Student> recordString(String column, Long currentYearId) {
		return (root, cb) -> ((HibernateCriteriaBuilder) cb).sql(SHOWN_RECORD.formatted(column), String.class,
				root.get("id"), cb.literal(currentYearId));
	}

	private static ExpressionFactory<Student> lastYearResult(Long previousYearId) {
		long prevId = previousYearId != null ? previousYearId : -1L;
		return (root, cb) -> ((HibernateCriteriaBuilder) cb).sql(
				"coalesce((select e.year_status from student_enrollments e where e.student_id = ? and e.academic_year_id = ?), '')",
				String.class, root.get("id"), cb.literal(prevId));
	}

	private static ExpressionFactory<Student> notPromotedYet(Long currentYearId) {
		if (currentYearId == null || currentYearId <= 0) {
			return (root, cb) -> cb.literal(false);
		}
		return (root, cb) -> ((HibernateCriteriaBuilder) cb).sql(
				"coalesce((select case when e.academic_year_id != ? and e.year_status = 'Studying' then true else false end "
						+ "from student_enrollments e where e.student_id = ? "
						+ "order by (e.academic_year_id = ?) desc, e.year_start_date desc limit 1), false)",
				Boolean.class, cb.literal(currentYearId), root.get("id"), cb.literal(currentYearId));
	}

	/**
	 * Yearly fee plus registration fee of the shown record's fee account; null when it has no account or no amount
	 * (F2 FR-010). This is the one place the students module reads the fees module's table (constitution VII 1.1.0,
	 * plan Complexity Tracking); a summary column was not needed (research R9).
	 */
	private static ExpressionFactory<Student> totalFee(Long currentYearId) {
		String sql = "(select cast(a.amount + coalesce(a.registration_fee, 0) as numeric(12,2)) from fee_accounts a where a.enrollment_id = "
				+ SHOWN_RECORD.formatted("id") + ")";
		return (root, cb) -> ((HibernateCriteriaBuilder) cb).sql(sql, BigDecimal.class, root.get("id"),
				cb.literal(currentYearId));
	}

	/**
	 * Text for the student's fee when there is no amount to show: "Not finalised" when the shown record has no fee
	 * account yet, "Fee not set" when it has an account without an amount; null when the fee is set (owner decision
	 * 2026-10-28, FR-010).
	 */
	private static ExpressionFactory<Student> feeState(Long currentYearId) {
		String sql = "(select case when a.enrollment_id is null then 'Not finalised' when a.amount is null then "
				+ "'Fee not set' end from (select " + SHOWN_RECORD.formatted("id")
				+ " as id) s left join fee_accounts a on a.enrollment_id = s.id)";
		return (root, cb) -> ((HibernateCriteriaBuilder) cb).sql(sql, String.class, root.get("id"),
				cb.literal(currentYearId));
	}

	/**
	 * One figure of the shown record's row in {@code v_fee_position} (alias {@code p}); null when the record has no fee
	 * account or no fee amount (FR-015). Reads the view, never the receipts (constitution VII 1.1.0, research R11).
	 */
	private static ExpressionFactory<Student> position(Long currentYearId, String figure) {
		String sql = "(select cast(" + figure + " as numeric(12,2)) from v_fee_position p where p.enrollment_id = " + SHOWN_RECORD.formatted("id")
				+ ")";
		return (root, cb) -> ((HibernateCriteriaBuilder) cb).sql(sql, BigDecimal.class, root.get("id"),
				cb.literal(currentYearId));
	}

	/** Brought-forward amount minus the valid previous-years receipts, 0.00 when the student has none (FR-015). */
	private static ExpressionFactory<Student> earlierPending() {
		String sql = "cast(coalesce((select greatest(v.pending, 0) from v_earlier_pending v where v.student_id = ?), 0) as numeric(12,2))";
		return (root, cb) -> ((HibernateCriteriaBuilder) cb).sql(sql, BigDecimal.class, root.get("id"));
	}

	/** Year fully paid (or no year fee): the whole year is paid, so there is no date to count. */
	private static final String YEAR_PAID = "p.year_fee = 0 or p.paid_year_fee >= p.year_fee";

	/**
	 * "Paid up to", the last paid day (FR-012): the year's start plus the days paid minus one, the days paid being
	 * {@code paid * days / fee} rounded half up and at least one; the year's last day when the year fee is fully paid;
	 * null when nothing is paid or there is no fee. The same rule as PaidUpTo in the fees module,
	 * with the year dates turned into a {@code CASE} over the few academic years (no table is read).
	 */
	private static ExpressionFactory<Student> paidUpTo(Long currentYearId,
			Map<Long, MasterDataLookup.YearDates> yearDates) {
		String start = dateCase(yearDates, true);
		String end = dateCase(yearDates, false);
		String sql = "(select case when p.year_fee is null then null when " + YEAR_PAID + " then " + end
				+ " when p.paid_year_fee = 0 then null else " + start
				+ " + cast(greatest(round(p.paid_year_fee * (" + end + " - " + start
				+ " + 1) / p.year_fee), 1) - 1 as integer) end from v_fee_position p where p.enrollment_id = "
				+ SHOWN_RECORD.formatted("id") + ")";
		return (root, cb) -> ((HibernateCriteriaBuilder) cb).sql(sql, LocalDate.class, root.get("id"),
				cb.literal(currentYearId));
	}

	/** NOTHING, DATE or FULL for the "Paid up to" column; null when there is no fee amount. */
	private static ExpressionFactory<Student> paidUpToState(Long currentYearId) {
		String sql = "(select case when p.year_fee is null then null when " + YEAR_PAID
				+ " then 'FULL' when p.paid_year_fee = 0 then 'NOTHING' else 'DATE' end from v_fee_position p "
				+ "where p.enrollment_id = " + SHOWN_RECORD.formatted("id") + ")";
		return (root, cb) -> ((HibernateCriteriaBuilder) cb).sql(sql, String.class, root.get("id"),
				cb.literal(currentYearId));
	}

	/**
	 * Paid when neither the record's pending nor the earlier years' pending is above zero, Partly paid when something is
	 * paid and something is pending, Unpaid when nothing is paid and something is pending; null when there is no fee
	 * amount (the Fee state column says why). Previous-years payments count as paid (default until the owner answers).
	 */
	private static ExpressionFactory<Student> feeStatus(Long currentYearId) {
		String earlier = "coalesce(greatest(e.pending, 0), 0)";
		String sql = "(select case when p.year_fee is null then null when p.pending_year_fee + p.pending_registration + "
				+ earlier + " <= 0 then 'Paid' when p.paid_year_fee + p.paid_registration + coalesce(e.paid, 0) > 0 "
				+ "then 'Partly paid' else 'Unpaid' end from v_fee_position p left join v_earlier_pending e on "
				+ "e.student_id = ? where p.enrollment_id = " + SHOWN_RECORD.formatted("id") + ")";
		return (root, cb) -> ((HibernateCriteriaBuilder) cb).sql(sql, String.class, root.get("id"), root.get("id"),
				cb.literal(currentYearId));
	}

	/** {@code case p.academic_year_id when 7 then date '2026-04-01' ... end}; ids and ISO dates only, no user input. */
	private static String dateCase(Map<Long, MasterDataLookup.YearDates> yearDates, boolean start) {
		if (yearDates.isEmpty()) {
			return "cast(null as date)";
		}
		StringBuilder text = new StringBuilder("(case p.academic_year_id");
		yearDates.forEach((id, dates) -> text.append(" when ")
			.append(id.longValue())
			.append(" then date '")
			.append(start ? dates.startDate() : dates.endDate())
			.append("'"));
		return text.append(" end)").toString();
	}

	@FunctionalInterface
	private interface StringFactory {

		Expression<String> build(Root<Student> root, CriteriaBuilder cb);

	}

	private static Expression<String> fullName(Root<Student> root, CriteriaBuilder cb) {
		Expression<String> withFirst = cb.concat(cb.concat(root.<String>get("surname"), " "),
				root.<String>get("firstName"));
		return cb.concat(withFirst, cb.coalesce(cb.concat(" ", root.<String>get("middleName")), ""));
	}

	private static StringFactory nameOf(ExpressionFactory<Student> id, Map<Long, String> names) {
		return (root, cb) -> {
			if (names.isEmpty()) {
				return cb.coalesce(cb.nullLiteral(String.class), ""); // a CASE with no WHEN is not valid SQL
			}
			var chosen = cb.<Long, String>selectCase(asLong(id.build(root, cb)));
			names.forEach((key, name) -> chosen.when(key, name));
			return chosen.otherwise("");
		};
	}


	@SuppressWarnings("unchecked")
	private static Expression<Long> asLong(Expression<?> expression) {
		return (Expression<Long>) expression;
	}

}
