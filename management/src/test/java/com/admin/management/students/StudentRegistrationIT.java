package com.admin.management.students;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.shared.web.ApiProblem.FieldError;
import com.admin.management.shared.web.ConflictException;
import com.admin.management.shared.web.ValidationException;
import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;

/** Registration: the rules of FR-017, GR No. uniqueness, the serial number, the audit entries and the rollback. */
@Covers("FR-001")
@Covers("FR-002")
@Covers("FR-003")
@Covers("FR-006")
@Covers("FR-012")
@Covers("FR-017")
class StudentRegistrationIT extends IntegrationTest {

	@Autowired
	StudentService service;

	@Autowired
	JdbcTemplate jdbc;


	long yearId;

	long standardId;

	long otherStandardId;

	long divisionId;

	long categoryId;

	@BeforeEach
	void setUp() {
		restoreAuditWrites();

		clock.setDate(LocalDate.of(2026, 10, 25));
		jdbc.update("delete from student_photos");
		jdbc.update("delete from student_enrollments");
		jdbc.update("delete from student_gr_numbers");
		jdbc.update("delete from students");
		jdbc.update("delete from divisions");
		jdbc.update("delete from standards");
		jdbc.update("delete from academic_years");
		jdbc.update("update student_categories set active = true");
		yearId = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values ('2026-27', '2026-04-01', '2027-03-31') returning id",
				Long.class);
		standardId = jdbc.queryForObject("insert into standards (name, display_order) values ('Standard 1', 1) returning id",
				Long.class);
		otherStandardId = jdbc.queryForObject(
				"insert into standards (name, display_order) values ('Standard 2', 2) returning id", Long.class);
		divisionId = jdbc.queryForObject(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A') returning id", Long.class,
				yearId, standardId);
		categoryId = jdbc.queryForObject("select min(id) from student_categories", Long.class);
	}

	private StudentInput input(String grNo) {
		return new StudentInput(null, grNo, "Testsurname", "Alpha", "Middle", "Male", LocalDate.of(2012, 1, 1), "Test Town",
				"Test father", "Test mother", "9000000001", "9000000002", "Test address 1", "1234 5678 9012",
				"Test religion 77", categoryId, LocalDate.of(2020, 6, 1), standardId, "Test school", standardId,
				divisionId);
	}

	private StudentInput with(UnaryOperator<StudentInput> change, String grNo) {
		return change.apply(input(grNo));
	}

	private List<FieldError> errorsOf(StudentInput in) {
		return ((ValidationException) catchThrowable(in)).getFieldErrors();
	}

	private Throwable catchThrowable(StudentInput in) {
		return org.assertj.core.api.Assertions.catchThrowable(() -> service.register(in));
	}

	private void assertRefused(StudentInput in, String field) {
		Throwable thrown = catchThrowable(in);
		assertThat(thrown).isInstanceOf(ValidationException.class);
		assertThat(((ValidationException) thrown).getFieldErrors()).extracting(FieldError::field).contains(field);
		assertThat(count("students")).isZero();
	}

	/** Makes every insert into the audit log fail, as if the entry could not be saved. */
	private void breakAuditWrites() {
		jdbc.execute("create or replace function fail_audit_insert() returns trigger as $$ begin raise exception 'audit write failed'; end; $$ language plpgsql");
		jdbc.execute("create trigger trg_fail_audit_insert before insert on audit_log for each row execute function fail_audit_insert()");
	}

	private void restoreAuditWrites() {
		jdbc.execute("drop trigger if exists trg_fail_audit_insert on audit_log");
	}

	private int count(String table) {
		return jdbc.queryForObject("select count(*) from " + table, Integer.class);
	}

	@Test
	void registersTheStudentTheGrKeyTheClassRecordAndTwoAuditEntriesInOneTransaction() {
		StudentDto saved = service.register(input("  Gr-1 "));

		assertThat(saved.id()).isPositive();
		assertThat(saved.grNo()).isEqualTo("Gr-1");
		assertThat(saved.status()).isEqualTo("Active");
		assertThat(saved.source()).isEqualTo("Manual");
		assertThat(saved.srNo()).isPositive();
		assertThat(saved.mobile1()).isEqualTo("9000000001");
		assertThat(saved.aadhaar()).isEqualTo("********9012");
		assertThat(saved.religionCaste()).isEqualTo("************n 77");
		assertThat(saved.admissionStandardName()).isEqualTo("Standard 1");
		assertThat(saved.classHistory()).hasSize(1);
		EnrollmentDto record = saved.classHistory().get(0);
		assertThat(record.academicYearName()).isEqualTo("2026-27");
		assertThat(record.stdDiv()).isEqualTo("Standard 1-A");
		assertThat(record.yearStatus()).isEqualTo("Studying");
		assertThat(jdbc.queryForObject("select aadhaar from students where id = ?", String.class, saved.id()))
			.isEqualTo("123456789012");
		assertThat(jdbc.queryForObject("select year_start_date::text from student_enrollments", String.class))
			.isEqualTo("2026-04-01");
		assertThat(jdbc.queryForObject("select count(*) from student_gr_numbers where gr_key = 'gr-1'", Integer.class))
			.isEqualTo(1);
		long enrollmentId = jdbc.queryForObject("select id from student_enrollments", Long.class);
		List<Map<String, Object>> audit = jdbc.queryForList(
				"select area, action, entity, changes::text c from audit_log where entity in (?, ?) order by id",
				"Student#" + saved.id(), "StudentEnrollment#" + enrollmentId);
		assertThat(audit).hasSize(2);
		assertThat(audit.get(0)).containsEntry("action", "Create").containsEntry("entity", "Student#" + saved.id());
		assertThat(audit.get(1)).containsEntry("entity", "StudentEnrollment#" + enrollmentId);
		assertThat(audit.toString()).doesNotContain("123456789012").doesNotContain("Test religion 77");
		assertThat(audit.get(0).get("c").toString()).contains("********9012");
	}

	@Test
	void requiredFieldsAreRefusedBesideTheirFields() {
		assertRefused(with(i -> new StudentInput(null, " ", i.surname(), i.firstName(), null, i.gender(), i.dob(), null,
				null, null, i.mobile1(), null, i.address(), null, null, categoryId, i.admissionDate(), standardId, null,
				standardId, divisionId), "x"), "grNo");
		assertRefused(with(i -> new StudentInput(null, "R1", "", i.firstName(), null, i.gender(), i.dob(), null, null,
				null, i.mobile1(), null, i.address(), null, null, categoryId, i.admissionDate(), standardId, null,
				standardId, divisionId), "x"), "surname");
		assertRefused(with(i -> new StudentInput(null, "R2", i.surname(), null, null, i.gender(), i.dob(), null, null,
				null, i.mobile1(), null, i.address(), null, null, categoryId, i.admissionDate(), standardId, null,
				standardId, divisionId), "x"), "firstName");
		assertRefused(with(i -> new StudentInput(null, "R3", i.surname(), i.firstName(), null, null, i.dob(), null, null,
				null, i.mobile1(), null, i.address(), null, null, categoryId, i.admissionDate(), standardId, null,
				standardId, divisionId), "x"), "gender");
		assertRefused(with(i -> new StudentInput(null, "R4", i.surname(), i.firstName(), null, i.gender(), null, null,
				null, null, i.mobile1(), null, i.address(), null, null, categoryId, i.admissionDate(), standardId, null,
				standardId, divisionId), "x"), "dob");
		assertRefused(with(i -> new StudentInput(null, "R5", i.surname(), i.firstName(), null, i.gender(), i.dob(), null,
				null, null, null, null, i.address(), null, null, categoryId, i.admissionDate(), standardId, null,
				standardId, divisionId), "x"), "mobile1");
		assertRefused(with(i -> new StudentInput(null, "R6", i.surname(), i.firstName(), null, i.gender(), i.dob(), null,
				null, null, i.mobile1(), null, "  ", null, null, categoryId, i.admissionDate(), standardId, null,
				standardId, divisionId), "x"), "address");
		assertRefused(with(i -> new StudentInput(null, "R7", i.surname(), i.firstName(), null, i.gender(), i.dob(), null,
				null, null, i.mobile1(), null, i.address(), null, null, categoryId, null, standardId, null, standardId,
				divisionId), "x"), "admissionDate");
		assertRefused(with(i -> new StudentInput(null, "R8", i.surname(), i.firstName(), null, i.gender(), i.dob(), null,
				null, null, i.mobile1(), null, i.address(), null, null, categoryId, i.admissionDate(), null, null,
				standardId, divisionId), "x"), "admissionStandardId");
		assertRefused(with(i -> new StudentInput(null, "R9", i.surname(), i.firstName(), null, i.gender(), i.dob(), null,
				null, null, i.mobile1(), null, i.address(), null, null, null, i.admissionDate(), standardId, null,
				standardId, divisionId), "x"), "categoryId");
		assertRefused(with(i -> new StudentInput(null, "R10", i.surname(), i.firstName(), null, i.gender(), i.dob(), null,
				null, null, i.mobile1(), null, i.address(), null, null, categoryId, i.admissionDate(), standardId, null,
				null, null), "x"), "standardId");
	}

	@Test
	void everyProblemIsReportedTogether() {
		StudentInput bad = new StudentInput(null, "", "", "", null, "Robot", null, null, null, null, "123", "abc", "", "12",
				null, null, null, null, null, null, null);

		assertThat(errorsOf(bad)).extracting(FieldError::field).contains("grNo", "surname", "firstName", "gender", "dob",
				"mobile1", "mobile2", "address", "aadhaar", "admissionDate", "admissionStandardId", "standardId",
				"divisionId", "categoryId");
	}

	@Test
	void mobileNumbersNeedTenDigitsAfterAnOptionalPlus91OrLeadingZero() {
		assertRefused(with(i -> mobile(i, "123456789"), "M1"), "mobile1");
		assertRefused(with(i -> mobile(i, "12345678901"), "M2"), "mobile1");
		assertRefused(with(i -> mobile(i, "98765abc10"), "M3"), "mobile1");

		assertThat(service.register(with(i -> mobile(i, "+91 98765 43210"), "M4")).mobile1()).isEqualTo("9876543210");
		assertThat(service.register(with(i -> mobile(i, "09876543211"), "M5")).mobile1()).isEqualTo("9876543211");
		assertThat(service.register(with(i -> mobile(i, "98765-43212"), "M6")).mobile1()).isEqualTo("9876543212");
	}

	private static StudentInput mobile(StudentInput i, String value) {
		return new StudentInput(null, i.grNo(), i.surname(), i.firstName(), i.middleName(), i.gender(), i.dob(),
				i.birthPlace(), i.fatherName(), i.motherName(), value, i.mobile2(), i.address(), i.aadhaar(),
				i.religionCaste(), i.categoryId(), i.admissionDate(), i.admissionStandardId(), i.previousSchool(),
				i.standardId(), i.divisionId());
	}

	@Test
	void mobile2IsOptionalButCheckedWhenGiven() {
		StudentInput badSecond = new StudentInput(null, "S1", "Testsurname", "Alpha", null, "Male", LocalDate.of(2012, 1, 1),
				null, null, null, "9000000001", "12345", "Test address", null, null, categoryId, LocalDate.of(2020, 6, 1),
				standardId, null, standardId, divisionId);
		assertRefused(badSecond, "mobile2");

		StudentInput none = new StudentInput(null, "S2", "Testsurname", "Alpha", null, "Male", LocalDate.of(2012, 1, 1), null,
				null, null, "9000000001", null, "Test address", null, null, categoryId, LocalDate.of(2020, 6, 1),
				standardId, null, standardId, divisionId);
		StudentDto saved = service.register(none);
		assertThat(saved.mobile2()).isNull();
		assertThat(saved.aadhaar()).isNull();
	}

	@Test
	void aadhaarNeedsTwelveDigitsSpacesIgnoredAndTheMessageNeverRepeatsIt() {
		StudentInput bad = with(i -> aadhaar(i, "1234 5678 901"), "A1");
		assertRefused(bad, "aadhaar");
		assertThat(errorsOf(bad).toString()).doesNotContain("1234").doesNotContain("901");
		assertRefused(with(i -> aadhaar(i, "12345678901a"), "A2"), "aadhaar");

		service.register(with(i -> aadhaar(i, " 1234 5678 9012 "), "A3"));
		assertThat(jdbc.queryForObject("select aadhaar from students where gr_no = 'A3'", String.class))
			.isEqualTo("123456789012");
	}

	private static StudentInput aadhaar(StudentInput i, String value) {
		return new StudentInput(null, i.grNo(), i.surname(), i.firstName(), i.middleName(), i.gender(), i.dob(),
				i.birthPlace(), i.fatherName(), i.motherName(), i.mobile1(), i.mobile2(), i.address(), value,
				i.religionCaste(), i.categoryId(), i.admissionDate(), i.admissionStandardId(), i.previousSchool(),
				i.standardId(), i.divisionId());
	}

	@Test
	void admissionDateCannotBeAfterTodayOrBeforeTheDateOfBirth() {
		assertRefused(with(i -> admission(i, LocalDate.of(2026, 10, 26)), "D1"), "admissionDate");
		assertRefused(with(i -> admission(i, LocalDate.of(2011, 12, 31)), "D2"), "admissionDate");

		service.register(with(i -> admission(i, LocalDate.of(2026, 10, 25)), "D3")); // today
		service.register(with(i -> admission(i, LocalDate.of(2012, 1, 1)), "D4")); // the date of birth
		assertThat(count("students")).isEqualTo(2);
	}

	private static StudentInput admission(StudentInput i, LocalDate date) {
		return new StudentInput(null, i.grNo(), i.surname(), i.firstName(), i.middleName(), i.gender(), i.dob(),
				i.birthPlace(), i.fatherName(), i.motherName(), i.mobile1(), i.mobile2(), i.address(), i.aadhaar(),
				i.religionCaste(), i.categoryId(), date, i.admissionStandardId(), i.previousSchool(), i.standardId(),
				i.divisionId());
	}

	@Test
	void todayIsTheSchoolDateNotTheServerDate() {
		clock.set(java.time.Instant.parse("2026-10-25T19:00:00Z")); // 00:30 on the 26th in India
		assertRefused(with(i -> admission(i, LocalDate.of(2026, 10, 27)), "T1"), "admissionDate");
		service.register(with(i -> admission(i, LocalDate.of(2026, 10, 26)), "T2"));
	}

	@Test
	void theDivisionMustBelongToTheChosenStandardInTheCurrentYear() {
		long otherYear = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values ('2027-28', '2027-04-01', '2028-03-31') returning id",
				Long.class);
		long divisionOfOtherYear = jdbc.queryForObject(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A') returning id", Long.class,
				otherYear, standardId);
		long divisionOfOtherStandard = jdbc.queryForObject(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A') returning id", Long.class,
				yearId, otherStandardId);

		assertRefused(with(i -> division(i, divisionOfOtherYear), "V1"), "divisionId");
		assertRefused(with(i -> division(i, divisionOfOtherStandard), "V2"), "divisionId");
		service.register(with(i -> division(i, divisionOfOtherStandard, otherStandardId), "V3"));
		assertThat(count("students")).isEqualTo(1);
	}

	private static StudentInput division(StudentInput i, long division) {
		return division(i, division, i.standardId());
	}

	private static StudentInput division(StudentInput i, long division, long standard) {
		return new StudentInput(null, i.grNo(), i.surname(), i.firstName(), i.middleName(), i.gender(), i.dob(),
				i.birthPlace(), i.fatherName(), i.motherName(), i.mobile1(), i.mobile2(), i.address(), i.aadhaar(),
				i.religionCaste(), i.categoryId(), i.admissionDate(), i.admissionStandardId(), i.previousSchool(),
				standard, division);
	}

	@Test
	void inactiveStandardDivisionOrCategoryIsRefusedForANewStudent() {
		jdbc.update("update divisions set active = false where id = ?", divisionId);
		assertRefused(input("I1"), "divisionId");
		jdbc.update("update divisions set active = true where id = ?", divisionId);
		jdbc.update("update student_categories set active = false where id = ?", categoryId);
		assertRefused(input("I2"), "categoryId");
		jdbc.update("update student_categories set active = true where id = ?", categoryId);
		jdbc.update("update standards set active = false where id = ?", standardId);
		assertRefused(input("I3"), "standardId");
	}

	@Test
	void noCurrentYearRefusesTheRegistration() {
		clock.setDate(LocalDate.of(2030, 1, 1));

		Throwable thrown = catchThrowable(input("N1"));

		assertThat(thrown).isInstanceOf(ValidationException.class);
		assertThat(count("students")).isZero();
	}

	@Test
	void aGrNoThatIsUsedOrEverUsedIsRefusedWhateverTheCapitalsAndSpaces() {
		service.register(input("Gr-100"));

		for (String again : List.of("Gr-100", "gr-100", "GR-100", "  gr-100  ")) {
			assertThatThrownBy(() -> service.register(input(again))).isInstanceOf(ConflictException.class)
				.satisfies(e -> assertThat(((ConflictException) e).getField()).isEqualTo("grNo"));
		}
		assertThat(count("students")).isEqualTo(1);

		jdbc.update("update students set status = 'Left'");
		assertThatThrownBy(() -> service.register(input("GR-100"))).isInstanceOf(ConflictException.class);

		// a number that was replaced by a correction stays reserved
		jdbc.update("update students set gr_no = 'Gr-200'");
		assertThatThrownBy(() -> service.register(input("gr-100"))).isInstanceOf(ConflictException.class);
	}

	@Test
	void serialNumbersAreNeverRepeatedAfterARolledBackSave() {
		long first = service.register(input("SR-1")).srNo();
		breakAuditWrites();
		assertThatThrownBy(() -> service.register(input("SR-2"))).isInstanceOf(RuntimeException.class);
		restoreAuditWrites();

		long second = service.register(input("SR-2")).srNo();

		assertThat(second).isGreaterThan(first + 1); // the rolled-back save used a number that is not handed out again
		assertThat(jdbc.queryForObject("select count(distinct sr_no) from students", Integer.class)).isEqualTo(2);
	}

	@Test
	void whenTheAuditWriteFailsNothingIsSaved() {
		breakAuditWrites();

		assertThatThrownBy(() -> service.register(input("RB-1"))).isInstanceOf(RuntimeException.class);
		restoreAuditWrites();

		assertThat(count("students")).isZero();
		assertThat(count("student_gr_numbers")).isZero();
		assertThat(count("student_enrollments")).isZero();
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity like 'Student%' and changes::text like '%RB-1%'",
				Integer.class)).isZero();
		// and the GR No. is free again
		service.register(input("RB-1"));
	}

	@Test
	void nameMatchesIgnoreCapitalsAndOuterSpacesAndIncludeLeftStudents() {
		StudentDto active = service.register(input("NM-1"));
		StudentDto left = service.register(with(i -> mobile(i, "9000000003"), "NM-2"));
		jdbc.update("update students set status = 'Left' where id = ?", left.id());
		service.register(with(i -> name(i, "Othersurname", "Alpha", "Middle"), "NM-3"));
		service.register(with(i -> name(i, "Testsurname", "Alpha", null), "NM-4"));

		List<NameMatch> matches = service.nameMatches("  TESTSURNAME ", "alpha", " middle", null);

		assertThat(matches).extracting(NameMatch::grNo).containsExactly("NM-1", "NM-2");
		assertThat(matches).extracting(NameMatch::status).containsExactly("Active", "Left");
		assertThat(matches.get(0).name()).isEqualTo("Testsurname Alpha Middle");
		assertThat(service.nameMatches("Testsurname", "Alpha", "Middle", active.id())).extracting(NameMatch::grNo)
			.containsExactly("NM-2");
		assertThat(service.nameMatches("Testsurname", "Alpha", null, null)).extracting(NameMatch::grNo)
			.containsExactly("NM-4");
		assertThat(service.nameMatches("Nobody", "Here", null, null)).isEmpty();
	}

	private static StudentInput name(StudentInput i, String surname, String first, String middle) {
		return new StudentInput(null, i.grNo(), surname, first, middle, i.gender(), i.dob(), i.birthPlace(),
				i.fatherName(), i.motherName(), i.mobile1(), i.mobile2(), i.address(), i.aadhaar(), i.religionCaste(),
				i.categoryId(), i.admissionDate(), i.admissionStandardId(), i.previousSchool(), i.standardId(),
				i.divisionId());
	}

}
