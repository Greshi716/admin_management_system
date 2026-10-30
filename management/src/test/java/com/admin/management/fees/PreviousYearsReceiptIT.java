package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
import com.admin.management.support.JsonMoney;
import com.admin.management.support.ReceiptApi;
import com.admin.management.support.SignedInClient;

/**
 * A previous-years receipt pays the student's brought-forward pending from one earlier year (FR-004), is limited to
 * what is still pending (FR-005) and is accepted for a Left student or one with no class record this year (FR-007).
 * Quickstart 9 and 10: 3,000.00 brought forward, 2,000.00 paid, then 1,500.00 refused with 1,000.00 still due.
 */
@Covers("FR-004")
@Covers("FR-005")
@Covers("FR-007")
class PreviousYearsReceiptIT extends IntegrationTest {

	static final String TODAY = "3931-10-01";

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
		earlierYearId = FeeTestData.year(jdbc, "PrevEarlier", LocalDate.of(3930, 4, 1), 0).yearId();
		setup = FeeTestData.year(jdbc, "PrevCurrent", LocalDate.of(3931, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
	}

	@BeforeEach
	void today() throws Exception {
		clock.setDate(LocalDate.of(3931, 10, 1));
		client = SignedInClient.of(mvc);
	}

	/** A student with 3,000.00 brought forward and a fee of 36,500.00 plus 500.00 registration. */
	Fx student() {
		Fx s = FeeTestData.studentWithAccount(jdbc, setup, 942000 + (++students), "36500.00", "500.00");
		jdbc.update("insert into fee_brought_forward (student_id, amount, updated_at) values (?, 3000.00, now())",
				s.studentId());
		return s;
	}

	private String previous(String receiptNo, String amount, long yearId) {
		return "\"receiptType\":\"PREVIOUS_YEARS\",\"previousYearId\":" + yearId + ",\"receiptNo\":\"" + receiptNo
				+ "\",\"receiptDate\":\"" + TODAY + "\",\"feePaid\":" + amount + ",\"paymentMode\":\"CASH\"";
	}

	private ResultActions save(Fx s, String fields) throws Exception {
		return client.post("/api/receipts", "{\"studentId\":" + s.studentId() + ",\"balanceVersion\":\""
				+ ReceiptApi.token(client, s.studentId()) + "\"," + fields + "}");
	}

	@Test
	void aCurrentYearReceiptIsRefusedWhileEarlierYearsArePending() throws Exception {
		Fx s = student();

		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("PY-1", TODAY, "100.00")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BROUGHT_FORWARD_PENDING"));
	}

	@Test
	void aPreviousYearsReceiptIsSavedThenTheNextIsLimitedToWhatIsStillPending() throws Exception {
		Fx s = student();

		save(s, previous("PY-2", "2000.00", earlierYearId)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.receiptType").value("PREVIOUS_YEARS"))
			.andExpect(JsonMoney.at("$.registrationPaid", "0.00"))
			.andExpect(JsonMoney.at("$.feePaid", "2000.00"))
			.andExpect(JsonMoney.at("$.total", "2000.00"))
			.andExpect(jsonPath("$.academicYear").value("PrevEarlier 3930-3931"))
			.andExpect(jsonPath("$.totalWords").value("Two Thousand Rupees Only"));

		client.get("/api/students/" + s.studentId() + "/fees")
			.andExpect(JsonMoney.at("$.balance.earlierYearsPending", "1000.00"))
			.andExpect(JsonMoney.at("$.balance.previousYearsPaid", "2000.00"));

		save(s, previous("PY-3", "1500.00", earlierYearId)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OVERPAYMENT"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("feePaid"))
			.andExpect(JsonMoney.at("$.current.due", "1000.00"));
		assertThat(jdbc.queryForObject("select count(*) from receipts where receipt_no = 'PY-3'", Long.class)).isZero();
	}

	@Test
	void theReceiptHasNoClassRecordButTheSnapshotOfTheShownRecordAndNoPaidUpTo() throws Exception {
		Fx s = student();
		long serial = ReceiptApi.serial(save(s, previous("PY-4", "500.00", earlierYearId)));

		Map<String, Object> row = jdbc.queryForMap("select * from receipts where serial_no = ?", serial);
		assertThat(row.get("enrollment_id")).isNull();
		assertThat(row.get("academic_year_id")).isEqualTo(earlierYearId);
		assertThat(row.get("standard_id")).isEqualTo(setup.standard(0));
		assertThat(row.get("registration_paid")).hasToString("0.00");
		String body = client.get("/api/receipts/" + serial).andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("paidUpTo");
	}

	@Test
	void aStudentWithNoEarlierPendingCannotPayThatType() throws Exception {
		Fx s = FeeTestData.studentWithAccount(jdbc, setup, 942900 + (++students), "36500.00", null);

		save(s, previous("PY-5", "100.00", earlierYearId)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NO_EARLIER_PENDING"));
	}

	@Test
	void theYearMustBeBeforeTheCurrentYearAndTheRowsFollowTheRules() throws Exception {
		Fx s = student();

		save(s, previous("PY-6", "100.00", setup.yearId())).andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("previousYearId"));
		save(s, previous("PY-6", "100.00", 987654321L)).andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("previousYearId"));
		save(s, "\"receiptType\":\"PREVIOUS_YEARS\",\"receiptNo\":\"PY-6\",\"receiptDate\":\"" + TODAY
				+ "\",\"feePaid\":100.00,\"paymentMode\":\"CASH\"").andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("previousYearId"));
		save(s, previous("PY-6", "100.00", earlierYearId).replace("\"feePaid\"", "\"registrationPaid\":10.00,\"feePaid\""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("registrationPaid"));
		save(s, previous("PY-6", "0", earlierYearId)).andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("feePaid"));
		save(s, previous("PY-6", "100.123", earlierYearId)).andExpect(status().isUnprocessableEntity());

		assertThat(jdbc.queryForObject("select count(*) from receipts where receipt_no = 'PY-6'", Long.class)).isZero();
	}

	@Test
	void aLeftStudentAndAStudentWithNoClassRecordThisYearCanPay() throws Exception {
		Fx left = student();
		jdbc.update("update students set status = 'Left' where id = ?", left.studentId());
		save(left, previous("PY-7", "100.00", earlierYearId)).andExpect(status().isCreated());

		Fx none = student();
		jdbc.update("delete from fee_accounts where enrollment_id = ?", none.enrollmentId());
		jdbc.update("delete from student_enrollments where id = ?", none.enrollmentId());
		save(none, previous("PY-8", "100.00", earlierYearId)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.standardName").doesNotExist());
	}

	@Test
	void aNotClearedPreviousYearsChequeReopensTheOrderRule() throws Exception {
		Fx s = student();
		long serial = ReceiptApi.serial(save(s, previous("PY-9", "3000.00", earlierYearId)
			.replace("\"CASH\"", "\"CHEQUE\",\"chequeNo\":\"123456\",\"bankName\":\"Test Bank\",\"chequeDate\":\"" + TODAY
					+ "\"")));
		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("PY-10", TODAY, "100.00")).andExpect(status().isCreated());

		client.post("/api/receipts/" + serial + "/not-cleared", "{\"reason\":\"Bounced\",\"balanceVersion\":\""
				+ ReceiptApi.token(client, s.studentId()) + "\"}").andExpect(status().isOk());

		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("PY-11", TODAY, "100.00")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BROUGHT_FORWARD_PENDING"));
	}

	@Test
	void thePreviewShowsTheBalanceAfterAPreviousYearsReceiptAndTheAuditEntryHoldsTheYear() throws Exception {
		Fx s = student();
		client.post("/api/students/" + s.studentId() + "/fee-collection/preview", "{\"receiptType\":\"PREVIOUS_YEARS\","
				+ "\"previousYearId\":" + earlierYearId + ",\"feePaid\":1200.00}")
			.andExpect(status().isOk())
			.andExpect(JsonMoney.at("$.total", "1200.00"))
			.andExpect(JsonMoney.at("$.balanceAfter.earlierYearsPending", "1800.00"));

		long serial = ReceiptApi.serial(save(s, previous("PY-12", "1200.00", earlierYearId)));
		List<Map<String, Object>> entries = jdbc.queryForList("select * from audit_log where entity = ?",
				"Receipt#" + serial);
		assertThat(entries).hasSize(1);
		assertThat(String.valueOf(entries.get(0).get("changes"))).contains("PREVIOUS_YEARS").contains("1200.00");
	}

}
