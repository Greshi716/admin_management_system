package com.admin.management.shared.table;

import java.util.List;

/**
 * What a list shows: the entity it reads, its columns, and the order used when the request names none.
 * Every list declares one of these and gets paging, search, filters, sort and export (FR-034).
 *
 * @param defaultSortField a column {@code field}, or null to order by {@code id}
 * @param defaultSortOrder 1 ascending, -1 descending
 */
public record TableDef<T>(Class<T> entity, List<ColumnDef<T>> columns, String defaultSortField,
		int defaultSortOrder) {

	public TableDef {
		columns = List.copyOf(columns);
	}

	public static <T> TableDef<T> of(Class<T> entity, List<ColumnDef<T>> columns) {
		return new TableDef<>(entity, columns, null, 1);
	}

	public TableDef<T> sortedBy(String field, int order) {
		return new TableDef<>(entity, columns, field, order);
	}

	ColumnDef<T> column(String field) {
		return columns.stream().filter(c -> c.field().equals(field)).findFirst().orElse(null);
	}

}
