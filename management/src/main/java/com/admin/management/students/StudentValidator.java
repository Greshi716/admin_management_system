package com.admin.management.students;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.shared.web.ApiProblem.FieldError;
import com.admin.management.shared.web.ValidationException;

/**
 * The rules of FR-017, shared by registration and edit. All problems are collected and sent together, each with
 * the field it belongs to. A message never repeats the value that was typed (Aadhaar is sensitive, constitution VI).
 */
@Component
class StudentValidator {

	private static final Set<String> GENDERS = Set.of("Male", "Female", "Other");

	private static final Pattern TEN_DIGITS = Pattern.compile("\\d{10}");

	private static final Pattern TWELVE_DIGITS = Pattern.compile("\\d{12}");

	private final MasterDataLookup masterData;

	private final Clock clock;

	StudentValidator(MasterDataLookup masterData, Clock clock) {
		this.masterData = masterData;
		this.clock = clock;
	}

	/**
	 * Cleans and checks the details common to create and edit.
	 *
	 * @param keptAdmissionStandardId the admission standard the student already has (edit), which is allowed even
	 *            when inactive; null on create
	 */
	StudentDetails details(StudentInput in, Long keptAdmissionStandardId, List<FieldError> errors) {
		String grNo = required(in.grNo(), "grNo", "Enter the GR No.", errors);
		String surname = required(in.surname(), "surname", "Enter the surname.", errors);
		String firstName = required(in.firstName(), "firstName", "Enter the first name.", errors);
		String address = required(in.address(), "address", "Enter the address.", errors);

		String gender = trimToNull(in.gender());
		if (gender == null) {
			errors.add(new FieldError("gender", "Choose the gender."));
		}
		else if (!GENDERS.contains(gender)) {
			errors.add(new FieldError("gender", "Choose Male, Female or Other."));
		}

		if (in.dob() == null) {
			errors.add(new FieldError("dob", "Enter the date of birth."));
		}
		String mobile1 = trimToNull(in.mobile1()) == null ? null : mobile(in.mobile1());
		if (trimToNull(in.mobile1()) == null) {
			errors.add(new FieldError("mobile1", "Enter Mobile 1."));
		}
		else if (mobile1 == null) {
			errors.add(new FieldError("mobile1", "Mobile 1 must be a 10-digit number."));
		}
		String mobile2 = null;
		if (trimToNull(in.mobile2()) != null) {
			mobile2 = mobile(in.mobile2());
			if (mobile2 == null) {
				errors.add(new FieldError("mobile2", "Mobile 2 must be a 10-digit number."));
			}
		}
		String aadhaar = null;
		if (trimToNull(in.aadhaar()) != null) {
			String digits = in.aadhaar().replaceAll("\\s", "");
			if (TWELVE_DIGITS.matcher(digits).matches()) {
				aadhaar = digits;
			}
			else {
				errors.add(new FieldError("aadhaar", "Aadhaar must be a 12-digit number."));
			}
		}

		LocalDate today = LocalDate.now(clock);
		if (in.admissionDate() == null) {
			errors.add(new FieldError("admissionDate", "Enter the admission date."));
		}
		else if (in.admissionDate().isAfter(today)) {
			errors.add(new FieldError("admissionDate", "The admission date cannot be after today."));
		}
		else if (in.dob() != null && in.admissionDate().isBefore(in.dob())) {
			errors.add(new FieldError("admissionDate", "The admission date cannot be before the date of birth."));
		}

		if (in.admissionStandardId() == null) {
			errors.add(new FieldError("admissionStandardId", "Choose the admission standard."));
		}
		else if (!in.admissionStandardId().equals(keptAdmissionStandardId)
				&& !masterData.isStandardActive(in.admissionStandardId())) {
			errors.add(new FieldError("admissionStandardId", "Choose an active standard."));
		}

		return new StudentDetails(grNo, surname, firstName, trimToNull(in.middleName()), gender, in.dob(),
				trimToNull(in.birthPlace()), trimToNull(in.fatherName()), trimToNull(in.motherName()), mobile1, mobile2,
				address, aadhaar, trimToNull(in.religionCaste()), in.admissionDate(), in.admissionStandardId(),
				trimToNull(in.previousSchool()));
	}

	/** The class choices of a registration: current year, standard, division and category (all must be active). */
	void classChoice(StudentInput in, Long currentYearId, List<FieldError> errors) {
		if (in.standardId() == null) {
			errors.add(new FieldError("standardId", "Choose the standard."));
		}
		else if (!masterData.isStandardActive(in.standardId())) {
			errors.add(new FieldError("standardId", "Choose an active standard."));
		}
		if (in.divisionId() == null) {
			errors.add(new FieldError("divisionId", "Choose the division."));
		}
		else if (in.standardId() != null && !masterData.divisionBelongsTo(in.divisionId(), currentYearId, in.standardId())) {
			errors.add(new FieldError("divisionId",
					"The division does not belong to the chosen standard in the current academic year."));
		}
		else if (!masterData.isDivisionActive(in.divisionId())) {
			errors.add(new FieldError("divisionId", "Choose an active division."));
		}
		if (in.categoryId() == null) {
			errors.add(new FieldError("categoryId", "Choose the category."));
		}
		else if (!masterData.isCategoryActive(in.categoryId())) {
			errors.add(new FieldError("categoryId", "Choose an active category."));
		}
	}


	/**
	 * The choices of a class record change: the division must belong to the record's own year and the chosen
	 * standard; a value that is not active is accepted only when the record already holds it.
	 */
	void enrollmentChoice(EnrollmentInput in, StudentEnrollment record, List<FieldError> errors) {
		if (in.standardId() == null) {
			errors.add(new FieldError("standardId", "Choose the standard."));
		}
		else if (!in.standardId().equals(record.getStandardId()) && !masterData.isStandardActive(in.standardId())) {
			errors.add(new FieldError("standardId", "Choose an active standard."));
		}
		if (in.divisionId() == null) {
			errors.add(new FieldError("divisionId", "Choose the division."));
		}
		else if (in.standardId() != null
				&& !masterData.divisionBelongsTo(in.divisionId(), record.getAcademicYearId(), in.standardId())) {
			errors.add(new FieldError("divisionId",
					"The division does not belong to the chosen standard in that academic year."));
		}
		else if (!in.divisionId().equals(record.getDivisionId()) && !masterData.isDivisionActive(in.divisionId())) {
			errors.add(new FieldError("divisionId", "Choose an active division."));
		}
		if (in.categoryId() == null) {
			errors.add(new FieldError("categoryId", "Choose the category."));
		}
		else if (!in.categoryId().equals(record.getCategoryId()) && !masterData.isCategoryActive(in.categoryId())) {
			errors.add(new FieldError("categoryId", "Choose an active category."));
		}
	}

	static void throwIfAny(List<FieldError> errors) {
		if (!errors.isEmpty()) {
			throw new ValidationException(errors);
		}
	}

	static List<FieldError> newErrors() {
		return new ArrayList<>();
	}

	/** A 10-digit number after removing spaces, hyphens, a leading +91 or a leading 0; null when it is not one. */
	static String mobile(String raw) {
		String digits = raw.replaceAll("[\\s-]", "");
		if (digits.startsWith("+91")) {
			digits = digits.substring(3);
		}
		else if (digits.startsWith("0")) {
			digits = digits.substring(1);
		}
		return TEN_DIGITS.matcher(digits).matches() ? digits : null;
	}

	private static String required(String value, String field, String message, List<FieldError> errors) {
		String text = trimToNull(value);
		if (text == null) {
			errors.add(new FieldError(field, message));
		}
		return text;
	}

	static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
