package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import com.admin.management.audit.AuditService;
import com.admin.management.fees.api.FeeCarryForward;
import com.admin.management.shared.web.ValidationException;
import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;

/**
 * Integration tests for fee carry-forward service (FR-020, FR-021, FR-022, FR-024).
 */
@Covers("FR-020")
@Covers("FR-021")
@Covers("FR-022")
@Covers("FR-024")
class FeeCarryForwardIT extends IntegrationTest {

	@Autowired
	FeeCarryForwardService carryForwardService;

	@Autowired
	BroughtForwardService broughtForwardService;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoSpyBean
	AuditService audit;

	private Setup setup(String prefix, int yearNumber) {
		return FeeTestData.year(jdbc, prefix, LocalDate.of(yearNumber, 4, 1), 2);
	}

	@Test
	void previewEqualsCarriedFigure() {
		Setup year1 = setup("CFPrev1", 3601);
		Setup year2 = setup("CFPrev2", 3602);
		Fx student = FeeTestData.studentWithAccount(jdbc, year1, 360101, "12000.00", "1000.00");
		FeeTestData.cashReceipt(jdbc, year1, student, "CF-R1", LocalDate.of(3601, 5, 1), "1000.00", "9000.00");

		BigDecimal preview = carryForwardService.preview(student.enrollmentId());
		assertThat(preview.compareTo(new BigDecimal("3000.00"))).isZero();
		assertThat(preview.scale()).isEqualTo(2);

		FeeCarryForward.CarryForwardResult result = carryForwardService.carryForward(student.studentId(), year1.yearId(),
				year2.yearId(), student.enrollmentId());

		assertThat(result.carriedAmount().compareTo(preview)).isZero();
		assertThat(result.carriedAmount().compareTo(new BigDecimal("3000.00"))).isZero();
		assertThat(result.broughtForwardBefore().compareTo(new BigDecimal("0.00"))).isZero();
		assertThat(result.broughtForwardAfter().compareTo(new BigDecimal("3000.00"))).isZero();

		BigDecimal savedAmount = jdbc.queryForObject("select amount from fee_brought_forward where student_id = ?",
				BigDecimal.class, student.studentId());
		assertThat(savedAmount.compareTo(new BigDecimal("3000.00"))).isZero();
	}

	@Test
	void carryForwardAddsToExistingBroughtForward() {
		Setup year1 = setup("CFAdd1", 3603);
		Setup year2 = setup("CFAdd2", 3604);
		Fx student = FeeTestData.studentWithAccount(jdbc, year1, 360301, "12000.00", null);
		FeeTestData.cashReceipt(jdbc, year1, student, "CF-R2", LocalDate.of(3603, 5, 1), "0.00", "9000.00");

		// Seed initial brought forward row with 5,000.00 and note
		jdbc.update("insert into fee_brought_forward (student_id, amount, note, updated_at, version) values (?, 5000.00, 'Old arrears', now(), 0)",
				student.studentId());

		FeeCarryForward.CarryForwardResult result = carryForwardService.carryForward(student.studentId(), year1.yearId(),
				year2.yearId(), student.enrollmentId());

		assertThat(result.carriedAmount().compareTo(new BigDecimal("3000.00"))).isZero();
		assertThat(result.broughtForwardBefore().compareTo(new BigDecimal("5000.00"))).isZero();
		assertThat(result.broughtForwardAfter().compareTo(new BigDecimal("8000.00"))).isZero();

		BigDecimal savedAmount = jdbc.queryForObject("select amount from fee_brought_forward where student_id = ?",
				BigDecimal.class, student.studentId());
		String savedNote = jdbc.queryForObject("select note from fee_brought_forward where student_id = ?",
				String.class, student.studentId());
		assertThat(savedAmount.compareTo(new BigDecimal("8000.00"))).isZero();
		assertThat(savedNote).isEqualTo("Old arrears");
	}

	@Test
	void unpaidRegistrationFeeNeverCarried() {
		Setup year1 = setup("CFReg1", 3605);
		Setup year2 = setup("CFReg2", 3606);
		// Year fee 10,000.00, registration fee 2,000.00
		Fx student = FeeTestData.studentWithAccount(jdbc, year1, 360501, "10000.00", "2000.00");
		// Paid year fee 10,000.00, registration paid 0.00
		FeeTestData.cashReceipt(jdbc, year1, student, "CF-R3", LocalDate.of(3605, 5, 1), "0.00", "10000.00");

		BigDecimal preview = carryForwardService.preview(student.enrollmentId());
		assertThat(preview.compareTo(BigDecimal.ZERO)).isZero();

		FeeCarryForward.CarryForwardResult result = carryForwardService.carryForward(student.studentId(), year1.yearId(),
				year2.yearId(), student.enrollmentId());

		assertThat(result.carriedAmount().compareTo(BigDecimal.ZERO)).isZero();
		assertThat(result.broughtForwardBefore().compareTo(BigDecimal.ZERO)).isZero();
		assertThat(result.broughtForwardAfter().compareTo(BigDecimal.ZERO)).isZero();

		Integer count = jdbc.queryForObject("select count(*) from fee_brought_forward where student_id = ?",
				Integer.class, student.studentId());
		assertThat(count).isZero();
	}

	@Test
	void feeNotSetCarriesZero() {
		Setup year1 = setup("CFNotSet1", 3607);
		Setup year2 = setup("CFNotSet2", 3608);
		long enrollmentId = FeeTestData.student(jdbc, year1, 360701, 0, 0);
		long studentId = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class,
				enrollmentId);

		BigDecimal preview = carryForwardService.preview(enrollmentId);
		assertThat(preview.compareTo(BigDecimal.ZERO)).isZero();

		FeeCarryForward.CarryForwardResult result = carryForwardService.carryForward(studentId, year1.yearId(),
				year2.yearId(), enrollmentId);

		assertThat(result.carriedAmount().compareTo(BigDecimal.ZERO)).isZero();
		Integer count = jdbc.queryForObject("select count(*) from fee_brought_forward where student_id = ?",
				Integer.class, studentId);
		assertThat(count).isZero();
	}

	@Test
	void fullyPaidWritesNothing() {
		Setup year1 = setup("CFFull1", 3609);
		Setup year2 = setup("CFFull2", 3610);
		Fx student = FeeTestData.studentWithAccount(jdbc, year1, 360901, "10000.00", null);
		FeeTestData.cashReceipt(jdbc, year1, student, "CF-R4", LocalDate.of(3609, 5, 1), "0.00", "10000.00");

		BigDecimal preview = carryForwardService.preview(student.enrollmentId());
		assertThat(preview.compareTo(BigDecimal.ZERO)).isZero();

		FeeCarryForward.CarryForwardResult result = carryForwardService.carryForward(student.studentId(), year1.yearId(),
				year2.yearId(), student.enrollmentId());

		assertThat(result.carriedAmount().compareTo(BigDecimal.ZERO)).isZero();
		Integer count = jdbc.queryForObject("select count(*) from fee_brought_forward where student_id = ?",
				Integer.class, student.studentId());
		assertThat(count).isZero();

		List<String> auditLogs = jdbc.queryForList(
				"select action from audit_log where area = 'Fees' and entity = ?",
				String.class, "BroughtForward#" + student.studentId());
		assertThat(auditLogs).isEmpty();
	}

	@Test
	void auditEntryContentsAndFailureRollback() {
		Setup year1 = setup("CFAudit1", 3611);
		Setup year2 = setup("CFAudit2", 3612);
		Fx student1 = FeeTestData.studentWithAccount(jdbc, year1, 361101, "12000.00", null);
		FeeTestData.cashReceipt(jdbc, year1, student1, "CF-R5", LocalDate.of(3611, 5, 1), "0.00", "9000.00");

		carryForwardService.carryForward(student1.studentId(), year1.yearId(), year2.yearId(), student1.enrollmentId());

		String changes = jdbc.queryForObject(
				"select changes::text from audit_log where area = 'Fees' and entity = ?",
				String.class, "BroughtForward#" + student1.studentId());

		assertThat(changes).contains("\"studentId\": " + student1.studentId());
		assertThat(changes).contains("\"oldYearId\": " + year1.yearId());
		assertThat(changes).contains("\"newYearId\": " + year2.yearId());
		assertThat(changes).contains("\"oldAmount\": \"0.00\"");
		assertThat(changes).contains("\"carriedAmount\": \"3000.00\"");
		assertThat(changes).contains("\"newAmount\": \"3000.00\"");

		// Test rollback on audit failure
		Fx student2 = FeeTestData.studentWithAccount(jdbc, year1, 361102, "12000.00", null);
		FeeTestData.cashReceipt(jdbc, year1, student2, "CF-R6", LocalDate.of(3611, 5, 1), "0.00", "9000.00");

		doThrow(new IllegalStateException("audit insert failed"))
				.when(AopTestUtils.<AuditService>getUltimateTargetObject(audit))
				.record(eq("Fees"), eq("Edit"), eq("BroughtForward#" + student2.studentId()), any(), any());

		assertThatThrownBy(() -> carryForwardService.carryForward(student2.studentId(), year1.yearId(), year2.yearId(),
				student2.enrollmentId()))
				.hasMessageContaining("audit insert failed");

		Integer count = jdbc.queryForObject("select count(*) from fee_brought_forward where student_id = ?",
				Integer.class, student2.studentId());
		assertThat(count).isZero();
	}

	@Test
	void belowPreviousYearsReceiptsFloorHolds() {
		Setup year1 = setup("CFFloor1", 3613);
		Setup year2 = setup("CFFloor2", 3614);
		Fx student = FeeTestData.studentWithAccount(jdbc, year1, 361301, "12000.00", null);
		FeeTestData.cashReceipt(jdbc, year1, student, "CF-R7", LocalDate.of(3613, 5, 1), "0.00", "9000.00");

		// Initial brought forward: 5,000.00
		jdbc.update("insert into fee_brought_forward (student_id, amount, note, updated_at, version) values (?, 5000.00, 'Initial', now(), 0)",
				student.studentId());

		// Receipt paid for previous years: 2,000.00
		FeeTestData.registerReceipt(jdbc, year1, student, "CF-R8", LocalDate.of(3613, 6, 1), "CASH", "PREVIOUS_YEARS",
				"0.00", "2000.00");

		// Carry forward 3,000.00 -> total becomes 8,000.00
		carryForwardService.carryForward(student.studentId(), year1.yearId(), year2.yearId(), student.enrollmentId());

		BigDecimal amountAfterCarry = jdbc.queryForObject("select amount from fee_brought_forward where student_id = ?",
				BigDecimal.class, student.studentId());
		Long version = jdbc.queryForObject("select version from fee_brought_forward where student_id = ?",
				Long.class, student.studentId());
		assertThat(amountAfterCarry.compareTo(new BigDecimal("8000.00"))).isZero();

		// Editing brought forward below 2,000.00 (the paid receipts floor) is refused
		assertThatThrownBy(() -> broughtForwardService.save(student.studentId(),
				new BroughtForwardInput(version, new BigDecimal("1500.00"), "Lowered")))
				.isInstanceOf(ValidationException.class)
				.satisfies(ex -> assertThat(((ValidationException) ex).getCode()).isEqualTo("BELOW_PREVIOUS_YEARS_PAID"));

		// Editing brought forward above 2,000.00 is allowed
		broughtForwardService.save(student.studentId(),
				new BroughtForwardInput(version, new BigDecimal("2500.00"), "Lowered ok"));

		BigDecimal finalAmount = jdbc.queryForObject("select amount from fee_brought_forward where student_id = ?",
				BigDecimal.class, student.studentId());
		assertThat(finalAmount.compareTo(new BigDecimal("2500.00"))).isZero();
	}

}
