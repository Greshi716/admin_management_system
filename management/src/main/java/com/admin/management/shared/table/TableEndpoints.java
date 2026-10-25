package com.admin.management.shared.table;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.admin.management.audit.AuditService;
import com.admin.management.audit.SensitiveMasker;
import com.admin.management.shared.excel.ExcelExporter;

import jakarta.servlet.http.HttpServletResponse;

/**
 * What a controller calls to serve a list: {@link #search} for a page and {@link #export} for the Excel file.
 * A new list only declares its {@link TableDef} and gets paging, search, filters, sort and export (FR-034).
 */
@Component
public class TableEndpoints {

	private final TableQueryService queries;

	private final ExcelExporter exporter;

	private final AuditService audit;

	private final Clock clock;

	public TableEndpoints(TableQueryService queries, ExcelExporter exporter, AuditService audit, Clock clock) {
		this.queries = queries;
		this.exporter = exporter;
		this.audit = audit;
		this.clock = clock;
	}

	public <T> TablePage search(TableDef<T> def, TableRequest request) {
		return queries.page(def, request);
	}

	public <T> TablePage search(TableDef<T> def, TableRequest request, TableQueryService.Scope<T> scope) {
		return queries.page(def, request, scope);
	}

	/**
	 * Streams everything that matches the request as an Excel file to the response. The {@code Export} audit
	 * entry is written only after the file has been produced, so an export that failed is not logged as done.
	 *
	 * @param area audit area, for example {@code Master data}
	 * @param listName the list's name, used for the sheet, the file name and the audit entry
	 */
	public <T> void export(String area, String listName, TableDef<T> def, TableRequest request,
			TableQueryService.Scope<T> scope, HttpServletResponse response) throws IOException {
		response.setContentType(ExcelExporter.CONTENT_TYPE);
		response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName(listName) + "\"");
		long rows;
		try {
			rows = exporter.write(listName, def.columns().stream().filter((column) -> !column.internal()).toList(), sink -> queries.stream(def, request, scope, sink),
					response.getOutputStream());
		}
		catch (IOException | RuntimeException ex) {
			if (!response.isCommitted()) {
				response.reset();
			}
			throw ex;
		}
		audit.recordIndependent(area, "Export", listName, auditChanges(def, request, rows), null);
	}

	private String fileName(String listName) {
		String slug = listName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		return (slug.isEmpty() ? "export" : slug) + "-" + LocalDate.now(clock) + ".xlsx";
	}

	/** What was exported: the row count and the search and filters, with values of sensitive columns masked. */
	private <T> Map<String, Object> auditChanges(TableDef<T> def, TableRequest request, long rows) {
		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put("rows", rows);
		if (request.globalFilter() != null && !request.globalFilter().isBlank()) {
			changes.put("search", request.globalFilter());
		}
		Map<String, Object> filters = new LinkedHashMap<>();
		request.filters().forEach((field, filter) -> {
			ColumnDef<T> column = def.column(field);
			if (filter != null && filter.value() != null) {
				Object shown = filter.value() instanceof List<?> ? filter.value() : filter.value().toString();
				boolean sensitive = column != null && column.sensitive();
				filters.put(field, (filter.matchMode() == null ? "contains" : filter.matchMode()) + ": "
						+ (sensitive ? SensitiveMasker.mask(shown.toString()) : shown));
			}
		});
		if (!filters.isEmpty()) {
			changes.put("filters", filters);
		}
		if (request.sortField() != null) {
			changes.put("sort", request.sortField() + (Integer.valueOf(-1).equals(request.sortOrder()) ? " desc" : " asc"));
		}
		return changes;
	}

}
