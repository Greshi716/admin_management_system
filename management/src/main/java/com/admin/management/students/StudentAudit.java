package com.admin.management.students;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.admin.management.audit.AuditService;

/**
 * Writes the audit entries of student changes in the caller's transaction, so the change and its entry are saved
 * together or not at all (FR-012). The entity column holds {@code Student#id} or {@code StudentEnrollment#id}.
 * Aadhaar and religion/caste are masked by the audit service when the entry is stored.
 */
@Component
class StudentAudit {

	static final String AREA = "Students";

	static final String STUDENT = "Student";

	static final String ENROLLMENT = "StudentEnrollment";

	private final AuditService audit;

	StudentAudit(AuditService audit) {
		this.audit = audit;
	}

	void created(String type, Long id, Map<String, Object> values) {
		audit.record(AREA, "Create", type + "#" + id, Map.of("new", values), null);
	}

	/** Records only the fields that changed. A save that changes nothing writes no entry. */
	void edited(String type, Long id, Map<String, Object> before, Map<String, Object> after) {
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

	/** A photo was added, replaced or removed. The image itself is never stored in the log. */
	void photoChanged(Long studentId, Long oldVersion, Long newVersion) {
		Map<String, Object> oldValues = new LinkedHashMap<>();
		oldValues.put("photo", oldVersion == null ? "none" : "version " + oldVersion);
		Map<String, Object> newValues = new LinkedHashMap<>();
		newValues.put("photo", newVersion == null ? "none" : "version " + newVersion);
		audit.record(AREA, "Edit", STUDENT + "#" + studentId, Map.of("old", oldValues, "new", newValues), null);
	}

}
