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
import org.springframework.test.web.servlet.ResultActions;

import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.ReceiptApi;
import com.admin.management.support.SignedInClient;
import com.jayway.jsonpath.JsonPath;

/**
 * The brought-forward amount cannot be set below what previous-years receipts already total (FR-025). Quickstart 11:
 * 3,000.00 brought forward, 2,000.00 paid: 1,500.00 is refused showing 2,000.00, 2,000.00 is accepted. Only valid
 * receipts count, so a cheque marked not cleared lowers the limit.
 */
@Covers("FR-025")
class BroughtForwardLimitIT extends IntegrationTest {

	static final String TODAY = "3941-10-01";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	Setup setup;

	long earlierYearId;

	SignedInClient client;

	int students;

	@BeforeAll
	void seed() {
		earlierYearId = FeeTestData.year(jdbc, "BfLimitEarlier", LocalDate.of(3940, 4, 1), 0).yearId();
		setup = FeeTestData.year(jdbc, "BfLimitCurrent", LocalDate.of(3941, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
	}

	@BeforeEach
	void today() throws Exception {
		clock.setDate(LocalDate.of(3941, 10, 1));
		client = SignedInClient.of(mvc);
	}

	Fx student() {
		Fx s = FeeTestData.studentWithAccount(jdbc, setup, 943000 + (++students), "36500.00", null);
		jdbc.update("insert into fee_brought_forward (student_id, amount, updated_at) values (?, 3000.00, now())",
				s.studentId());
		return s;
	}

	private long version(Fx s) throws Exception {
		String body = client.get("/api/students/" + s.studentId() + "/fees").andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.broughtForward.version")).longValue();
	}

	private ResultActions edit(Fx s, String amount) throws Exception {
		return client.put("/api/students/" + s.studentId() + "/brought-forward",
				"{\"amount\":" + amount + ",\"note\":null,\"version\":" + version(s) + "}");
	}

	private long pay(Fx s, String receiptNo, String amount, boolean cheque) throws Exception {
		String mode = cheque ? "\"CHEQUE\",\"chequeNo\":\"555\",\"bankName\":\"Test Bank\",\"chequeDate\":\"" + TODAY + "\""
				: "\"CASH\"";
		return ReceiptApi.serial(client.post("/api/receipts", "{\"studentId\":" + s.studentId()
				+ ",\"receiptType\":\"PREVIOUS_YEARS\",\"previousYearId\":" + earlierYearId + ",\"receiptNo\":\"" + receiptNo
				+ "\",\"receiptDate\":\"" + TODAY + "\",\"feePaid\":" + amount + ",\"paymentMode\":" + mode
				+ ",\"balanceVersion\":\"" + ReceiptApi.token(client, s.studentId()) + "\"}")
			.andExpect(status().isCreated()));
	}

	@Test
	void anAmountBelowWhatPreviousYearsReceiptsTotalIsRefusedShowingTheTotal() throws Exception {
		Fx s = student();
		pay(s, "BL-1", "2000.00", false);

		edit(s, "1500.00").andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("BELOW_PREVIOUS_YEARS_PAID"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("amount"))
			.andExpect(jsonPath("$.fieldErrors[0].message").value(org.hamcrest.Matchers.containsString("2000.00")));

		edit(s, "2000.00").andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(2000.00));
		client.get("/api/students/" + s.studentId() + "/fees")
			.andExpect(jsonPath("$.balance.earlierYearsPending").value(0.00));
	}

	@Test
	void onlyValidReceiptsCountSoANotClearedChequeLowersTheLimit() throws Exception {
		Fx s = student();
		pay(s, "BL-2", "1000.00", false);
		long cheque = pay(s, "BL-3", "1000.00", true);
		edit(s, "1500.00").andExpect(status().isUnprocessableEntity());

		client.post("/api/receipts/" + cheque + "/not-cleared", "{\"reason\":\"Bounced\",\"balanceVersion\":\""
				+ ReceiptApi.token(client, s.studentId()) + "\"}").andExpect(status().isOk());

		edit(s, "1500.00").andExpect(status().isOk());
	}

	@Test
	void aStudentWithNoPreviousYearsReceiptsCanStillChangeTheAmountFreely() throws Exception {
		Fx s = student();

		edit(s, "0").andExpect(status().isOk());
	}

}
