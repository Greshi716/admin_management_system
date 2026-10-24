package com.admin.management.shared.table;

import java.util.List;
import java.util.Map;

import com.admin.management.shared.web.ApiProblem;
import com.admin.management.shared.web.ValidationException;

/**
 * A list query. It mirrors PrimeNG's lazy-load event so the table sends it unchanged
 * (contracts/openapi.yaml {@code TableRequest}).
 *
 * @param sortOrder 1 ascending, -1 descending
 * @param filters per column: the value and a match mode (contains, startsWith, equals, dateRange, boolean)
 */
public record TableRequest(Integer first, Integer rows, String sortField, Integer sortOrder, String globalFilter,
		Map<String, Filter> filters) {

	public static final List<Integer> PAGE_SIZES = List.of(10, 25, 50, 100);

	public static final int DEFAULT_ROWS = 25;

	public record Filter(Object value, String matchMode) {
	}

	public TableRequest {
		first = first == null ? 0 : first;
		rows = rows == null ? DEFAULT_ROWS : rows;
		filters = filters == null ? Map.of() : Map.copyOf(filters);
	}

	/** A request for the first page with defaults and no search, filters or sort. */
	public static TableRequest firstPage() {
		return new TableRequest(null, null, null, null, null, null);
	}

	/** Checks the paging and sort values; filter values are checked against the list's columns later. */
	public void validate() {
		if (first < 0) {
			throw new ValidationException(List.of(new ApiProblem.FieldError("first", "must not be negative")));
		}
		if (!PAGE_SIZES.contains(rows)) {
			throw new ValidationException(
					List.of(new ApiProblem.FieldError("rows", "must be one of " + PAGE_SIZES)));
		}
		if (sortOrder != null && sortOrder != 1 && sortOrder != -1) {
			throw new ValidationException(List.of(new ApiProblem.FieldError("sortOrder", "must be 1 or -1")));
		}
	}

	/** Same request without paging, for an export of everything that matches. */
	public TableRequest forExport() {
		return new TableRequest(0, DEFAULT_ROWS, sortField, sortOrder, globalFilter, filters);
	}

}
