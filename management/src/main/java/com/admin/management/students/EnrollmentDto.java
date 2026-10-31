package com.admin.management.students;

/**
 * One row of the class history (contracts/openapi.yaml {@code Enrollment}). {@code feeLocked} is true when the year's fee
 * structure is finalised, so the standard and category are fixed and only the division can change (F2 FR-008).
 * {@code stream} is 'Science' or 'Commerce' for stream standards, null otherwise (F4 FR-012).
 */
public record EnrollmentDto(Long version, Long academicYearId, String academicYearName, Long standardId,
		Long divisionId, String stdDiv, Long categoryId, String categoryName, String yearStatus,
		boolean feeLocked, String stream) {
}
