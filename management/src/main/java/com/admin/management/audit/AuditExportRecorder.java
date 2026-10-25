package com.admin.management.audit;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.admin.management.shared.table.ExportRecorder;

/** Writes the {@code Export} audit entry for the shared table export (FR-027). */
@Component
class AuditExportRecorder implements ExportRecorder {

	private final AuditService audit;

	AuditExportRecorder(AuditService audit) {
		this.audit = audit;
	}

	@Override
	public void recordExport(String area, String listName, Map<String, Object> changes) {
		audit.recordIndependent(area, "Export", listName, changes, null);
	}

}
