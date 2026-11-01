package com.admin.management.yearchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.shared.web.CodedConflictException;
import com.admin.management.shared.web.ValidationException;
import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.YearChangeTestData;
import com.admin.management.support.YearChangeTestData.TwoYears;

/**
 * Integration tests for PromotionService and PromotionController endpoints (FR-006 to FR-012, FR-016 to FR-024, FR-026).
 */
@Covers("FR-006")
@Covers("FR-007")
@Covers("FR-008")
@Covers("FR-009")
@Covers("FR-010")
@Covers("FR-011")
@Covers("FR-012")
@Covers("FR-016")
@Covers("FR-017")
@Covers("FR-018")
@Covers("FR-019")
@Covers("FR-020")
@Covers("FR-021")
@Covers("FR-022")
@Covers("FR-024")
@Covers("FR-026")
class PromotionIT extends IntegrationTest {

	@Autowired
	PromotionService promotionService;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	@Covers("FR-007")
	void promoteScreenListsStudentsWithDefaultsAndPreview() {
		TwoYears years = YearChangeTestData.twoConsecutiveYears(jdbc, "PromScr", LocalDate.of(3901, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, years.newYear(), "18000.00");
		YearChangeTestData.startNewYear(clock, years.newYear());

		// Seed student in standard 0 with fee account and partial receipt
		Fx student = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 390101, "12000.00", null);
		FeeTestData.cashReceipt(jdbc, years.oldYear(), student, "SCR-R1", LocalDate.of(3901, 5, 1), "0.00", "9000.00");

		PromoteScreenDto screen = promotionService.getPromoteScreen(years.oldYear().standard(0));
		assertThat(screen.toYear()).contains("3902-3903");
		assertThat(screen.fromYear()).contains("3901-3902");
		assertThat(screen.nextStandard()).isNotNull();
		assertThat(screen.nextStandard().id()).isEqualTo(years.oldYear().standard(1));
		assertThat(screen.students()).hasSize(1);

		PromoteScreenDto.StudentItemDto item = screen.students().get(0);
		assertThat(item.studentId()).isEqualTo(student.studentId());
		assertThat(item.unpaidYearFee()).isEqualTo("3000.00");
		assertThat(item.defaultOutcome()).isEqualTo("PROMOTED");
		assertThat(item.defaultDivisionId()).isEqualTo(years.newYear().divisionByStandard().get(years.oldYear().standard(1)));
	}

	@Test
	void promoteStandardWithMixedOutcomesAndCarryForward() {
		TwoYears years = YearChangeTestData.twoConsecutiveYears(jdbc, "PromMix", LocalDate.of(3903, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, years.newYear(), "18000.00");
		YearChangeTestData.startNewYear(clock, years.newYear());

		// Student 1: to be PROMOTED, owes 3,000.00
		Fx s1 = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 390201, "12000.00", null);
		FeeTestData.cashReceipt(jdbc, years.oldYear(), s1, "MIX-R1", LocalDate.of(3903, 5, 1), "0.00", "9000.00");
		long ver1 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s1.enrollmentId());

		// Student 2: to be DETAINED, owes 2,000.00
		Fx s2 = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 390202, "12000.00", null);
		FeeTestData.cashReceipt(jdbc, years.oldYear(), s2, "MIX-R2", LocalDate.of(3903, 5, 1), "0.00", "10000.00");
		long ver2 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s2.enrollmentId());

		// Student 3: to be LEFT, owes 5,000.00 (carries nothing)
		Fx s3 = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 390203, "12000.00", null);
		FeeTestData.cashReceipt(jdbc, years.oldYear(), s3, "MIX-R3", LocalDate.of(3903, 5, 1), "0.00", "7000.00");
		long ver3 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s3.enrollmentId());

		PromoteInput input = new PromoteInput(List.of(
				new PromoteInput.Line(s1.enrollmentId(), "PROMOTED", years.newYear().divisionByStandard().get(years.oldYear().standard(1)), years.oldYear().category(0), null, ver1),
				new PromoteInput.Line(s2.enrollmentId(), "DETAINED", years.newYear().divisionByStandard().get(years.oldYear().standard(0)), years.oldYear().category(0), null, ver2),
				new PromoteInput.Line(s3.enrollmentId(), "LEFT", null, null, null, ver3)
		));

		PromoteResult result = promotionService.promote(years.oldYear().standard(0), input);
		assertThat(result.promoted()).isEqualTo(1);
		assertThat(result.detained()).isEqualTo(1);
		assertThat(result.left()).isEqualTo(1);
		assertThat(result.carriedTotal()).isEqualTo("5000.00"); // 3000 + 2000 + 0

		// Check carried amounts in fee_brought_forward
		BigDecimal bf1 = jdbc.queryForObject("select amount from fee_brought_forward where student_id = ?", BigDecimal.class, s1.studentId());
		assertThat(bf1).isEqualByComparingTo("3000.00");

		BigDecimal bf2 = jdbc.queryForObject("select amount from fee_brought_forward where student_id = ?", BigDecimal.class, s2.studentId());
		assertThat(bf2).isEqualByComparingTo("2000.00");

		Integer bf3Count = jdbc.queryForObject("select count(*) from fee_brought_forward where student_id = ?", Integer.class, s3.studentId());
		assertThat(bf3Count).isZero(); // Left carries nothing

		String s3Status = jdbc.queryForObject("select status from students where id = ?", String.class, s3.studentId());
		assertThat(s3Status).isEqualTo("Left");

		// Run items in DB
		Integer itemCount = jdbc.queryForObject("select count(*) from promotion_run_items where run_id = ?", Integer.class, result.runId());
		assertThat(itemCount).isEqualTo(3);
	}

	@Test
	void refuseWhenFeeStructureNotFinalised() {
		TwoYears years = YearChangeTestData.twoConsecutiveYears(jdbc, "PromDraft", LocalDate.of(3905, 4, 1), 2);
		YearChangeTestData.startNewYear(clock, years.newYear());
		// Notice: newYear fee structure is NOT finalised

		Fx s1 = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 390301, "12000.00", null);
		long ver1 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s1.enrollmentId());

		PromoteInput input = new PromoteInput(List.of(
				new PromoteInput.Line(s1.enrollmentId(), "PROMOTED", years.newYear().divisionByStandard().get(years.oldYear().standard(1)), years.oldYear().category(0), null, ver1)
		));

		assertThatThrownBy(() -> promotionService.promote(years.oldYear().standard(0), input))
				.isInstanceOf(CodedConflictException.class)
				.satisfies(ex -> assertThat(((CodedConflictException) ex).getCode()).isEqualTo("FEE_STRUCTURE_NOT_FINALISED"));
	}

	@Test
	void refuseWhenFeeNotSet() {
		TwoYears years = YearChangeTestData.twoConsecutiveYears(jdbc, "PromNoFee", LocalDate.of(3907, 4, 1), 2);

		// Finalised but NO fee row for standard 1
		FeeTestData.addRow(jdbc, years.newYear().yearId(), years.oldYear().standard(0), null, "15000.00", null, null);
		FeeTestData.setStatus(jdbc, years.newYear().yearId(), true);
		YearChangeTestData.startNewYear(clock, years.newYear());

		Fx s1 = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 390401, "12000.00", null);
		long ver1 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s1.enrollmentId());

		PromoteInput input = new PromoteInput(List.of(
				new PromoteInput.Line(s1.enrollmentId(), "PROMOTED", years.newYear().divisionByStandard().get(years.oldYear().standard(1)), years.oldYear().category(0), null, ver1)
		));

		assertThatThrownBy(() -> promotionService.promote(years.oldYear().standard(0), input))
				.isInstanceOf(CodedConflictException.class)
				.satisfies(ex -> assertThat(((CodedConflictException) ex).getCode()).isEqualTo("FEE_NOT_SET"));
	}

	@Test
	void rollbackOnBadLineLeavesWholeStandardUntouched() {
		TwoYears years = YearChangeTestData.twoConsecutiveYears(jdbc, "PromRoll", LocalDate.of(3909, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, years.newYear(), "18000.00");
		YearChangeTestData.startNewYear(clock, years.newYear());

		Fx s1 = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 390501, "12000.00", null);
		long ver1 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s1.enrollmentId());

		// Second student has a bad division id (999999L)
		Fx s2 = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 390502, "12000.00", null);
		long ver2 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s2.enrollmentId());

		PromoteInput input = new PromoteInput(List.of(
				new PromoteInput.Line(s1.enrollmentId(), "PROMOTED", years.newYear().divisionByStandard().get(years.oldYear().standard(1)), years.oldYear().category(0), null, ver1),
				new PromoteInput.Line(s2.enrollmentId(), "PROMOTED", 999999L, years.oldYear().category(0), null, ver2)
		));

		assertThatThrownBy(() -> promotionService.promote(years.oldYear().standard(0), input))
				.isInstanceOf(ValidationException.class);

		// Entire standard remains Studying
		String s1Status = jdbc.queryForObject("select year_status from student_enrollments where id = ?", String.class, s1.enrollmentId());
		assertThat(s1Status).isEqualTo("Studying");
	}

	@Test
	void streamRequiredForStreamStandard() {
		TwoYears years = YearChangeTestData.twoConsecutiveYears(jdbc, "PromStr", LocalDate.of(3911, 4, 1), 2);

		// Target standard uses stream
		jdbc.update("update standards set uses_stream = true where id = ?", years.oldYear().standard(1));

		YearChangeTestData.finaliseNewYear(jdbc, years.newYear(), "18000.00");
		YearChangeTestData.startNewYear(clock, years.newYear());

		Fx s1 = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 390601, "12000.00", null);
		long ver1 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s1.enrollmentId());

		// Promoting without stream
		PromoteInput input = new PromoteInput(List.of(
				new PromoteInput.Line(s1.enrollmentId(), "PROMOTED", years.newYear().divisionByStandard().get(years.oldYear().standard(1)), years.oldYear().category(0), null, ver1)
		));

		assertThatThrownBy(() -> promotionService.promote(years.oldYear().standard(0), input))
				.isInstanceOf(ValidationException.class);

		// Promoting with Science succeeds
		PromoteInput inputOk = new PromoteInput(List.of(
				new PromoteInput.Line(s1.enrollmentId(), "PROMOTED", years.newYear().divisionByStandard().get(years.oldYear().standard(1)), years.oldYear().category(0), "Science", ver1)
		));
		PromoteResult result = promotionService.promote(years.oldYear().standard(0), inputOk);
		assertThat(result.promoted()).isEqualTo(1);
	}

	@Test
	void stdTwelveOffersOnlyLeftOrDetained() {
		// Year with 1 standard (so it is the last standard)
		TwoYears years = YearChangeTestData.twoConsecutiveYears(jdbc, "PromLast", LocalDate.of(3913, 4, 1), 1);
		YearChangeTestData.finaliseNewYear(jdbc, years.newYear(), "18000.00");
		YearChangeTestData.startNewYear(clock, years.newYear());

		Fx s1 = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 390701, "12000.00", null);
		long ver1 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s1.enrollmentId());

		// Attempting to promote from last standard
		PromoteInput input = new PromoteInput(List.of(
				new PromoteInput.Line(s1.enrollmentId(), "PROMOTED", years.newYear().divisionByStandard().get(years.oldYear().standard(0)), years.oldYear().category(0), null, ver1)
		));

		assertThatThrownBy(() -> promotionService.promote(years.oldYear().standard(0), input))
				.isInstanceOf(ValidationException.class);

		// Detaining succeeds
		PromoteInput inputOk = new PromoteInput(List.of(
				new PromoteInput.Line(s1.enrollmentId(), "DETAINED", years.newYear().divisionByStandard().get(years.oldYear().standard(0)), years.oldYear().category(0), null, ver1)
		));
		PromoteResult result = promotionService.promote(years.oldYear().standard(0), inputOk);
		assertThat(result.detained()).isEqualTo(1);
	}

}
