package com.admin.management.masterdata;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.admin.management.audit.AuditService;

/**
 * Writes the audit entry of a master data change in the caller's transaction, so the change and its entry are
 * saved together or not at all (FR-021, FR-023). The record type and id go in the entity column, for example
 * {@code AcademicYear#12}; the old and new values go in the changes.
 */
@Component
public class MasterDataAudit {

	public static final String AREA = "Master data";

	private final AuditService audit;

	public MasterDataAudit(AuditService audit) {
		this.audit = audit;
	}

	public void created(String type, Long id, Map<String, Object> values) {
		audit.record(AREA, "Create", type + "#" + id, Map.of("new", values), null);
	}

	/** Records only the fields that changed. An edit that changes nothing writes no entry. */
	public void edited(String type, Long id, Map<String, Object> before, Map<String, Object> after) {
		Map<String, Object> oldValues = new LinkedHashMap<>();
		Map<String, Object> newValues = new LinkedHashMap<>();
		after.forEach((field, value) -> {
			if (!Objects.equals(before.get(field), value)) {
				oldValues.put(field, before.get(field));
				newValues.put(field, value);
			}
		});
		if (!newValues.isEmpty()) {
			audit.record(AREA, "Edit", type + "#" + id, Map.of("old", oldValues, "new", newValues), null);
		}
	}

	public void deleted(String type, Long id, Map<String, Object> values) {
		audit.record(AREA, "Delete", type + "#" + id, Map.of("old", values), null);
	}

}
