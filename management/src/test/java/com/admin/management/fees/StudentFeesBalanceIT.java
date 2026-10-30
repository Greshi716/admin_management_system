package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.admin.management.support.JsonMoney;
import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/**
 * The Fees block of the profile now holds paid, pending, the total with the brought-forward amount, excess paid, "Paid
 * up to" and the student's receipts with status (FR-014, FR-012). The year is a 365-day year starting 1 April, so
 * quickstart 6 to 8 hold: 10,000.00 paid of 36,500.00 is 100 days, 9 July; 18,250.00 is 182.5 days, 183 days, 30 September.
 */
@Covers("FR-014")
@Covers("FR-012")
class StudentFeesBalanceIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	Setup setup;

	SignedInClient client;

	int students;

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "PfBal", LocalDate.of(3901, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
	}

	@BeforeEach
	void today() throws Exception {
		clock.setDate(LocalDate.of(3901, 10, 1));
		client = SignedInClient.of(mvc);
	}

	Fx student() {
		return FeeTestData.studentWithAccount(jdbc, setup, 940000 + (++students), "36500.00", "500.00");
	}

	private ResultActions fees(Fx s) throws Exception {
		return client.get("/api/students/" + s.studentId() + "/fees").andExpect(status().isOk());
	}

	@Test
	void paidPendingAndPaidUpToFollowTheReceiptsAndTheReceiptsCarryNoPaidUpTo() throws Exception {
		Fx s = student();
		FeeTestData.cashReceipt(jdbc, setup, s, "PB-1", LocalDate.of(3901, 6, 1), "500.00", "10000.00");

		fees(s).andExpect(JsonMoney.at("$.balance.paid", "10500.00"))
			.andExpect(JsonMoney.at("$.balance.pending", "26500.00"))
			.andExpect(JsonMoney.at("$.balance.totalFee", "37000.00"))
			.andExpect(jsonPath("$.balance.paidUpTo.state").value("DATE"))
			.andExpect(jsonPath("$.balance.paidUpTo.date").value("3901-07-09"))
			.andExpect(jsonPath("$.receipts.length()").value(1))
			.andExpect(jsonPath("$.receipts[0].receiptNo").value("PB-1"))
			.andExpect(jsonPath("$.receipts[0].status").value("VALID"))
			.andExpect(JsonMoney.at("$.receipts[0].total", "10500.00"))
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString("\"paidUpTo\":\"")) ));

		FeeTestData.cashReceipt(jdbc, setup, s, "PB-2", LocalDate.of(3901, 6, 2), "0", "8250.00");

		fees(s).andExpect(jsonPath("$.balance.paidUpTo.date").value("3901-09-30"))
			.andExpect(jsonPath("$.receipts.length()").value(2))
			.andExpect(jsonPath("$.receipts[0].receiptNo").value("PB-2"));
	}

	@Test
	void aFullyPaidYearShowsFullAndNothingPending() throws Exception {
		Fx s = student();
		FeeTestData.cashReceipt(jdbc, setup, s, "PB-3", LocalDate.of(3901, 6, 1), "500.00", "36500.00");

		fees(s).andExpect(JsonMoney.at("$.balance.pending", "0.00"))
			.andExpect(jsonPath("$.balance.paidUpTo.state").value("FULL"))
			.andExpect(jsonPath("$.balance.paidUpTo.date").value("3902-03-31"))
			.andExpect(JsonMoney.at("$.balance.excessPaid", "0.00"));
	}

	@Test
	void onlyTheRegistrationFeePaidIsNothingPaid() throws Exception {
		Fx s = student();
		FeeTestData.cashReceipt(jdbc, setup, s, "PB-4", LocalDate.of(3901, 6, 1), "500.00", "0");

		fees(s).andExpect(JsonMoney.at("$.balance.paid", "500.00"))
			.andExpect(JsonMoney.at("$.balance.pending", "36500.00"))
			.andExpect(jsonPath("$.balance.paidUpTo.state").value("NOTHING"))
			.andExpect(jsonPath("$.balance.paidUpTo.date").doesNotExist());
	}

	@Test
	void aMarkedChequeIsListedWithItsStatusAndIsNotCounted() throws Exception {
		Fx s = student();
		FeeTestData.cashReceipt(jdbc, setup, s, "PB-5", LocalDate.of(3901, 6, 1), "0", "10000.00");
		long cheque = FeeTestData.chequeReceipt(jdbc, setup, s, "PB-6", LocalDate.of(3901, 6, 2), "5000.00");
		FeeTestData.markNotCleared(jdbc, cheque, "Bounced");

		fees(s).andExpect(JsonMoney.at("$.balance.paid", "10000.00"))
			.andExpect(JsonMoney.at("$.balance.pending", "27000.00"))
			.andExpect(jsonPath("$.balance.paidUpTo.date").value("3901-07-09"))
			.andExpect(jsonPath("$.receipts.length()").value(2))
			.andExpect(jsonPath("$.receipts[0].receiptNo").value("PB-6"))
			.andExpect(jsonPath("$.receipts[0].status").value("CHEQUE_NOT_CLEARED"))
			.andExpect(jsonPath("$.receipts[1].status").value("VALID"));
	}

	@Test
	void theTotalIncludesTheBroughtForwardAmountAndEarlierYearsPendingIsShown() throws Exception {
		Fx s = student();
		jdbc.update("insert into fee_brought_forward (student_id, amount, updated_at) values (?, 3000.00, now())",
				s.studentId());

		fees(s).andExpect(JsonMoney.at("$.balance.broughtForward", "3000.00"))
			.andExpect(JsonMoney.at("$.balance.totalFee", "40000.00"))
			.andExpect(JsonMoney.at("$.balance.earlierYearsPending", "3000.00"))
			.andExpect(JsonMoney.at("$.balance.pending", "40000.00"))
			// F2's fields stay as they were
			.andExpect(JsonMoney.at("$.totalFee", "37000.00"))
			.andExpect(JsonMoney.at("$.totalDue", "40000.00"));
	}

	@Test
	void aFeeRevisedBelowWhatIsPaidShowsNoPendingAndTheExcess() throws Exception {
		Fx s = student();
		FeeTestData.cashReceipt(jdbc, setup, s, "PB-7", LocalDate.of(3901, 6, 1), "0", "10000.00");
		jdbc.update("update fee_accounts set amount = 6000.00 where enrollment_id = ?", s.enrollmentId());

		var body = fees(s).andExpect(JsonMoney.at("$.balance.yearFeeDue", "0.00"))
			.andExpect(JsonMoney.at("$.balance.excessPaid", "4000.00"))
			.andExpect(jsonPath("$.balance.paidUpTo.state").value("FULL"))
			.andReturn().getResponse().getContentAsString();
		assertThat(body).contains("\"excessPaid\":4000.00");
	}

	@Test
	void aLeftStudentStillShowsTheBalanceAndTheReceipts() throws Exception {
		Fx s = student();
		FeeTestData.cashReceipt(jdbc, setup, s, "PB-8", LocalDate.of(3901, 6, 1), "0", "1000.00");
		jdbc.update("update students set status = 'Left' where id = ?", s.studentId());

		fees(s).andExpect(JsonMoney.at("$.balance.paid", "1000.00")).andExpect(jsonPath("$.receipts.length()").value(1));
	}

}
