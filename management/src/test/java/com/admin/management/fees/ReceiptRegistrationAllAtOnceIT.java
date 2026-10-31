package com.admin.management.fees;

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

/**
 * Tests that registration fee is paid all-at-once (FR-027).
 * When registration amount is above zero, it must equal registration fee still due.
 */
@Covers("FR-027")
class ReceiptRegistrationAllAtOnceIT extends IntegrationTest {

	static final String TODAY = "3632-10-01";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	Setup setup;

	SignedInClient client;

	int students;

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "RegAllOnceIT", LocalDate.of(3632, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
	}

	@BeforeEach
	void today() throws Exception {
		clock.setDate(LocalDate.of(3632, 10, 1));
		client = SignedInClient.of(mvc);
	}

	Fx student(String fee, String registration) {
		return FeeTestData.studentWithAccount(jdbc, setup, 988000 + (++students), fee, registration);
	}

	@Test
	void exactPaymentAccepted() throws Exception {
		Fx s = student("10000.00", "500.00");

		ReceiptApi.create(client, s.studentId(),
				"\"receiptNo\":\"REG-1\",\"receiptDate\":\"" + TODAY + "\",\"registrationPaid\":500.00,"
						+ "\"feePaid\":1000.00,\"paymentMode\":\"CASH\"")
			.andExpect(status().isCreated())
			.andExpect(JsonMoney.at("$.registrationPaid", "500.00"));
	}

	@Test
	void onePaisaLessRefused() throws Exception {
		Fx s = student("10000.00", "500.00");

		ReceiptApi.create(client, s.studentId(),
				"\"receiptNo\":\"REG-2\",\"receiptDate\":\"" + TODAY + "\",\"registrationPaid\":499.99,"
						+ "\"feePaid\":1000.00,\"paymentMode\":\"CASH\"")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REGISTRATION_PART_PAYMENT"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("registrationPaid"))
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("500.00")));
	}

	@Test
	void aboveDueRefusedAsOverpayment() throws Exception {
		Fx s = student("10000.00", "500.00");

		ReceiptApi.create(client, s.studentId(),
				"\"receiptNo\":\"REG-3\",\"receiptDate\":\"" + TODAY + "\",\"registrationPaid\":500.01,"
						+ "\"feePaid\":1000.00,\"paymentMode\":\"CASH\"")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OVERPAYMENT"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("registrationPaid"));
	}

	@Test
	void studentWithOldPartPaymentCanPayExactRemainder() throws Exception {
		Fx s = student("10000.00", "500.00");
		// Seed an old part-payment receipt of 200.00
		FeeTestData.cashReceipt(jdbc, setup, s, "OLD-REG", LocalDate.parse(TODAY).minusDays(10), "200.00", "0.00");

		// 300.00 remains due. 299.99 is refused
		ReceiptApi.create(client, s.studentId(),
				"\"receiptNo\":\"REG-4\",\"receiptDate\":\"" + TODAY + "\",\"registrationPaid\":299.99,"
						+ "\"feePaid\":0.00,\"paymentMode\":\"CASH\"")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REGISTRATION_PART_PAYMENT"))
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("300.00")));

		// 300.00 is accepted
		ReceiptApi.create(client, s.studentId(),
				"\"receiptNo\":\"REG-5\",\"receiptDate\":\"" + TODAY + "\",\"registrationPaid\":300.00,"
						+ "\"feePaid\":0.00,\"paymentMode\":\"CASH\"")
			.andExpect(status().isCreated())
			.andExpect(JsonMoney.at("$.registrationPaid", "300.00"));
	}

	@Test
	void yearFeeOnlyReceiptAcceptedWhileRegistrationDue() throws Exception {
		Fx s = student("10000.00", "500.00");

		ReceiptApi.create(client, s.studentId(),
				"\"receiptNo\":\"REG-6\",\"receiptDate\":\"" + TODAY + "\",\"registrationPaid\":0.00,"
						+ "\"feePaid\":2000.00,\"paymentMode\":\"CASH\"")
			.andExpect(status().isCreated())
			.andExpect(JsonMoney.at("$.registrationPaid", "0.00"))
			.andExpect(JsonMoney.at("$.feePaid", "2000.00"));
	}

}
