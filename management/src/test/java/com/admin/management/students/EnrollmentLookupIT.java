package com.admin.management.students;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.students.api.EnrollmentLookup;
import com.admin.management.students.api.EnrollmentLookup.ClassRecordRef;
import com.admin.management.students.api.StudentLookup;
import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.StudentTestData;
import com.admin.management.support.StudentTestData.YearSetup;

/** What the fees module may ask the students module (research R2): class records of a year and a student's shown record. */
@Covers("FR-006")
@Covers("FR-014")
class EnrollmentLookupIT extends IntegrationTest {

	@Autowired
	EnrollmentLookup enrollmentLookup;

	@Autowired
	StudentLookup studentLookup;

	@Autowired
	JdbcTemplate jdbc;

	long currentYear;

	long earlierYear;

	long earliestYear;

	long standard;

	long otherStandard;

	long category;

	long otherCategory;

	long both;

	long onlyEarlier;

	long twoEarlier;

	long none;

	long left;

	@BeforeAll
	void seed() {
		LocalDate today = LocalDate.now(clock);
		LocalDate start = today.minusMonths(2);
		LocalDate earlierStart = start.minusYears(1);
		LocalDate earliestStart = start.minusYears(2);
		currentYear = year("current", start, start.plusYears(1).minusDays(1));
		earlierYear = year("earlier", earlierStart, start.minusDays(1));
		earliestYear = year("earliest", earliestStart, earlierStart.minusDays(1));
		standard = jdbc.queryForObject("insert into standards (name, display_order) values ('Lookup Std 10', 10) returning id",
				Long.class);
		otherStandard = jdbc.queryForObject(
				"insert into standards (name, display_order) values ('Lookup Std 9', 9) returning id", Long.class);
		category = jdbc.queryForObject("select min(id) from student_categories", Long.class);
		otherCategory = jdbc.queryForObject("select max(id) from student_categories", Long.class);
		YearSetup current = setup(currentYear, start, standard);
		YearSetup earlier = setup(earlierYear, earlierStart, otherStandard);
		YearSetup earliest = setup(earliestYear, earliestStart, otherStandard);

		both = student(1);
		StudentTestData.insertEnrollment(jdbc, both, earlier, category);
		StudentTestData.insertEnrollment(jdbc, both, current, otherCategory);
		onlyEarlier = student(2);
		StudentTestData.insertEnrollment(jdbc, onlyEarlier, earlier, category);
		twoEarlier = student(3);
		StudentTestData.insertEnrollment(jdbc, twoEarlier, earliest, category);
		StudentTestData.insertEnrollment(jdbc, twoEarlier, earlier, otherCategory);
		none = student(4);
		left = student(5);
		StudentTestData.insertEnrollment(jdbc, left, current, category);
		jdbc.update("update students set status = 'Left' where id = ?", left);
		jdbc.update("update student_enrollments set year_status = 'Left' where student_id = ?", left);
	}

	@Test
	void classRecordsOfAYearListEveryRecordIncludingStudentsWhoLeft() {
		List<ClassRecordRef> records = enrollmentLookup.classRecordsOfYear(currentYear);

		assertThat(records).extracting(ClassRecordRef::studentId).containsExactlyInAnyOrder(both, left);
		ClassRecordRef bothRecord = records.stream().filter(r -> r.studentId() == both).findFirst().orElseThrow();
		assertThat(bothRecord.yearId()).isEqualTo(currentYear);
		assertThat(bothRecord.standardId()).isEqualTo(standard);
		assertThat(bothRecord.categoryId()).isEqualTo(otherCategory);
		assertThat(bothRecord.enrollmentId()).isEqualTo(jdbc.queryForObject(
				"select id from student_enrollments where student_id = ? and academic_year_id = ?", Long.class, both,
				currentYear));
	}

	@Test
	void theCountIsTheNumberOfClassRecordsOfTheYear() {
		assertThat(enrollmentLookup.countOfYear(currentYear)).isEqualTo(2);
		assertThat(enrollmentLookup.countOfYear(earlierYear)).isEqualTo(3);
		assertThat(enrollmentLookup.countOfYear(-1L)).isZero();
		assertThat(enrollmentLookup.classRecordsOfYear(-1L)).isEmpty();
	}

	@Test
	void theShownRecordIsTheCurrentYearsElseTheMostRecent() {
		assertThat(enrollmentLookup.shownRecord(both)).get().extracting(ClassRecordRef::yearId).isEqualTo(currentYear);
		assertThat(enrollmentLookup.shownRecord(onlyEarlier)).get().extracting(ClassRecordRef::yearId)
			.isEqualTo(earlierYear);
		ClassRecordRef recent = enrollmentLookup.shownRecord(twoEarlier).orElseThrow();
		assertThat(recent.yearId()).isEqualTo(earlierYear);
		assertThat(recent.categoryId()).isEqualTo(otherCategory);
		assertThat(enrollmentLookup.shownRecord(none)).isEmpty();
		assertThat(enrollmentLookup.shownRecord(-1L)).isEmpty();
	}

	@Test
	void aRecordIsFoundByStudentAndYear() {
		assertThat(enrollmentLookup.byStudentAndYear(both, earlierYear)).get().extracting(ClassRecordRef::standardId)
			.isEqualTo(otherStandard);
		assertThat(enrollmentLookup.byStudentAndYear(both, earliestYear)).isEmpty();
		assertThat(enrollmentLookup.byStudentAndYear(none, currentYear)).isEmpty();
	}

	@Test
	void aStudentExistsOrNot() {
		assertThat(studentLookup.exists(none)).isTrue();
		assertThat(studentLookup.exists(left)).isTrue();
		assertThat(studentLookup.exists(-1L)).isFalse();
		assertThat(studentLookup.exists(null)).isFalse();
	}

	private long year(String name, LocalDate from, LocalDate to) {
		return jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values (?, ?, ?) returning id", Long.class, name,
				java.sql.Date.valueOf(from), java.sql.Date.valueOf(to));
	}

	private YearSetup setup(long yearId, LocalDate yearStart, long standardId) {
		long division = jdbc.queryForObject(
				"insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A') returning id", Long.class,
				yearId, standardId);
		return new YearSetup(yearId, yearStart, standardId, division);
	}

	private long student(int n) {
		return StudentTestData.insertStudent(jdbc, StudentTestData.valid(n, category, standard));
	}

}
