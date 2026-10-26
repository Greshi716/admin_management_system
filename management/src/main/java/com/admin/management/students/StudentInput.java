package com.admin.management.students;

import java.time.LocalDate;

/**
 * Request body of a registration or an edit, and the response of the edit form (contracts/openapi.yaml
 * {@code StudentInput}). {@code standardId}, {@code divisionId} and {@code categoryId} are used on create only;
 * {@code version} is required on edit. Values are text as typed; the validator cleans them.
 */
public record StudentInput(Long version, String grNo, String surname, String firstName, String middleName,
		String gender, LocalDate dob, String birthPlace, String fatherName, String motherName, String mobile1,
		String mobile2, String address, String aadhaar, String religionCaste, Long categoryId,
		LocalDate admissionDate, Long admissionStandardId, String previousSchool, Long standardId, Long divisionId) {

	/** Never prints Aadhaar or religion/caste (constitution VI). */
	@Override
	public String toString() {
		return "StudentInput[grNo=" + grNo + "]";
	}

}
