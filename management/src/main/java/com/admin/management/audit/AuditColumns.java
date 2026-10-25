package com.admin.management.audit;

import java.util.List;

import com.admin.management.shared.table.ColumnDef;
import com.admin.management.shared.table.TableDef;

/**
 * The audit log's columns (FR-022, FR-026). Everything shown is searchable and sortable. The user is the signed-in
 * name, or the name typed in a failed sign-in. {@code changes} is jsonb, so it is read as text, already masked when
 * it was written (FR-025).
 */
final class AuditColumns {

	static final TableDef<AuditEntry> TABLE = TableDef
		.of(AuditEntry.class,
				List.of(ColumnDef.<AuditEntry>number("id", "Id").asInternal(),
						ColumnDef.<AuditEntry>date("at", "Date and time"),
						ColumnDef.<AuditEntry>text("user", "User")
							.expression((root, cb) -> cb.coalesce(root.<String>get("userName"),
									root.<String>get("usernameAttempted"))),
						ColumnDef.<AuditEntry>text("area", "Area"), ColumnDef.<AuditEntry>text("action", "Action"),
						ColumnDef.<AuditEntry>text("entity", "Record"),
						ColumnDef.<AuditEntry>text("changes", "What changed").path("changesText"),
						ColumnDef.<AuditEntry>text("reason", "Reason")))
		.sortedBy("at", -1);

	private AuditColumns() {
	}

}
