package com.admin.management.audit;

import java.io.IOException;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.admin.management.shared.table.TableEndpoints;
import com.admin.management.shared.table.TablePage;

import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code /api/audit-log}: the audit log can be searched and exported, nothing else. There is no way to add, edit or
 * delete an entry from the application (FR-024); entries are written only through {@link AuditService}.
 */
@RestController
@RequestMapping("/api/audit-log")
class AuditController {

	static final String AREA = "Audit log";

	private final TableEndpoints tables;

	AuditController(TableEndpoints tables) {
		this.tables = tables;
	}

	@PostMapping("/search")
	TablePage search(@RequestBody AuditLogRequest request) {
		return tables.search(AuditColumns.TABLE, request.tableRequest(), request.scope());
	}

	@PostMapping("/export")
	void export(@RequestBody AuditLogRequest request, HttpServletResponse response) throws IOException {
		tables.export(AREA, "Audit log", AuditColumns.TABLE, request.tableRequest(), request.scope(), response);
	}

}
