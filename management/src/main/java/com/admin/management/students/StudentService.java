package com.admin.management.students;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.masterdata.api.MasterDataLookup.PickLists;
import com.admin.management.masterdata.api.MasterDataLookup.YearRef;
import com.admin.management.shared.web.ApiProblem.FieldError;
import com.admin.management.shared.web.ConflictException;
import com.admin.management.shared.web.NotFoundException;
import com.admin.management.shared.web.StaleVersionException;
import com.admin.management.shared.web.ValidationException;

/**
 * Registers students and reads them for the screens. Every change and its audit entries are saved in one
 * transaction (FR-012); nothing here deletes a student, a class record or a GR No. (constitution IV).
 */
@Service
public class StudentService {

	static final int HISTORY_LIMIT = 200;

	private final StudentRepository students;

	private final EnrollmentRepository enrollments;

	private final GrNumberRepository grNumbers;

	private final StudentAudit audit;

	private final StudentValidator validator;

	private final MasterDataLookup masterData;

	private final Clock clock;

	StudentService(StudentRepository students, EnrollmentRepository enrollments, GrNumberRepository grNumbers,
			StudentAudit audit, StudentValidator validator, MasterDataLookup masterData, Clock clock) {
		this.students = students;
		this.enrollments = enrollments;
		this.grNumbers = grNumbers;
		this.audit = audit;
		this.validator = validator;
		this.masterData = masterData;
		this.clock = clock;
	}

	/**
	 * Registers a student and the class record of the current academic year (FR-001, FR-006). The student is
	 * Active with entry source Manual; the serial number comes from the database sequence.
	 */
	@Transactional
	public StudentDto register(StudentInput in) {
		YearRef year = masterData.currentYear()
			.orElseThrow(() -> new ValidationException("academicYear",
					"There is no current academic year. Add one under Setup first."));
		List<FieldError> errors = StudentValidator.newErrors();
		StudentDetails details = validator.details(in, null, errors);
		validator.classChoice(in, year.id(), errors);
		StudentValidator.throwIfAny(errors);
		checkGrNoFree(details.grNo());

		Student student = students.save(new Student(details, in.categoryId()));
		grNumbers.save(new StudentGrNumber(details.grNo(), student.getId(), Instant.now(clock)));
		StudentEnrollment enrollment = enrollments.save(new StudentEnrollment(student.getId(), year.id(),
				in.standardId(), in.divisionId(), in.categoryId(), year.startDate()));
		audit.created(StudentAudit.STUDENT, student.getId(), student.snapshot());
		audit.created(StudentAudit.ENROLLMENT, enrollment.getId(), enrollment.snapshot());
		return toDto(student);
	}

	/**
	 * The values for the edit form, with the FULL Aadhaar and religion/caste. The only place they leave the server
	 * unmasked (FR-010). The category is not part of an edit (the yearly one changes on the class record).
	 */
	@Transactional(readOnly = true)
	public StudentInput editForm(Long id) {
		Student s = students.findById(id).orElseThrow(() -> new NotFoundException("Student", id));
		return new StudentInput(s.getVersion(), s.getGrNo(), s.getSurname(), s.getFirstName(), s.getMiddleName(),
				s.getGender(), s.getDob(), s.getBirthPlace(), s.getFatherName(), s.getMotherName(), s.getMobile1(),
				s.getMobile2(), s.getAddress(), s.getAadhaar(), s.getReligionCaste(), null, s.getAdmissionDate(),
				s.getAdmissionStandardId(), s.getPreviousSchool(), null, null);
	}


	/**
	 * Edits the details of a student, including a GR No. correction (FR-004, FR-017, FR-018). A new GR No. is
	 * reserved and the old one stays reserved. The status, the serial number and the category are not changed here.
	 */
	@Transactional
	public StudentDto edit(Long id, StudentInput in) {
		Student student = students.findById(id).orElseThrow(() -> new NotFoundException("Student", id));
		checkVersion(in.version(), student.getVersion(), () -> toDto(student));
		List<FieldError> errors = StudentValidator.newErrors();
		StudentDetails details = validator.details(in, student.getAdmissionStandardId(), errors);
		StudentValidator.throwIfAny(errors);
		boolean newKey = !StudentGrNumber.keyOf(details.grNo()).equals(StudentGrNumber.keyOf(student.getGrNo()));
		if (newKey) {
			checkGrNoFree(details.grNo());
			grNumbers.save(new StudentGrNumber(details.grNo(), id, Instant.now(clock)));
		}
		Map<String, Object> before = student.snapshot();
		student.apply(details);
		Student saved = students.saveAndFlush(student);
		audit.edited(StudentAudit.STUDENT, id, before, saved.snapshot());
		return toDto(saved);
	}

	/** Active to Left, once and for good: there is no way back (FR-004). */
	@Transactional
	public StudentDto markLeft(Long id, Long version) {
		Student student = students.findById(id).orElseThrow(() -> new NotFoundException("Student", id));
		checkVersion(version, student.getVersion(), () -> toDto(student));
		if (Student.LEFT.equals(student.getStatus())) {
			throw new ValidationException("status", "This student is already marked as left.");
		}
		Map<String, Object> before = student.snapshot();
		student.markLeft();
		Student saved = students.saveAndFlush(student);
		audit.edited(StudentAudit.STUDENT, id, before, saved.snapshot());
		return toDto(saved);
	}

	/**
	 * Changes standard, division and category of one year's class record (FR-006). The year cannot change and no
	 * second record is ever made. A value that is no longer active is allowed only when the record already has it.
	 */
	@Transactional
	public EnrollmentDto changeEnrollment(Long studentId, Long yearId, EnrollmentInput in) {
		students.findById(studentId).orElseThrow(() -> new NotFoundException("Student", studentId));
		StudentEnrollment record = enrollments.findByStudentIdAndAcademicYearId(studentId, yearId)
			.orElseThrow(() -> new NotFoundException("Class record", yearId));
		checkVersion(in.version(), record.getVersion(), () -> toDto(record));
		List<FieldError> errors = StudentValidator.newErrors();
		validator.enrollmentChoice(in, record, errors);
		StudentValidator.throwIfAny(errors);
		Map<String, Object> before = record.snapshot();
		record.change(in.standardId(), in.divisionId(), in.categoryId());
		StudentEnrollment saved = enrollments.saveAndFlush(record);
		audit.edited(StudentAudit.ENROLLMENT, saved.getId(), before, saved.snapshot());
		return toDto(saved);
	}

	private static void checkVersion(Long sent, Long actual, java.util.function.Supplier<Object> latest) {
		if (sent == null) {
			throw new ValidationException("version", "The version is missing. Reload the record and try again.");
		}
		if (!sent.equals(actual)) {
			throw new StaleVersionException(latest.get());
		}
	}

	/** Students (any status) with the same three names; never blocks a save (FR-003). */
	@Transactional(readOnly = true)
	public List<NameMatch> nameMatches(String surname, String firstName, String middleName, Long excludeId) {
		return students
			.findNameMatches(normalised(surname), normalised(firstName), normalised(middleName),
					excludeId == null ? -1L : excludeId)
			.stream()
			.map((s) -> new NameMatch(s.getId(), s.getGrNo(), fullName(s), s.getStatus()))
			.toList();
	}

	/** The choices for the forms: the divisions of {@code yearId} (default the current year). */
	@Transactional(readOnly = true)
	public PickListsDto pickLists(Long yearId, Long studentId) {
		Optional<YearRef> current = masterData.currentYear();
		Long chosenYear = yearId != null ? yearId : current.map(YearRef::id).orElse(null);
		if (chosenYear == null) {
			throw new ConflictException("academicYear", "There is no current academic year.");
		}
		Long keepStandard = null;
		Long keepDivision = null;
		Long keepCategory = null;
		if (studentId != null) {
			Optional<StudentEnrollment> own = enrollments.findByStudentIdAndAcademicYearId(studentId, chosenYear);
			if (own.isPresent()) {
				keepStandard = own.get().getStandardId();
				keepDivision = own.get().getDivisionId();
				keepCategory = own.get().getCategoryId();
			}
		}
		PickLists lists = masterData.pickLists(chosenYear, keepStandard, keepDivision, keepCategory);
		return new PickListsDto(current.map((y) -> new PickListsDto.CurrentYear(y.id(), y.name())).orElse(null),
				lists.standards(), lists.divisions(), lists.categories());
	}

	/** The profile: the student with masked sensitive fields and the class history (FR-007, FR-009). */
	@Transactional(readOnly = true)
	public StudentDto get(Long id) {
		return toDto(students.findById(id).orElseThrow(() -> new NotFoundException("Student", id)));
	}

	/** The newest {@value #HISTORY_LIMIT} audit entries about the student and its class records (FR-019). */
	@Transactional(readOnly = true)
	public StudentHistoryDto history(Long id) {
		students.findById(id).orElseThrow(() -> new NotFoundException("Student", id));
		List<Long> recordIds = enrollments.findByStudentId(id).stream().map(StudentEnrollment::getId).toList();
		var found = audit.history(id, recordIds, HISTORY_LIMIT);
		return new StudentHistoryDto(
				found.entries().stream()
					.map((e) -> new StudentHistoryDto.Entry(e.at(), e.userName(), e.action(), e.entity(), e.changes()))
					.toList(),
				found.truncated());
	}

	/** The student as shown on screen, with masked sensitive fields and the class history, newest year first. */
	StudentDto toDto(Student student) {
		Map<Long, String> standardNames = masterData.standardNames();
		Map<Long, String> divisionNames = masterData.divisionNames();
		Map<Long, String> categoryNames = masterData.categoryNames();
		Map<Long, String> yearNames = masterData.yearNames();
		List<EnrollmentDto> history = enrollments.findByStudentId(student.getId())
			.stream()
			.sorted(Comparator.comparing(StudentEnrollment::getYearStartDate).reversed())
			.map((e) -> toDto(e, yearNames, standardNames, divisionNames, categoryNames))
			.toList();
		return StudentDto.of(student, standardNames.getOrDefault(student.getAdmissionStandardId(), ""), history);
	}

	EnrollmentDto toDto(StudentEnrollment e) {
		return toDto(e, masterData.yearNames(), masterData.standardNames(), masterData.divisionNames(),
				masterData.categoryNames());
	}

	private static EnrollmentDto toDto(StudentEnrollment e, Map<Long, String> yearNames,
			Map<Long, String> standardNames, Map<Long, String> divisionNames, Map<Long, String> categoryNames) {
		String stdDiv = standardNames.getOrDefault(e.getStandardId(), "") + "-"
				+ divisionNames.getOrDefault(e.getDivisionId(), "");
		return new EnrollmentDto(e.getVersion(), e.getAcademicYearId(), yearNames.get(e.getAcademicYearId()),
				e.getStandardId(), e.getDivisionId(), stdDiv, e.getCategoryId(),
				categoryNames.get(e.getCategoryId()), e.getYearStatus());
	}

	/** A GR No. any student has or ever had is refused (FR-002), whatever the capitals or the spaces around it. */
	void checkGrNoFree(String grNo) {
		if (grNumbers.existsById(StudentGrNumber.keyOf(grNo))) {
			throw new ConflictException("grNo", "This GR No. is already used by a student.");
		}
	}

	static String fullName(Student s) {
		return (s.getSurname() + " " + s.getFirstName() + (s.getMiddleName() == null ? "" : " " + s.getMiddleName()))
			.trim();
	}

	private static String normalised(String name) {
		return name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
	}

}
