package com.admin.management.admissions;

import java.time.LocalDate;
import java.util.Map;

/**
 * Payload for approving an admission request (FR-033, FR-034).
 */
public record ApproveInput(
		String grNo,
		Long academicYearId,
		Long divisionId,
		Long categoryId,
		LocalDate admissionDate,
		Map<String, Object> form,
		Long version
) {
}
