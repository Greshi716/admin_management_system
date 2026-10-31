package com.admin.management.yearchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.YearChangeTestData;
import com.admin.management.support.YearChangeTestData.TwoYears;

/**
 * Tests for PostgreSQL developer correction function correct_promotion (FR-025, Principle II, Principle V).
 */
@Covers("FR-025")
class CorrectPromotionIT extends IntegrationTest {

	@Autowired
	PromotionService promotionService;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void correctPromotedStudentReversesCarriedAmountAndRestoresStudying() {
		TwoYears years = YearChangeTestData.twoConsecutiveYears(jdbc, "CorrProm", LocalDate.of(3701, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, years.newYear(), "15000.00");
		YearChangeTestData.startNewYear(clock, years.newYear());

		Fx student = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 370101, "12000.00", null);
		FeeTestData.cashReceipt(jdbc, years.oldYear(), student, "CORR-R1", LocalDate.of(3701, 5, 1), "0.00", "9000.00");
		long ver = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, student.enrollmentId());

		PromoteInput input = new PromoteInput(List.of(
				new PromoteInput.Line(student.enrollmentId(), "PROMOTED", years.newYear().divisionByStandard().get(years.oldYear().standard(1)), years.oldYear().category(0), null, ver)
		));
		PromoteResult result = promotionService.promote(years.oldYear().standard(0), input);

		Long itemId = jdbc.queryForObject("select id from promotion_run_items where run_id = ?", Long.class, result.runId());

		// Execute developer correction
		jdbc.execute("SELECT correct_promotion(" + itemId + ", 'Promoted by mistake', 'Dev Jane')");

		// 1. Old record is restored to Studying
		String status = jdbc.queryForObject("select year_status from student_enrollments where id = ?", String.class, student.enrollmentId());
		assertThat(status).isEqualTo("Studying");

		// 2. Carried amount is reversed
		BigDecimal bf = jdbc.queryForObject("select amount from fee_brought_forward where student_id = ?", BigDecimal.class, student.studentId());
		assertThat(bf).isEqualByComparingTo("0.00");

		// 3. promotion_corrections row exists
		Map<String, Object> corr = jdbc.queryForMap("select * from promotion_corrections where item_id = ?", itemId);
		assertThat(corr.get("reason")).isEqualTo("Promoted by mistake");
		assertThat(corr.get("done_by")).isEqualTo("Dev Jane");
		assertThat((BigDecimal) corr.get("amount_reversed")).isEqualByComparingTo("3000.00");

		// 4. Audit entry exists in area Students
		Map<String, Object> audit = jdbc.queryForMap("""
				select * from audit_log
				where area = 'Students' and action = 'Edit' and entity = ?
				order by id desc limit 1
				""", "Student#" + student.studentId());
		assertThat(audit.get("reason")).isEqualTo("Promoted by mistake");
		assertThat(audit.get("changes").toString()).contains("Dev Jane");
		assertThat(audit.get("changes").toString()).contains("3000.00");

		// 5. No records deleted
		Integer enrollmentCount = jdbc.queryForObject("select count(*) from student_enrollments where student_id = ?", Integer.class, student.studentId());
		assertThat(enrollmentCount).isEqualTo(2); // old and new records both stay
	}

	@Test
	void correctLeftStudentWithAndWithoutReactivateFlag() {
		TwoYears years = YearChangeTestData.twoConsecutiveYears(jdbc, "CorrLeft", LocalDate.of(3703, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, years.newYear(), "15000.00");
		YearChangeTestData.startNewYear(clock, years.newYear());

		// Student 1: corrected without explicit flag -> student status stays Left
		Fx s1 = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 370301, "12000.00", null);
		long v1 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s1.enrollmentId());
		PromoteInput in1 = new PromoteInput(List.of(new PromoteInput.Line(s1.enrollmentId(), "LEFT", null, null, null, v1)));
		PromoteResult res1 = promotionService.promote(years.oldYear().standard(0), in1);
		Long item1 = jdbc.queryForObject("select id from promotion_run_items where run_id = ?", Long.class, res1.runId());

		jdbc.execute("SELECT correct_promotion(" + item1 + ", 'Marked Left mistakenly', 'Dev John')");

		String s1YearStatus = jdbc.queryForObject("select year_status from student_enrollments where id = ?", String.class, s1.enrollmentId());
		assertThat(s1YearStatus).isEqualTo("Studying");
		String s1Status = jdbc.queryForObject("select status from students where id = ?", String.class, s1.studentId());
		assertThat(s1Status).isEqualTo("Left"); // not reactivated without explicit flag

		// Student 2: corrected with p_reactivate_student = true -> student status restored to Active
		Fx s2 = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 370302, "12000.00", null);
		long v2 = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, s2.enrollmentId());
		PromoteInput in2 = new PromoteInput(List.of(new PromoteInput.Line(s2.enrollmentId(), "LEFT", null, null, null, v2)));
		PromoteResult res2 = promotionService.promote(years.oldYear().standard(0), in2);
		Long item2 = jdbc.queryForObject("select id from promotion_run_items where run_id = ?", Long.class, res2.runId());

		jdbc.execute("SELECT correct_promotion(" + item2 + ", 'Reactivate Left', 'Dev John', NULL, NULL, NULL, true)");

		String s2YearStatus = jdbc.queryForObject("select year_status from student_enrollments where id = ?", String.class, s2.enrollmentId());
		assertThat(s2YearStatus).isEqualTo("Studying");
		String s2Status = jdbc.queryForObject("select status from students where id = ?", String.class, s2.studentId());
		assertThat(s2Status).isEqualTo("Active");
	}

	@Test
	void floorRefusalWhenReceiptsExceedReducedAmount() {
		TwoYears years = YearChangeTestData.twoConsecutiveYears(jdbc, "CorrFloor", LocalDate.of(3705, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, years.newYear(), "15000.00");
		YearChangeTestData.startNewYear(clock, years.newYear());

		// Student owes 3,000.00 which gets carried forward
		Fx student = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 370501, "12000.00", null);
		FeeTestData.cashReceipt(jdbc, years.oldYear(), student, "CFL-R1", LocalDate.of(3705, 5, 1), "0.00", "9000.00");
		long ver = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, student.enrollmentId());

		PromoteInput input = new PromoteInput(List.of(
				new PromoteInput.Line(student.enrollmentId(), "PROMOTED", years.newYear().divisionByStandard().get(years.oldYear().standard(1)), years.oldYear().category(0), null, ver)
		));
		PromoteResult result = promotionService.promote(years.oldYear().standard(0), input);
		Long itemId = jdbc.queryForObject("select id from promotion_run_items where run_id = ?", Long.class, result.runId());

		// Enter a previous-years receipt for 2,000.00 against the brought-forward balance
		FeeTestData.registerReceipt(jdbc, years.newYear(), student, "PREV-R1", LocalDate.of(3706, 4, 15), "CASH", "PREVIOUS_YEARS", "0.00", "2000.00");

		// Attempting to reverse the 3,000.00 carry would bring the brought-forward balance to 0, which is below the 2,000.00 paid!
		assertThatThrownBy(() -> jdbc.execute("SELECT correct_promotion(" + itemId + ", 'Reversing carry', 'Dev Jane')"))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("reduced brought forward amount");
	}

	@Test
	void emptyReasonOrDoneByRefused() {
		TwoYears years = YearChangeTestData.twoConsecutiveYears(jdbc, "CorrVal", LocalDate.of(3707, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, years.newYear(), "15000.00");
		YearChangeTestData.startNewYear(clock, years.newYear());

		Fx student = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 370701, "12000.00", null);
		long ver = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, student.enrollmentId());
		PromoteInput input = new PromoteInput(List.of(
				new PromoteInput.Line(student.enrollmentId(), "DETAINED", years.newYear().divisionByStandard().get(years.oldYear().standard(0)), years.oldYear().category(0), null, ver)
		));
		PromoteResult result = promotionService.promote(years.oldYear().standard(0), input);
		Long itemId = jdbc.queryForObject("select id from promotion_run_items where run_id = ?", Long.class, result.runId());

		assertThatThrownBy(() -> jdbc.execute("SELECT correct_promotion(" + itemId + ", '', 'Dev Jane')"))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("a reason is required");

		assertThatThrownBy(() -> jdbc.execute("SELECT correct_promotion(" + itemId + ", 'Reason', '')"))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("the name of the person who does it is required");

		String longReason = "A".repeat(201);
		assertThatThrownBy(() -> jdbc.execute("SELECT correct_promotion(" + itemId + ", '" + longReason + "', 'Dev Jane')"))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("at most 200 characters");
	}

	@Test
	void doubleCorrectionRefused() {
		TwoYears years = YearChangeTestData.twoConsecutiveYears(jdbc, "CorrDbl", LocalDate.of(3709, 4, 1), 2);
		YearChangeTestData.finaliseNewYear(jdbc, years.newYear(), "15000.00");
		YearChangeTestData.startNewYear(clock, years.newYear());

		Fx student = FeeTestData.studentWithAccount(jdbc, years.oldYear(), 370901, "12000.00", null);
		long ver = jdbc.queryForObject("select version from student_enrollments where id = ?", Long.class, student.enrollmentId());
		PromoteInput input = new PromoteInput(List.of(
				new PromoteInput.Line(student.enrollmentId(), "DETAINED", years.newYear().divisionByStandard().get(years.oldYear().standard(0)), years.oldYear().category(0), null, ver)
		));
		PromoteResult result = promotionService.promote(years.oldYear().standard(0), input);
		Long itemId = jdbc.queryForObject("select id from promotion_run_items where run_id = ?", Long.class, result.runId());

		jdbc.execute("SELECT correct_promotion(" + itemId + ", 'First correction', 'Dev Jane')");

		assertThatThrownBy(() -> jdbc.execute("SELECT correct_promotion(" + itemId + ", 'Second correction', 'Dev Jane')"))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("already corrected");
	}

}
