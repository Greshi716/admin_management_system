package com.admin.management.audit;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.admin.management.shared.table.TableQueryService;
import com.admin.management.shared.table.TableRequest;
import com.admin.management.shared.time.ClockConfig;
import com.admin.management.shared.web.ApiProblem;
import com.admin.management.shared.web.ValidationException;

import jakarta.persistence.criteria.Predicate;

/**
 * A query on the audit log: the standard table request plus the date range, month, year, area and action filters
 * (FR-026, contracts/openapi.yaml {@code /audit-log/search}). The filters combine with AND, and with the search and
 * column filters of the table.
 *
 * @param from first day, inclusive
 * @param to last day, inclusive
 * @param month a month as {@code yyyy-MM}
 * @param year a calendar year
 */
record AuditLogRequest(Integer first, Integer rows, String sortField, Integer sortOrder, String globalFilter,
		Map<String, TableRequest.Filter> filters, LocalDate from, LocalDate to, String month, Integer year,
		String area, String action) {

	TableRequest tableRequest() {
		return new TableRequest(first, rows, sortField, sortOrder, globalFilter, filters);
	}

	/** The extra conditions; refuses a reversed range or a month that is not {@code yyyy-MM} with HTTP 422. */
	TableQueryService.Scope<AuditEntry> scope() {
		List<ApiProblem.FieldError> errors = new ArrayList<>();
		if (from != null && to != null && from.isAfter(to)) {
			errors.add(new ApiProblem.FieldError("from", "The start date is after the end date"));
		}
		YearMonth wholeMonth = null;
		if (month != null && !month.isBlank()) {
			try {
				wholeMonth = YearMonth.parse(month.trim());
			}
			catch (DateTimeParseException ex) {
				errors.add(new ApiProblem.FieldError("month", "Use the form yyyy-MM, for example 2026-10"));
			}
		}
		if (year != null && (year < 1900 || year > 9999)) {
			errors.add(new ApiProblem.FieldError("year", "Enter a four-digit year"));
		}
		if (!errors.isEmpty()) {
			throw new ValidationException(errors);
		}
		YearMonth monthOnly = wholeMonth;
		return (root, cb) -> {
			var at = root.<java.time.Instant>get("at");
			List<Predicate> all = new ArrayList<>();
			if (from != null) {
				all.add(cb.greaterThanOrEqualTo(at, startOf(from)));
			}
			if (to != null) {
				all.add(cb.lessThan(at, startOf(to.plusDays(1))));
			}
			if (monthOnly != null) {
				all.add(cb.greaterThanOrEqualTo(at, startOf(monthOnly.atDay(1))));
				all.add(cb.lessThan(at, startOf(monthOnly.plusMonths(1).atDay(1))));
			}
			if (year != null) {
				all.add(cb.greaterThanOrEqualTo(at, startOf(LocalDate.of(year, 1, 1))));
				all.add(cb.lessThan(at, startOf(LocalDate.of(year + 1, 1, 1))));
			}
			if (area != null && !area.isBlank()) {
				all.add(cb.equal(root.get("area"), area.trim()));
			}
			if (action != null && !action.isBlank()) {
				all.add(cb.equal(root.get("action"), action.trim()));
			}
			return cb.and(all.toArray(Predicate[]::new));
		};
	}

	/** Days are the school's local days, as everywhere else. */
	private static java.time.Instant startOf(LocalDate day) {
		return day.atStartOfDay(ClockConfig.SCHOOL_ZONE).toInstant();
	}

}
