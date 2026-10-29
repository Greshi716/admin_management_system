package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/**
 * The balance of a student worked out from the valid receipts, the Collect fee context and the preview (FR-014,
 * FR-016, FR-012; SC-002). Every test has a year of its own because receipts cannot be deleted.
 */
@Covers("FR-014")
@Covers("FR-016")
@Covers("FR-012")
class FeeBalanceIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	FeeBalanceService balances;

	int receiptNumbers;

	String no() {
		return "FB-" + (++receiptNumbers);
	}

	/** A year starting on 1 April of {@code startYear}, today set inside it, one student with an account. */
	record Case(Setup setup, Fx student) {
	}

	Case aCase(String prefix, int startYear, int studentNo, String fee, String registration) {
		Setup setup = FeeTestData.year(jdbc, prefix, LocalDate.of(startYear, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
		Fx student = FeeTestData.studentWithAccount(jdbc, setup, studentNo, fee, registration);
		clock.setDate(LocalDate.of(startYear, 10, 1));
		return new Case(setup, student);
	}

	FeeBalance balance(Case c) {
		return balances.balance(c.student().studentId(), c.student().enrollmentId());
	}

	@Test
	void theBalanceFollowsASequenceOfPartialFullPastDatedAndNotClearedReceipts() {
		Case c = aCase("SeqBal", 3501, 920001, "36500.00", "500.00");

		FeeBalance none = balance(c);
		assertThat(none.paid()).isEqualByComparingTo("0");
		assertThat(none.pending()).isEqualByComparingTo("37000.00");
		assertThat(none.paidUpTo().state()).isEqualTo("NOTHING");

		FeeTestData.cashReceipt(jdbc, c.setup(), c.student(), no(), LocalDate.of(3501, 10, 1), "500.00", "10000.00");
		FeeBalance first = balance(c);
		assertThat(first.paid()).isEqualByComparingTo("10500.00");
		assertThat(first.pending()).isEqualByComparingTo("26500.00");
		assertThat(first.registrationDue()).isEqualByComparingTo("0");
		assertThat(first.yearFeeDue()).isEqualByComparingTo("26500.00");
		assertThat(first.paidUpTo().state()).isEqualTo("DATE");
		assertThat(first.paidUpTo().date()).isEqualTo(LocalDate.of(3501, 7, 9));

		FeeTestData.cashReceipt(jdbc, c.setup(), c.student(), no(), LocalDate.of(3501, 5, 1), "0.00", "8250.00");
		assertThat(balance(c).paidUpTo().date()).isEqualTo(LocalDate.of(3501, 9, 30));

		long cheque = FeeTestData.chequeReceipt(jdbc, c.setup(), c.student(), no(), LocalDate.of(3501, 10, 1), "5000.00");
		assertThat(balance(c).paid()).isEqualByComparingTo("23750.00");
		FeeTestData.markNotCleared(jdbc, cheque, "Bounced");
		FeeBalance afterMark = balance(c);
		assertThat(afterMark.paid()).isEqualByComparingTo("18750.00");
		assertThat(afterMark.paidUpTo().date()).isEqualTo(LocalDate.of(3501, 9, 30));

		FeeTestData.cashReceipt(jdbc, c.setup(), c.student(), no(), LocalDate.of(3501, 10, 1), "0.00", "18250.00");
		FeeBalance full = balance(c);
		assertThat(full.pending()).isEqualByComparingTo("0");
		assertThat(full.paidUpTo().state()).isEqualTo("FULL");
		assertThat(full.paidUpTo().date()).isEqualTo(LocalDate.of(3502, 3, 31));

		FeeTestData.Balance independent = FeeTestData.balance(jdbc, c.student().enrollmentId());
		assertThat(full.yearFeeDue()).isEqualByComparingTo(independent.pendingYearFee());
		assertThat(full.registrationDue()).isEqualByComparingTo(independent.pendingRegistration());
		assertThat(full.paid()).isEqualByComparingTo(independent.paidYearFee().add(independent.paidRegistration()));
	}

	@Test
	void everyFeeStateIsReportedWithWhyAReceiptIsNotPossible() throws Exception {
		Setup setup = FeeTestData.year(jdbc, "States", LocalDate.of(3510, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
		clock.setDate(LocalDate.of(3510, 10, 1));
		Fx set = FeeTestData.studentWithAccount(jdbc, setup, 920010, "1000.00", null);
		Fx notSet = FeeTestData.studentWithAccount(jdbc, setup, 920011, "1000.00", null);
		jdbc.update("update fee_accounts set amount = null where enrollment_id = ?", notSet.enrollmentId());
		long unfinalised = FeeTestData.student(jdbc, setup, 920012, 0, 0);
		Setup other = FeeTestData.year(jdbc, "StatesOther", LocalDate.of(3512, 4, 1), 1);
		long otherRecord = FeeTestData.student(jdbc, other, 920013, 0, 0);
		long noRecordStudent = jdbc.queryForObject("select student_id from student_enrollments where id = ?", Long.class,
				otherRecord);
		long unfinalisedStudent = jdbc.queryForObject("select student_id from student_enrollments where id = ?",
				Long.class, unfinalised);

		SignedInClient client = SignedInClient.of(mvc);
		client.get("/api/students/" + set.studentId() + "/fee-collection")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currentYear.state").value("FEE_SET"))
			.andExpect(jsonPath("$.canPayCurrentYear").value(true))
			.andExpect(jsonPath("$.canPayPreviousYears").value(false))
			.andExpect(jsonPath("$.chargesRegistrationFee").value(false))
			.andExpect(jsonPath("$.student.grNo").value("GR-920010"))
			.andExpect(jsonPath("$.student.stdDiv").value("States Std 1 / A"))
			.andExpect(content().string(containsString("\"yearFeeDue\":1000.00")));
		client.get("/api/students/" + notSet.studentId() + "/fee-collection")
			.andExpect(jsonPath("$.currentYear.state").value("FEE_NOT_SET"))
			.andExpect(jsonPath("$.canPayCurrentYear").value(false));
		client.get("/api/students/" + unfinalisedStudent + "/fee-collection")
			.andExpect(jsonPath("$.currentYear.state").value("FEE_NOT_FINALISED"))
			.andExpect(jsonPath("$.canPayCurrentYear").value(false));
		client.get("/api/students/" + noRecordStudent + "/fee-collection")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currentYear.state").value("NO_CLASS_RECORD"))
			.andExpect(jsonPath("$.canPayCurrentYear").value(false))
			.andExpect(jsonPath("$.student.stdDiv").doesNotExist());
		clock.setDate(LocalDate.of(2001, 1, 1));
		client.get("/api/students/" + set.studentId() + "/fee-collection")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currentYear").doesNotExist())
			.andExpect(jsonPath("$.canPayCurrentYear").value(false));
		client.get("/api/students/-1/fee-collection").andExpect(status().isNotFound());
	}

	@Test
	void earlierYearsPendingBlocksTheCurrentYearAndOffersThePreviousYears() throws Exception {
		Case c = aCase("EarlyBal", 3520, 920020, "1000.00", "100.00");
		Setup earlier = FeeTestData.year(jdbc, "EarlyBalOld", LocalDate.of(3518, 4, 1), 1);
		jdbc.update("insert into fee_brought_forward (student_id, amount, updated_at) values (?, 3000.00, now())",
				c.student().studentId());

		FeeBalance b = balance(c);
		assertThat(b.broughtForward()).isEqualByComparingTo("3000.00");
		assertThat(b.earlierYearsPending()).isEqualByComparingTo("3000.00");
		assertThat(b.totalFee()).isEqualByComparingTo("4100.00");
		assertThat(b.pending()).isEqualByComparingTo("4100.00");

		SignedInClient.of(mvc).get("/api/students/" + c.student().studentId() + "/fee-collection")
			.andExpect(jsonPath("$.canPayCurrentYear").value(false))
			.andExpect(jsonPath("$.canPayPreviousYears").value(true))
			.andExpect(jsonPath("$.chargesRegistrationFee").value(true))
			.andExpect(jsonPath("$.earlierYears[?(@.id == " + earlier.yearId() + ")]").exists())
			.andExpect(jsonPath("$.earlierYears[0].id").value(earlier.yearId()));
	}

	@Test
	void excessPaidAfterAReviseLowersTheFeeAndALeftStudentCanStillPay() throws Exception {
		Case c = aCase("ExcessBal", 3530, 920030, "10000.00", null);
		FeeTestData.cashReceipt(jdbc, c.setup(), c.student(), no(), LocalDate.of(3530, 10, 1), "0.00", "10000.00");
		jdbc.update("update fee_accounts set amount = 6000.00, updated_at = now() where enrollment_id = ?",
				c.student().enrollmentId());
		jdbc.update("update students set status = 'Left' where id = ?", c.student().studentId());

		FeeBalance b = balance(c);
		assertThat(b.yearFeeDue()).isEqualByComparingTo("0");
		assertThat(b.pending()).isEqualByComparingTo("0");
		assertThat(b.excessPaid()).isEqualByComparingTo("4000.00");
		assertThat(b.paidUpTo().state()).isEqualTo("FULL");

		SignedInClient.of(mvc).get("/api/students/" + c.student().studentId() + "/fee-collection")
			.andExpect(jsonPath("$.student.status").value("Left"))
			.andExpect(jsonPath("$.canPayCurrentYear").value(true));
	}

	@Test
	void theTokenChangesAfterAReceiptAMarkAnAccountChangeAndABroughtForwardChangeAndIsStableOtherwise() {
		Case c = aCase("TokenBal", 3540, 920040, "10000.00", null);
		long student = c.student().studentId();
		long enrollment = c.student().enrollmentId();

		String initial = balances.token(student, enrollment);
		assertThat(initial).startsWith("r0.m0.a").endsWith(".b0");
		assertThat(balances.token(student, enrollment)).isEqualTo(initial);

		long cheque = FeeTestData.chequeReceipt(jdbc, c.setup(), c.student(), no(), LocalDate.of(3540, 10, 1), "100.00");
		String afterReceipt = balances.token(student, enrollment);
		assertThat(afterReceipt).startsWith("r1.m0.").isNotEqualTo(initial);
		assertThat(balances.token(student, enrollment)).isEqualTo(afterReceipt);

		FeeTestData.markNotCleared(jdbc, cheque, "Bounced");
		String afterMark = balances.token(student, enrollment);
		assertThat(afterMark).startsWith("r1.m1.").isNotEqualTo(afterReceipt);

		jdbc.update("update fee_accounts set amount = 9000.00, updated_at = clock_timestamp() where enrollment_id = ?",
				enrollment);
		String afterAccount = balances.token(student, enrollment);
		assertThat(afterAccount).isNotEqualTo(afterMark);

		jdbc.update("insert into fee_brought_forward (student_id, amount, updated_at) values (?, 10.00, now())", student);
		String afterBroughtForward = balances.token(student, enrollment);
		assertThat(afterBroughtForward).isNotEqualTo(afterAccount).endsWith(".b1");
		jdbc.update("update fee_brought_forward set amount = 20.00, version = version + 1 where student_id = ?", student);
		assertThat(balances.token(student, enrollment)).isNotEqualTo(afterBroughtForward).endsWith(".b2");
	}

	@Test
	void thePreviewGivesTheTotalTheWordsAndTheBalanceAfterAndWritesNothing() throws Exception {
		Case c = aCase("PreviewBal", 3550, 920050, "36500.00", "500.00");
		long student = c.student().studentId();
		SignedInClient client = SignedInClient.of(mvc);
		long auditBefore = jdbc.queryForObject("select count(*) from audit_log", Long.class);
		long receiptsBefore = jdbc.queryForObject("select count(*) from receipts", Long.class);

		client
			.post("/api/students/" + student + "/fee-collection/preview",
					"{\"receiptType\":\"CURRENT_YEAR\",\"registrationPaid\":500.00,\"feePaid\":10000.00}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalWords").value("Ten Thousand Five Hundred Rupees Only"))
			.andExpect(content().string(containsString("\"total\":10500.00")))
			.andExpect(content().string(containsString("\"pending\":26500.00")))
			.andExpect(jsonPath("$.balanceAfter.paidUpTo.state").value("DATE"))
			.andExpect(jsonPath("$.balanceAfter.paidUpTo.date").value("3550-07-09"));

		assertThat(jdbc.queryForObject("select count(*) from audit_log", Long.class)).isEqualTo(auditBefore);
		assertThat(jdbc.queryForObject("select count(*) from receipts", Long.class)).isEqualTo(receiptsBefore);
		assertThat(balance(c).paid()).isEqualByComparingTo("0");

		client
			.post("/api/students/" + student + "/fee-collection/preview",
					"{\"receiptType\":\"CURRENT_YEAR\",\"feePaid\":100.123}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("feePaid"));
		SignedInClient.of(mvc)
			.post("/api/students/-1/fee-collection/preview", "{\"receiptType\":\"CURRENT_YEAR\",\"feePaid\":1}")
			.andExpect(status().isNotFound());
	}

	@Test
	void theBalanceUsesBigDecimalWithTwoDecimals() {
		Case c = aCase("ScaleBal", 3560, 920060, "100", "0");
		FeeBalance b = balance(c);
		assertThat(b.yearFee()).isEqualTo(new BigDecimal("100.00"));
		assertThat(b.paid().scale()).isEqualTo(2);
	}

}
