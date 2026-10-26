package com.admin.management.students;

import java.time.LocalDate;

/**
 * The editable details of a student, already cleaned (trimmed, mobile numbers as 10 digits, Aadhaar as 12 digits).
 * The category chosen at registration is not here: it is stored once and the yearly category is changed on the
 * class record (FR-006).
 */
public record StudentDetails(String grNo, String surname, String firstName, String middleName, String gender,
		LocalDate dob, String birthPlace, String fatherName, String motherName, String mobile1, String mobile2,
		String address, String aadhaar, String religionCaste, LocalDate admissionDate, Long admissionStandardId,
		String previousSchool) {

	/** Never prints Aadhaar or religion/caste (constitution VI). */
	@Override
	public String toString() {
		return "StudentDetails[grNo=" + grNo + "]";
	}

}
