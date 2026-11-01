package com.admin.management.students.api;

import java.time.LocalDate;

/**
 * Public interface for registering a student from an approved admission request (FR-034, FR-038).
 */
public interface StudentRegistration {

	/**
	 * Registers a student approved from an admission request, creating the student row, GR number reservation,
	 * class record in {@code academicYearId}, and fee account hook.
	 *
	 * @param input          the student details
	 * @param academicYearId the selected academic year ID
	 * @param source         the admission source (e.g. "Google Form")
	 * @return the created student ID
	 */
	long registerFromAdmission(RegistrationInput input, long academicYearId, String source);

	record RegistrationInput(String grNo, String surname, String firstName, String middleName,
			String gender, LocalDate dob, String birthPlace, String fatherName, String motherName,
			String mobile1, String mobile2, String address, String aadhaar, String religionCaste,
			Long categoryId, LocalDate admissionDate, Long admissionStandardId, String previousSchool,
			Long standardId, Long divisionId) {

		@Override
		public String toString() {
			return "RegistrationInput[grNo=" + grNo + "]";
		}
	}

}
