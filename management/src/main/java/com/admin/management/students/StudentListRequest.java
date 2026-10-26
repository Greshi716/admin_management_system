package com.admin.management.students;

import java.util.Map;

import com.admin.management.shared.table.TableRequest;

/**
 * The standard table request plus the status scope of the Students list (contracts/openapi.yaml): {@code Active}
 * (the default), {@code Left} or {@code All}.
 */
public record StudentListRequest(Integer first, Integer rows, String sortField, Integer sortOrder,
		String globalFilter, Map<String, TableRequest.Filter> filters, String status) {

	TableRequest table() {
		return new TableRequest(first, rows, sortField, sortOrder, searchText(globalFilter), filters);
	}

	/**
	 * Mobile numbers are stored as 10 digits, so a search typed as a whole mobile number with {@code +91}, a leading
	 * 0, spaces or dashes is turned into those 10 digits. Any other search text is left as it is.
	 */
	static String searchText(String text) {
		if (text == null || text.isBlank()) {
			return text;
		}
		String digits = StudentValidator.mobile(text.trim());
		return digits != null ? digits : text;
	}

}
