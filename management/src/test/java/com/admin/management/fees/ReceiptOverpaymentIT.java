package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.JsonMoney;
import com.admin.management.support.ReceiptApi;
import com.admin.management.support.SignedInClient;

/** Each amount is limited to what is still due for its row, and earlier years are paid first (FR-005; quickstart 5, 9). */
@Covers("FR-005")
class ReceiptOverpaymentIT extends IntegrationTest {

	static final String TODAY = "3631-10-01";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	Setup setup;

	SignedInClient client;

	int students;

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "OverIT", LocalDate.of(3631, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
	}

	@BeforeEach
	void today() throws Exception {
		clock.setDate(LocalDate.of(3631, 10, 1));
		client = SignedInClient.of(mvc);
	}

	Fx student(String fee, String registration) {
		return FeeTestData.studentWithAccount(jdbc, setup, 933000 + (++students), fee, registration);
	}

	@Test
	void aYearFeeAboveWhatIsDueShowsTheAmountStillDueAndSavesNothing() throws Exception {
		Fx s = student("36500.00", "500.00");
		ReceiptApi.create(client, s.studentId(),
				"\"receiptNo\":\"O-1\",\"receiptDate\":\"" + TODAY + "\",\"registrationPaid\":500.00,"
						+ "\"feePaid\":10000.00,\"paymentMode\":\"CASH\"")
			.andExpect(status().isCreated());

		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("O-2", TODAY, "30000.00"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OVERPAYMENT"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("feePaid"))
			.andExpect(JsonMoney.at("$.current.due", "26500.00"))
			.andExpect(jsonPath("$.detail").value("Only 26500.00 is still due for the year fee."));
		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("O-3", TODAY, "26500.00"))
			.andExpect(status().isCreated());
		assertThat(FeeTestData.balance(jdbc, s.enrollmentId()).pendingYearFee()).isEqualByComparingTo("0");
		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("O-4", TODAY, "0.01"))
			.andExpect(status().isConflict())
			.andExpect(JsonMoney.at("$.current.due", "0.00"));
	}

	@Test
	void aRegistrationFeeAboveItsDueIsRefused() throws Exception {
		Fx s = student("1000.00", "500.00");

		ReceiptApi.create(client, s.studentId(),
				"\"receiptNo\":\"O-5\",\"receiptDate\":\"" + TODAY + "\",\"registrationPaid\":500.01,\"feePaid\":1.00,"
						+ "\"paymentMode\":\"CASH\"")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OVERPAYMENT"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("registrationPaid"))
			.andExpect(JsonMoney.at("$.current.due", "500.00"));
		ReceiptApi.create(client, s.studentId(),
				"\"receiptNo\":\"O-6\",\"receiptDate\":\"" + TODAY + "\",\"registrationPaid\":200.00,\"paymentMode\":\"CASH\"")
			.andExpect(status().isCreated());
		ReceiptApi.create(client, s.studentId(),
				"\"receiptNo\":\"O-7\",\"receiptDate\":\"" + TODAY + "\",\"registrationPaid\":300.01,\"paymentMode\":\"CASH\"")
			.andExpect(status().isConflict())
			.andExpect(JsonMoney.at("$.current.due", "300.00"));
	}

	@Test
	void anyAmountAtAnyTimeIsAllowedUpToWhatIsDue() throws Exception {
		Fx s = student("1000.00", null);

		for (int i = 1; i <= 4; i++) {
			ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("O-8" + i, TODAY, "250.00"))
				.andExpect(status().isCreated());
		}
		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("O-89", TODAY, "0.01")).andExpect(status().isConflict());
	}

	@Test
	void earlierYearsPendingMustBePaidBeforeACurrentYearReceipt() throws Exception {
		Fx s = student("1000.00", null);
		jdbc.update("insert into fee_brought_forward (student_id, amount, updated_at) values (?, 3000.00, now())",
				s.studentId());

		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("O-9", TODAY, "10.00"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BROUGHT_FORWARD_PENDING"))
			.andExpect(JsonMoney.at("$.current.pending", "3000.00"))
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("3000.00")));
		assertThat(jdbc.queryForObject("select count(*) from receipts where student_id = ?", Integer.class,
				s.studentId())).isZero();
	}

	@Test
	void theBalanceAfterAnOverpaymentRefusalIsUnchanged() throws Exception {
		Fx s = student("1000.00", null);
		String token = ReceiptApi.token(client, s.studentId());

		ReceiptApi.createWithToken(client, s.studentId(), token, ReceiptApi.cash("O-10", TODAY, "1000.01"))
			.andExpect(status().isConflict());

		assertThat(ReceiptApi.token(client, s.studentId())).isEqualTo(token);
	}

}
