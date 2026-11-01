package com.admin.management.students;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.shared.ClassRecordFees;
import com.admin.management.shared.web.ApiProblem.FieldError;
import com.admin.management.shared.web.ConflictException;
import com.admin.management.shared.web.ValidationException;
import com.admin.management.students.api.StudentRegistration;

/**
 * Implements the registration path for approved admissions (FR-034, FR-038).
 * Shares validation, GR number check, enrollment write, fee account hook and audit with {@link StudentService},
 * but registers the student in the year chosen at review and marks the entry source (e.g. "Google Form").
 */
@Service
public class StudentRegistrationService implements StudentRegistration {

	private final StudentRepository students;
	private final EnrollmentRepository enrollments;
	private final GrNumberRepository grNumbers;
	private final StudentAudit audit;
	private final StudentValidator validator;
	private final MasterDataLookup masterData;
	private final Clock clock;
	private final ClassRecordFees classRecordFees;

	public StudentRegistrationService(StudentRepository students, EnrollmentRepository enrollments,
			GrNumberRepository grNumbers, StudentAudit audit, StudentValidator validator,
			MasterDataLookup masterData, Clock clock, ClassRecordFees classRecordFees) {
		this.students = students;
		this.enrollments = enrollments;
		this.grNumbers = grNumbers;
		this.audit = audit;
		this.validator = validator;
		this.masterData = masterData;
		this.clock = clock;
		this.classRecordFees = classRecordFees;
	}

	@Override
	@Transactional
	public long registerFromAdmission(RegistrationInput in, long academicYearId, String source) {
		LocalDate yearStartDate = masterData.yearStartDate(academicYearId)
				.orElseThrow(() -> new ValidationException("academicYear", "The chosen academic year does not exist."));

		StudentInput studentInput = new StudentInput(
				null, in.grNo(), in.surname(), in.firstName(), in.middleName(),
				in.gender(), in.dob(), in.birthPlace(), in.fatherName(), in.motherName(),
				in.mobile1(), in.mobile2(), in.address(), in.aadhaar(), in.religionCaste(),
				in.categoryId(), in.admissionDate(), in.admissionStandardId(), in.previousSchool(),
				in.standardId(), in.divisionId()
		);

		List<FieldError> errors = StudentValidator.newErrors();
		StudentDetails details = validator.details(studentInput, null, errors);
		validator.classChoice(studentInput, academicYearId, errors);
		StudentValidator.throwIfAny(errors);

		checkGrNoFree(details.grNo());

		String admissionSource = source != null && !source.isBlank() ? source : "Google Form";
		Student student = students.save(new Student(details, in.categoryId(), admissionSource));
		grNumbers.save(new StudentGrNumber(details.grNo(), student.getId(), Instant.now(clock)));

		StudentEnrollment enrollment = enrollments.save(new StudentEnrollment(
				student.getId(), academicYearId, in.standardId(), in.divisionId(), in.categoryId(), yearStartDate));

		audit.created(StudentAudit.STUDENT, student.getId(), student.snapshot());
		audit.created(StudentAudit.ENROLLMENT, enrollment.getId(), enrollment.snapshot());

		classRecordFees.classRecordAdded(enrollment.getId(), academicYearId, enrollment.getStandardId(),
				enrollment.getCategoryId());

		return student.getId();
	}

	private void checkGrNoFree(String grNo) {
		if (grNumbers.existsById(StudentGrNumber.keyOf(grNo))) {
			throw new ConflictException("grNo", "This GR No. is already used by a student.");
		}
	}

}
