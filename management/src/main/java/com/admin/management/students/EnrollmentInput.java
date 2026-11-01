package com.admin.management.students;

/** Request body of a class record change (contracts/openapi.yaml {@code EnrollmentInput}). */
public record EnrollmentInput(Long version, Long standardId, Long divisionId, Long categoryId, String stream) {

	public EnrollmentInput(Long version, Long standardId, Long divisionId, Long categoryId) {
		this(version, standardId, divisionId, categoryId, null);
	}

}
