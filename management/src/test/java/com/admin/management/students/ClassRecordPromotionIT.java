package com.admin.management.students;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.shared.web.ValidationException;
import com.admin.management.students.api.ClassRecordPromotion;
import com.admin.management.students.api.ClassRecordPromotion.CandidateStudent;
import com.admin.management.students.api.ClassRecordPromotion.PendingStandard;
import com.admin.management.students.api.ClassRecordPromotion.PromotionItemOutcome;
import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;

/**
 * Integration tests for class record promotion writes in students
 * (FR-008, FR-010, FR-011, FR-016, FR-018, FR-019).
 */
@Covers("FR-008")
@Covers("FR-010")
@Covers("FR-011")
@Covers("FR-016")
@Covers("FR-018")
@Covers("FR-019")
class ClassRecordPromotionIT extends IntegrationTest {

	@Autowired
	ClassRecordPromotionService promotionService;

	@Autowired
	JdbcTemplate jdbc;

	private Setup setup(String prefix, int yearNumber) {
		return FeeTestData.year(jdbc, prefix, LocalDate.of(yearNumber, 4, 1), 3);
	}

	private long createNextYear(Setup year1, int yearNumber) {
		LocalDate start = LocalDate.of(yearNumber, 4, 1);
		long yearId = jdbc.queryForObject(
				"insert into academic_years (name, start_date, end_date) values (?, ?, ?) returning id", Long.class,
				"CRP " + yearNumber + " " + start.getYear() + "-" + (start.getYear() + 1), java.sql.Date.valueOf(start),
				java.sql.Date.valueOf(start.plusYears(1).minusDays(1)));
		for (Long standardId : year1.standardIds()) {
			jdbc.update("insert into divisions (academic_year_id, standard_id, name) values (?, ?, 'A')",
					yearId, standardId);
		}
		return yearId;
	}

	private long divisionOf(long yearId, long standardId) {
		return jdbc.queryForObject("select id from divisions where academic_year_id = ? and standard_id = ?",
				Long.class, yearId, standardId);
	}

	@Test
	void promotedOutcomeCreatesNewRecordAndSetsOldStatusPromoted() {
		Setup year1 = setup("CRP_Prom", 3701);
		long year2Id = createNextYear(year1, 3702);

		long enrollmentId = FeeTestData.student(jdbc, year1, 370101, 0, 0);
		long studentId = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class,
				enrollmentId);
		long version = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class,
				enrollmentId);

		PromotionItemOutcome outcome = new PromotionItemOutcome(
				enrollmentId,
				year2Id,
				"PROMOTED",
				year1.standard(1), // next standard
				divisionOf(year2Id, year1.standard(1)),
				year1.category(0),
				null,
				version);

		Long newEnrollmentId = promotionService.recordOutcome(outcome);
		assertThat(newEnrollmentId).isNotNull();

		String oldStatus = jdbc.queryForObject("select year_status from student_enrollments where id = ?", String.class,
				enrollmentId);
		assertThat(oldStatus).isEqualTo("Promoted");

		String newStatus = jdbc.queryForObject("select year_status from student_enrollments where id = ?", String.class,
				newEnrollmentId);
		assertThat(newStatus).isEqualTo("Studying");

		Long newStandardId = jdbc.queryForObject("select standard_id from student_enrollments where id = ?", Long.class,
				newEnrollmentId);
		assertThat(newStandardId).isEqualTo(year1.standard(1));

		String studentStatus = jdbc.queryForObject("select status from students where id = ?", String.class, studentId);
		assertThat(studentStatus).isEqualTo("Active");
	}

	@Test
	void detainedOutcomeCreatesNewRecordAndSetsOldStatusDetained() {
		Setup year1 = setup("CRP_Det", 3703);
		long year2Id = createNextYear(year1, 3704);

		long enrollmentId = FeeTestData.student(jdbc, year1, 370301, 0, 0);
		long studentId = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class,
				enrollmentId);
		long version = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class,
				enrollmentId);

		PromotionItemOutcome outcome = new PromotionItemOutcome(
				enrollmentId,
				year2Id,
				"DETAINED",
				year1.standard(0), // same standard
				divisionOf(year2Id, year1.standard(0)),
				year1.category(0),
				null,
				version);

		Long newEnrollmentId = promotionService.recordOutcome(outcome);
		assertThat(newEnrollmentId).isNotNull();

		String oldStatus = jdbc.queryForObject("select year_status from student_enrollments where id = ?", String.class,
				enrollmentId);
		assertThat(oldStatus).isEqualTo("Detained");

		String newStatus = jdbc.queryForObject("select year_status from student_enrollments where id = ?", String.class,
				newEnrollmentId);
		assertThat(newStatus).isEqualTo("Studying");

		Long newStandardId = jdbc.queryForObject("select standard_id from student_enrollments where id = ?", Long.class,
				newEnrollmentId);
		assertThat(newStandardId).isEqualTo(year1.standard(0));
	}

	@Test
	void leftOutcomeSetsStudentLeftAndNoNewRecord() {
		Setup year1 = setup("CRP_Left", 3705);
		long year2Id = createNextYear(year1, 3706);

		long enrollmentId = FeeTestData.student(jdbc, year1, 370501, 0, 0);
		long studentId = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class,
				enrollmentId);
		long version = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class,
				enrollmentId);

		PromotionItemOutcome outcome = new PromotionItemOutcome(
				enrollmentId,
				year2Id,
				"LEFT",
				null,
				null,
				null,
				null,
				version);

		Long newEnrollmentId = promotionService.recordOutcome(outcome);
		assertThat(newEnrollmentId).isNull();

		String oldStatus = jdbc.queryForObject("select year_status from student_enrollments where id = ?", String.class,
				enrollmentId);
		assertThat(oldStatus).isEqualTo("Left");

		String studentStatus = jdbc.queryForObject("select status from students where id = ?", String.class, studentId);
		assertThat(studentStatus).isEqualTo("Left");

		Integer targetRecordCount = jdbc.queryForObject(
				"select count(*) from student_enrollments where student_id = ? and academic_year_id = ?",
				Integer.class, studentId, year2Id);
		assertThat(targetRecordCount).isZero();
	}

	@Test
	void feeAccountFollowsChangedCategory() {
		Setup year1 = setup("CRP_Fee", 3707);
		long year2Id = createNextYear(year1, 3708);

		long targetStandard = year1.standard(1);
		long cat1 = year1.category(0);
		long cat2 = year1.category(1);

		// Fee rows in year2 for targetStandard: cat1 -> 10000, cat2 -> 15000
		FeeTestData.addRow(jdbc, year2Id, targetStandard, cat1, "10000.00", null, null);
		FeeTestData.addRow(jdbc, year2Id, targetStandard, cat2, "15000.00", null, null);
		FeeTestData.setStatus(jdbc, year2Id, true); // Finalised

		// Student in year1 with cat1
		long enrollmentId = FeeTestData.student(jdbc, year1, 370701, 0, 0);
		long version = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class,
				enrollmentId);

		// Promote with changed category to cat2
		PromotionItemOutcome outcome = new PromotionItemOutcome(
				enrollmentId,
				year2Id,
				"PROMOTED",
				targetStandard,
				divisionOf(year2Id, targetStandard),
				cat2,
				null,
				version);

		Long newEnrollmentId = promotionService.recordOutcome(outcome);
		assertThat(newEnrollmentId).isNotNull();

		BigDecimal accountAmount = jdbc.queryForObject(
				"select amount from fee_accounts where enrollment_id = ?",
				BigDecimal.class, newEnrollmentId);
		assertThat(accountAmount).isEqualByComparingTo("15000.00");
	}

	@Test
	void refuseWhenOldRecordNotStudying() {
		Setup year1 = setup("CRP_NotStd", 3709);
		long year2Id = createNextYear(year1, 3710);

		long enrollmentId = FeeTestData.student(jdbc, year1, 370901, 0, 0);
		jdbc.update("update student_enrollments set year_status = 'Promoted' where id = ?", enrollmentId);
		long version = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class,
				enrollmentId);

		PromotionItemOutcome outcome = new PromotionItemOutcome(
				enrollmentId,
				year2Id,
				"PROMOTED",
				year1.standard(1),
				divisionOf(year2Id, year1.standard(1)),
				year1.category(0),
				null,
				version);

		assertThatThrownBy(() -> promotionService.recordOutcome(outcome))
				.isInstanceOf(ValidationException.class)
				.satisfies(ex -> assertThat(((ValidationException) ex).getCode()).isEqualTo("ALREADY_PROCESSED"));
	}

	@Test
	void refuseWhenStudentRecordAlreadyExistsInTargetYear() {
		Setup year1 = setup("CRP_Dup", 3711);
		long year2Id = createNextYear(year1, 3712);

		long enrollmentId = FeeTestData.student(jdbc, year1, 371101, 0, 0);
		long studentId = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class,
				enrollmentId);
		long version = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class,
				enrollmentId);

		// Pre-create enrollment in target year
		jdbc.update("insert into student_enrollments (student_id, academic_year_id, standard_id, division_id, category_id, year_start_date, year_status) values (?, ?, ?, ?, ?, now(), 'Studying')",
				studentId, year2Id, year1.standard(0), divisionOf(year2Id, year1.standard(0)), year1.category(0));

		PromotionItemOutcome outcome = new PromotionItemOutcome(
				enrollmentId,
				year2Id,
				"PROMOTED",
				year1.standard(1),
				divisionOf(year2Id, year1.standard(1)),
				year1.category(0),
				null,
				version);

		assertThatThrownBy(() -> promotionService.recordOutcome(outcome))
				.isInstanceOf(ValidationException.class)
				.satisfies(ex -> assertThat(((ValidationException) ex).getCode()).isEqualTo("ALREADY_PROCESSED"));
	}

	@Test
	void auditContentVerifiedForPromotionAndLeft() {
		Setup year1 = setup("CRP_Aud", 3713);
		long year2Id = createNextYear(year1, 3714);

		// 1. Promote student
		long enr1 = FeeTestData.student(jdbc, year1, 371301, 0, 0);
		long ver1 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, enr1);
		promotionService.recordOutcome(new PromotionItemOutcome(
				enr1, year2Id, "PROMOTED", year1.standard(1),
				divisionOf(year2Id, year1.standard(1)), year1.category(0), null, ver1));

		String changes1 = jdbc.queryForObject(
				"select changes::text from audit_log where area = 'Students' and entity = ?",
				String.class, "StudentEnrollment#" + enr1);
		assertThat(changes1).contains("\"yearStatus\": \"Studying\"");
		assertThat(changes1).contains("\"yearStatus\": \"Promoted\"");
		assertThat(changes1).contains("\"standardId\": " + year1.standard(1));

		// 2. Left student
		long enr2 = FeeTestData.student(jdbc, year1, 371302, 0, 0);
		long stu2 = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class, enr2);
		long ver2 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, enr2);
		promotionService.recordOutcome(new PromotionItemOutcome(
				enr2, year2Id, "LEFT", null, null, null, null, ver2));

		String changesStu = jdbc.queryForObject(
				"select changes::text from audit_log where area = 'Students' and entity = ?",
				String.class, "Student#" + stu2);
		assertThat(changesStu).contains("\"status\": \"Active\"");
		assertThat(changesStu).contains("\"status\": \"Left\"");

		String changesEnr = jdbc.queryForObject(
				"select changes::text from audit_log where area = 'Students' and entity = ?",
				String.class, "StudentEnrollment#" + enr2);
		assertThat(changesEnr).contains("\"yearStatus\": \"Left\"");
	}

	@Test
	void studentsToPromoteAndPendingByStandard() {
		Setup year1 = setup("CRP_List", 3715);
		long year2Id = createNextYear(year1, 3716);

		long enr1 = FeeTestData.student(jdbc, year1, 371501, 0, 0);
		long enr2 = FeeTestData.student(jdbc, year1, 371502, 0, 0);

		// Add pending fee for enr1 to ensure pending fee does NOT block (FR-019)
		jdbc.update("insert into fee_accounts (enrollment_id, academic_year_id, amount, updated_at) values (?, ?, 10000.00, now())",
				enr1, year1.yearId());

		// Candidate students in standard 0
		List<CandidateStudent> candidates = promotionService.studentsToPromote(year1.yearId(), year2Id, year1.standard(0));
		assertThat(candidates).hasSize(2);
		assertThat(candidates).extracting(CandidateStudent::enrollmentId).containsExactlyInAnyOrder(enr1, enr2);

		// Pending standards
		List<PendingStandard> pending = promotionService.pendingByStandard(year1.yearId(), year2Id);
		assertThat(pending).hasSize(1);
		assertThat(pending.get(0).standardId()).isEqualTo(year1.standard(0));
		assertThat(pending.get(0).pending()).isEqualTo(2);

		// After promoting enr1, pending count drops to 1
		long ver1 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, enr1);
		promotionService.recordOutcome(new PromotionItemOutcome(
				enr1, year2Id, "PROMOTED", year1.standard(1),
				divisionOf(year2Id, year1.standard(1)), year1.category(0), null, ver1));

		List<PendingStandard> pendingAfter = promotionService.pendingByStandard(year1.yearId(), year2Id);
		assertThat(pendingAfter.get(0).pending()).isEqualTo(1);
	}

}
