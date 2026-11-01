package com.admin.management.admissions;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Detailed view of an admission request (FR-033, FR-035).
 * Sensitive fields (Aadhaar, religion/caste) in {@code form} are masked.
 */
public record AdmissionDetailDto(
		Long id,
		String status,
		boolean duplicateFlag,
		Map<String, Object> duplicateOf,
		List<DuplicateMatchDto> duplicateMatches,
		Map<String, Object> form,
		Long version,
		Instant submittedAt,
		String remarks,
		Long studentId,
		Long reviewedBy,
		Instant reviewedAt
) {
	public record DuplicateMatchDto(
			String type,
			Long id,
			String label,
			String link
	) {
	}
}
