package com.admin.management.students;

import java.time.LocalDate;
import java.util.List;

import com.admin.management.shared.masking.SensitiveMasker;

/**
 * A student as shown on screen (contracts/openapi.yaml {@code Student}). Aadhaar and religion/caste are masked
 * (last 4 characters visible); the full values leave the server only through the edit form endpoint.
 */
public record StudentDto(Long id, Long version, String grNo, Long srNo, String surname, String firstName,
		String middleName, String gender, LocalDate dob, String birthPlace, String fatherName, String motherName,
		String mobile1, String mobile2, String address, String aadhaar, String religionCaste,
		LocalDate admissionDate, String admissionStandardName, String previousSchool, Long photoVersion,
		String status, String source, List<EnrollmentDto> classHistory) {

	static StudentDto of(Student s, String admissionStandardName, List<EnrollmentDto> classHistory) {
		return new StudentDto(s.getId(), s.getVersion(), s.getGrNo(), s.getSrNo(), s.getSurname(), s.getFirstName(),
				s.getMiddleName(), s.getGender(), s.getDob(), s.getBirthPlace(), s.getFatherName(),
				s.getMotherName(), s.getMobile1(), s.getMobile2(), s.getAddress(),
				SensitiveMasker.mask(s.getAadhaar()), SensitiveMasker.mask(s.getReligionCaste()),
				s.getAdmissionDate(), admissionStandardName, s.getPreviousSchool(), s.getPhotoVersion(),
				s.getStatus(), s.getSource(), classHistory);
	}

	@Override
	public String toString() {
		return "StudentDto[id=" + id + ", grNo=" + grNo + "]";
	}

}
