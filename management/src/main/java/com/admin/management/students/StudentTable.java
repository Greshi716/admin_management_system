package com.admin.management.students;

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
		Map<Long, String> standardNames = lookup.standardNames();
		Map<Long, Integer> standardOrders = lookup.standardDisplayOrders();
		Map<Long, String> divisionNames = lookup.divisionNames();
		Map<Long, String> categoryNames = lookup.categoryNames();
		Map<Long, String> yearNames = lookup.yearNames();

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
				ColumnDef.<Student>number("totalFee", "Total fee").expression(totalFee(currentYearId)),
				ColumnDef.<Student>text("feeState", "Fee state").expression(feeState(currentYearId))
					.searchable(false).asHidden(),
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

	/**
	 * Yearly fee plus registration fee of the shown record's fee account; null when it has no account or no amount
	 * (F2 FR-010). This is the one place the students module reads the fees module's table (constitution VII 1.1.0,
	 * plan Complexity Tracking); a summary column was not needed (research R9).
	 */
	private static ExpressionFactory<Student> totalFee(Long currentYearId) {
		String sql = "(select a.amount + coalesce(a.registration_fee, 0) from fee_accounts a where a.enrollment_id = "
				+ SHOWN_RECORD.formatted("id") + ")";
		return (root, cb) -> ((HibernateCriteriaBuilder) cb).sql(sql, java.math.BigDecimal.class, root.get("id"),
				cb.literal(currentYearId));
	}

	/** NOT_SET when the shown record has an account without an amount; null otherwise (no account means not finalised). */
	private static ExpressionFactory<Student> feeState(Long currentYearId) {
		String sql = "(select case when a.amount is null then 'NOT_SET' end from fee_accounts a where a.enrollment_id = "
				+ SHOWN_RECORD.formatted("id") + ")";
		return (root, cb) -> ((HibernateCriteriaBuilder) cb).sql(sql, String.class, root.get("id"),
				cb.literal(currentYearId));
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
