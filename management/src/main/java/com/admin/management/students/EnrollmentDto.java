package com.admin.management.students;

/** One row of the class history (contracts/openapi.yaml {@code Enrollment}). */
public record EnrollmentDto(Long version, Long academicYearId, String academicYearName, Long standardId,
		Long divisionId, String stdDiv, Long categoryId, String categoryName, String yearStatus) {
}
