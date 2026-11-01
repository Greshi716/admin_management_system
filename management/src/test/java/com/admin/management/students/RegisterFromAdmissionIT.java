package com.admin.management.students;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.shared.web.ConflictException;
import com.admin.management.students.api.StudentRegistration.RegistrationInput;
import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;

@Covers("FR-034")
@Covers("FR-038")
class RegisterFromAdmissionIT extends IntegrationTest {

	@Autowired
	StudentRegistrationService registrationService;

	@Autowired
	StudentService studentService;

	@Autowired
	StudentRepository studentRepo;

	@Autowired
	EnrollmentRepository enrollmentRepo;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	@DisplayName("FR-038: Manual F1 registration remains unchanged and sets source Manual")
	void manualRegistrationUnchanged() {
		LocalDate today = LocalDate.now();
		long currentYearId = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values (?, ?, ?) returning id", Long.class,
				"RFA Current " + today.getYear(), java.sql.Date.valueOf(today.minusMonths(1)),
				java.sql.Date.valueOf(today.plusMonths(11)));

		long stdId = jdbc.queryForObject(
				"insert into standards (name, display_order) values ('RFA Std 1', 1) returning id", Long.class);
		long divId = jdbc.queryForObject(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A') returning id",
				Long.class, currentYearId, stdId);
		long catId = jdbc.queryForList("select id from student_categories order by id", Long.class).get(0);

		StudentInput input = new StudentInput(
				null, "GR-M1", "Patil", "Rahul", "Ashok",
				"Male", LocalDate.of(2015, 6, 10), "Mumbai", "Ashok", "Kavita",
				"9876543210", null, "123 Main Street, Mumbai", null, null,
				catId, today, stdId, null, stdId, divId
		);

		StudentDto dto = studentService.register(input);
		assertThat(dto.id()).isNotNull();

		Student student = studentRepo.findById(dto.id()).orElseThrow();
		assertThat(student.getSource()).isEqualTo(Student.MANUAL);
	}

	@Test
	@DisplayName("FR-034: Registration from admission creates student with source Google Form in the chosen year")
	void registerFromAdmissionIntoLaterYear() {
		LocalDate today = LocalDate.now();
		Setup year2032 = FeeTestData.year(jdbc, "RFA2", LocalDate.of(2032, 4, 1), 2);
		long stdId = year2032.standardIds().get(0);
		long nextDivId = divisionOf(year2032.yearId(), stdId);
		long catId = year2032.categoryIds().get(0);

		RegistrationInput input = new RegistrationInput(
				"GR-ADM-1", "Sharma", "Aarav", "Sunil",
				"Male", LocalDate.of(2016, 4, 15), "Pune", "Sunil", "Meena",
				"9811223344", null, "45 MG Road, Pune", null, null,
				catId, today, stdId, "Kindergarten", stdId, nextDivId
		);

		long studentId = registrationService.registerFromAdmission(input, year2032.yearId(), "Google Form");
		assertThat(studentId).isPositive();

		Student student = studentRepo.findById(studentId).orElseThrow();
		assertThat(student.getSource()).isEqualTo("Google Form");
		assertThat(student.getGrNo()).isEqualTo("GR-ADM-1");

		List<StudentEnrollment> enrollments = enrollmentRepo.findByStudentId(studentId);
		assertThat(enrollments).hasSize(1);
		StudentEnrollment enr = enrollments.get(0);
		assertThat(enr.getAcademicYearId()).isEqualTo(year2032.yearId());
		assertThat(enr.getStandardId()).isEqualTo(stdId);
		assertThat(enr.getDivisionId()).isEqualTo(nextDivId);
		assertThat(enr.getYearStatus()).isEqualTo("Studying");

		// Audit rows check: Student and Enrollment created
		Integer studentAuditCount = jdbc.queryForObject(
				"select count(*) from audit_log where area = 'Students' and entity = ?", Integer.class,
				"Student#" + studentId);
		assertThat(studentAuditCount).isEqualTo(1);
	}

	@Test
	@DisplayName("FR-034: GR No already in use is refused with ConflictException")
	void grNoAlreadyInUseRefused() {
		LocalDate today = LocalDate.now();
		Setup year2034 = FeeTestData.year(jdbc, "RFA3", LocalDate.of(2034, 4, 1), 2);
		long stdId = year2034.standardIds().get(0);
		long divId = divisionOf(year2034.yearId(), stdId);
		long catId = year2034.categoryIds().get(0);

		RegistrationInput first = new RegistrationInput(
				"GR-DUP", "Kulkarni", "Aditya", null,
				"Male", LocalDate.of(2015, 8, 20), null, null, null,
				"9822334455", null, "Pune", null, null,
				catId, today, stdId, null, stdId, divId
		);
		registrationService.registerFromAdmission(first, year2034.yearId(), "Google Form");

		RegistrationInput secondWithSameGrNo = new RegistrationInput(
				"GR-DUP", "Deshmukh", "Rohan", null,
				"Male", LocalDate.of(2015, 9, 10), null, null, null,
				"9833445566", null, "Mumbai", null, null,
				catId, today, stdId, null, stdId, divId
		);

		assertThatThrownBy(() -> registrationService.registerFromAdmission(secondWithSameGrNo, year2034.yearId(), "Google Form"))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("already used by a student");
	}

	@Test
	@DisplayName("FR-034: Fee account created when registering into a finalised year")
	void feeAccountCreatedWhenYearFinalised() {
		LocalDate today = LocalDate.now();
		Setup year2036 = FeeTestData.year(jdbc, "RFA4", LocalDate.of(2036, 4, 1), 2);
		long stdId = year2036.standardIds().get(0);
		long divId = divisionOf(year2036.yearId(), stdId);
		long catId = year2036.categoryIds().get(0);

		// Add fee row and finalise fee structure for this year
		FeeTestData.addRow(jdbc, year2036.yearId(), stdId, catId, "10000.00", "2000.00", LocalDate.of(2036, 6, 1));
		jdbc.update("insert into fee_year_status (academic_year_id, status, finalised_at) values (?, 'Finalised', now())", year2036.yearId());

		RegistrationInput input = new RegistrationInput(
				"GR-FEE-1", "Jadhav", "Vivaan", null,
				"Male", LocalDate.of(2015, 3, 5), null, null, null,
				"9844556677", null, "Nagpur", null, null,
				catId, today, stdId, null, stdId, divId
		);

		long studentId = registrationService.registerFromAdmission(input, year2036.yearId(), "Google Form");
		StudentEnrollment enr = enrollmentRepo.findByStudentId(studentId).get(0);

		// Verify fee account exists
		Integer feeAccountCount = jdbc.queryForObject(
				"select count(*) from fee_accounts where enrollment_id = ?", Integer.class, enr.getId());
		assertThat(feeAccountCount).isEqualTo(1);
	}

	private long divisionOf(long yearId, long standardId) {
		return jdbc.queryForObject("select id from divisions where academic_year_id = ? and standard_id = ?",
				Long.class, yearId, standardId);
	}

}
