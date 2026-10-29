package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.admin.management.support.ReceiptApi;
import com.admin.management.support.SignedInClient;

/** An amount is exact to two decimals, zero or more, and never rounded; nothing is saved when it is refused (FR-023). */
@Covers("FR-023")
class ReceiptMoneyInputIT extends IntegrationTest {

	static final String TODAY = "3621-10-01";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	Setup setup;

	SignedInClient client;

	Fx student;

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "MoneyIT", LocalDate.of(3621, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
		student = FeeTestData.studentWithAccount(jdbc, setup, 932001, "100000.00", "500.00");
	}

	@BeforeEach
	void today() throws Exception {
		clock.setDate(LocalDate.of(3621, 10, 1));
		client = SignedInClient.of(mvc);
	}

	private long rows() {
		return jdbc.queryForObject("select count(*) from receipts where student_id = ?", Long.class, student.studentId());
	}

	private void refused(String no, String amounts, String field) throws Exception {
		long before = rows();
		ReceiptApi.create(client, student.studentId(),
				"\"receiptNo\":\"" + no + "\",\"receiptDate\":\"" + TODAY + "\"," + amounts + ",\"paymentMode\":\"CASH\"")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value(field));
		assertThat(rows()).isEqualTo(before);
	}

	@Test
	void moreThanTwoDecimalsIsRefusedNotRounded() throws Exception {
		refused("M-1", "\"feePaid\":100.123", "feePaid");
		refused("M-2", "\"feePaid\":100.004", "feePaid");
		refused("M-3", "\"registrationPaid\":0.005,\"feePaid\":10.00", "registrationPaid");
	}

	@Test
	void aNegativeAmountIsRefused() throws Exception {
		refused("M-4", "\"feePaid\":-1", "feePaid");
		refused("M-5", "\"registrationPaid\":-0.01,\"feePaid\":10.00", "registrationPaid");
	}

	@Test
	void anAmountWithMoreThanTenDigitsBeforeThePointIsRefused() throws Exception {
		refused("M-6", "\"feePaid\":12345678901.00", "feePaid");
	}

	@Test
	void missingAndZeroAmountsTogetherAreRefused() throws Exception {
		refused("M-7", "\"registrationPaid\":0.00,\"feePaid\":0.00", "feePaid");
		refused("M-8", "\"x\":0", "feePaid");
	}

	@Test
	void trailingZerosAreAcceptedAndStoredWithTwoDecimals() throws Exception {
		ReceiptApi.create(client, student.studentId(), ReceiptApi.cash("M-9", TODAY, "100.10"))
			.andExpect(status().isCreated());
		ReceiptApi.create(client, student.studentId(), ReceiptApi.cash("M-10", TODAY, "100.100"))
			.andExpect(status().isCreated());
		ReceiptApi.create(client, student.studentId(), ReceiptApi.cash("M-11", TODAY, "7"))
			.andExpect(status().isCreated());

		assertThat(jdbc.queryForObject("select fee_paid from receipts where receipt_no = 'M-9'", BigDecimal.class)
			.toPlainString()).isEqualTo("100.10");
		assertThat(jdbc.queryForObject("select fee_paid from receipts where receipt_no = 'M-10'", BigDecimal.class)
			.toPlainString()).isEqualTo("100.10");
		assertThat(jdbc.queryForObject("select total from receipts where receipt_no = 'M-11'", BigDecimal.class)
			.toPlainString()).isEqualTo("7.00");
	}

	@Test
	void theLargestAmountThePreviewAcceptsAlsoNeedsToBeBelowWhatIsDue() throws Exception {
		client.post("/api/students/" + student.studentId() + "/fee-collection/preview",
				"{\"receiptType\":\"CURRENT_YEAR\",\"feePaid\":100.123}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("feePaid"));
	}

}
