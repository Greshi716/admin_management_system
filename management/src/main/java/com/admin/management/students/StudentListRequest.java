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
		return new TableRequest(first, rows, sortField, sortOrder, globalFilter, filters);
	}

}
