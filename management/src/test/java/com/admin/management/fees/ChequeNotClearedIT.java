package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.admin.management.support.JsonMoney;
import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Fx;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.ReceiptApi;
import com.admin.management.support.SignedInClient;

/**
 * Marking a cheque receipt "Cheque not cleared" (FR-013): a required reason, the amount added back, "paid up to"
 * recalculated, one audit entry in the same transaction, and the receipt row itself unchanged (FR-021, FR-012). The
 * year is a 365-day year starting 1 April, so 10,000.00 of 36,500.00 is 100 days (9 July).
 */
@Covers("FR-013")
@Covers("FR-021")
@Covers("FR-012")
class ChequeNotClearedIT extends IntegrationTest {

	static final LocalDate PAID = LocalDate.of(3911, 6, 1);

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoSpyBean
	FeesAudit feesAudit;

	Setup setup;

	SignedInClient client;

	int students;

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "NotCleared", LocalDate.of(3911, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
	}

	@BeforeEach
	void today() throws Exception {
		clock.setDate(LocalDate.of(3911, 10, 1));
		client = SignedInClient.of(mvc);
	}

	@AfterEach
	void clean() {
		reset(feesAudit);
	}

	Fx student() {
		return FeeTestData.studentWithAccount(jdbc, setup, 941000 + (++students), "36500.00", "500.00");
	}

	private ResultActions mark(Fx s, long serial, String reason) throws Exception {
		return markWithToken(s, serial, ReceiptApi.token(client, s.studentId()), reason);
	}

	private ResultActions markWithToken(Fx s, long serial, String token, String reason) throws Exception {
		return client.post("/api/receipts/" + serial + "/not-cleared",
				"{\"reason\":\"" + reason + "\",\"balanceVersion\":\"" + token + "\"}");
	}

	private BigDecimal pending(Fx s) throws Exception {
		String body = client.get("/api/students/" + s.studentId() + "/fees")
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return new BigDecimal(String.valueOf(com.jayway.jsonpath.JsonPath.<Object>read(body, "$.balance.pending")));
	}

	private List<Map<String, Object>> entries(long serial) {
		return jdbc.queryForList("select * from audit_log where entity = ? order by id", "Receipt#" + serial);
	}

	private long markCount(long serial) {
		return jdbc.queryForObject("select count(*) from receipt_marks where receipt_serial_no = ?", Long.class,
				serial);
	}

	@Test
	void aMissingBlankOrTooLongReasonIsRefusedBesideTheReasonAndNothingIsSaved() throws Exception {
		Fx s = student();
		long serial = FeeTestData.chequeReceipt(jdbc, setup, s, "NC-1", PAID, "5000.00");

		for (String reason : List.of("", "   ", "x".repeat(201))) {
			mark(s, serial, reason).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
		}

		assertThat(markCount(serial)).isZero();
		assertThat(entries(serial)).isEmpty();
	}

	@Test
	void aTwoHundredCharacterReasonIsAccepted() throws Exception {
		Fx s = student();
		long serial = FeeTestData.chequeReceipt(jdbc, setup, s, "NC-2", PAID, "5000.00");

		mark(s, serial, "y".repeat(200)).andExpect(status().isOk());
	}

	@Test
	void markingAddsTheAmountBackAndRecalculatesPaidUpTo() throws Exception {
		Fx s = student();
		FeeTestData.cashReceipt(jdbc, setup, s, "NC-3", PAID, "0", "10000.00");
		long cheque = FeeTestData.chequeReceipt(jdbc, setup, s, "NC-4", PAID.plusDays(1), "8250.00");
		assertThat(pending(s)).isEqualByComparingTo("18750.00");

		mark(s, cheque, "Bounced, insufficient funds").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CHEQUE_NOT_CLEARED"))
			.andExpect(jsonPath("$.statusReason").value("Bounced, insufficient funds"))
			.andExpect(jsonPath("$.markedAt").isNotEmpty());

		assertThat(pending(s)).isEqualByComparingTo("27000.00");
		client.get("/api/students/" + s.studentId() + "/fees")
			.andExpect(jsonPath("$.balance.paidUpTo.date").value("3911-07-09"));
	}

	@Test
	void theReasonIsTrimmed() throws Exception {
		Fx s = student();
		long serial = FeeTestData.chequeReceipt(jdbc, setup, s, "NC-5", PAID, "100.00");

		mark(s, serial, "  Stopped by the payer  ").andExpect(status().isOk())
			.andExpect(jsonPath("$.statusReason").value("Stopped by the payer"));
	}

	@Test
	void aCashReceiptCannotBeMarked() throws Exception {
		Fx s = student();
		long serial = FeeTestData.cashReceipt(jdbc, setup, s, "NC-6", PAID, "0", "1000.00");

		mark(s, serial, "Not a cheque").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NOT_A_CHEQUE"));

		assertThat(markCount(serial)).isZero();
	}

	@Test
	void aSecondMarkAndAVoidedReceiptAreRefusedAsAlreadyMarked() throws Exception {
		Fx s = student();
		long cheque = FeeTestData.chequeReceipt(jdbc, setup, s, "NC-7", PAID, "1000.00");
		mark(s, cheque, "Bounced").andExpect(status().isOk());

		mark(s, cheque, "Again").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_MARKED"));

		long voided = FeeTestData.chequeReceipt(jdbc, setup, s, "NC-8", PAID, "1000.00");
		jdbc.update("""
				insert into receipt_marks (receipt_serial_no, kind, reason, amount_added_back, marked_by, marked_at)
				select serial_no, 'DEVELOPER_VOID', 'Entered twice', total, null, now() from receipts where serial_no = ?
				""", voided);
		mark(s, voided, "Bounced").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_MARKED"));

		assertThat(markCount(cheque)).isEqualTo(1);
		assertThat(markCount(voided)).isEqualTo(1);
	}

	@Test
	void aStaleBalanceVersionIsRefusedWithTheCurrentContextAndNothingIsSaved() throws Exception {
		Fx s = student();
		long cheque = FeeTestData.chequeReceipt(jdbc, setup, s, "NC-9", PAID, "1000.00");
		String stale = ReceiptApi.token(client, s.studentId());
		FeeTestData.cashReceipt(jdbc, setup, s, "NC-10", PAID, "0", "10.00");

		markWithToken(s, cheque, stale, "Bounced").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BALANCE_CHANGED"))
			.andExpect(jsonPath("$.current.balanceVersion").isNotEmpty());

		assertThat(markCount(cheque)).isZero();
	}

	@Test
	void anUnknownReceiptIsNotFound() throws Exception {
		Fx s = student();

		markWithToken(s, 987654321L, ReceiptApi.token(client, s.studentId()), "Bounced").andExpect(status().isNotFound());
	}

	@Test
	void oneAuditEntryInAreaFeesHasTheReasonTheAmountAndPaidUpToBeforeAndAfter() throws Exception {
		Fx s = student();
		FeeTestData.cashReceipt(jdbc, setup, s, "NC-11", PAID, "0", "10000.00");
		long cheque = FeeTestData.chequeReceipt(jdbc, setup, s, "NC-12", PAID.plusDays(1), "8250.00");

		mark(s, cheque, "Bounced").andExpect(status().isOk());

		List<Map<String, Object>> found = entries(cheque);
		assertThat(found).hasSize(1);
		Map<String, Object> entry = found.get(0);
		assertThat(entry.get("area")).isEqualTo("Fees");
		assertThat(entry.get("action")).isEqualTo("Edit");
		assertThat(entry.get("user_id")).isNotNull();
		assertThat(String.valueOf(entry.get("reason"))).isEqualTo("Bounced");
		String changes = String.valueOf(entry.get("changes"));
		assertThat(changes).contains("\"amountAddedBack\": \"8250.00\"")
			.contains("\"paidUpToBefore\": \"3911-09-30\"")
			.contains("\"paidUpToAfter\": \"3911-07-09\"")
			.contains("CHEQUE_NOT_CLEARED")
			.doesNotContain("*");
	}

	@Test
	void theReceiptRowIsUnchangedByTheMark() throws Exception {
		Fx s = student();
		long cheque = FeeTestData.chequeReceipt(jdbc, setup, s, "NC-13", PAID, "3000.00");
		Map<String, Object> before = jdbc.queryForMap("select * from receipts where serial_no = ?", cheque);

		mark(s, cheque, "Bounced").andExpect(status().isOk());

		assertThat(jdbc.queryForMap("select * from receipts where serial_no = ?", cheque)).isEqualTo(before);
	}

	@Test
	void aMarkedReceiptKeepsItsNumberButANewNumberIsAccepted() throws Exception {
		Fx s = student();
		long cheque = FeeTestData.chequeReceipt(jdbc, setup, s, "NC-14", PAID, "1000.00");
		mark(s, cheque, "Bounced").andExpect(status().isOk());

		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("NC-14", "3911-10-01", "1000.00"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_RECEIPT_NO"));
		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("NC-15", "3911-10-01", "1000.00"))
			.andExpect(status().isCreated());
	}

	@Test
	void aPreviousYearsChequeCanBeMarkedAndEarlierPendingRisesAndPaidUpToStaysEmpty() throws Exception {
		Fx s = student();
		jdbc.update("insert into fee_brought_forward (student_id, amount, updated_at) values (?, 3000.00, now())",
				s.studentId());
		long cheque = jdbc.queryForObject("""
				insert into receipts (receipt_no, receipt_date, academic_year_id, student_id, enrollment_id, student_name,
				    gr_no, receipt_type, registration_paid, fee_paid, total, total_words, payment_mode, cheque_no,
				    bank_name, cheque_date, created_by, created_at)
				values ('NC-16', ?, ?, ?, null, 'Test Student', ?, 'PREVIOUS_YEARS', 0, 1000.00, 1000.00, 'Test words',
				    'CHEQUE', 'CQNC-16', 'Test Bank', ?, ?, now())
				returning serial_no
				""", Long.class, Date.valueOf(PAID), setup.yearId(), s.studentId(), s.grNo(), Date.valueOf(PAID),
				FeeTestData.adminId(jdbc));
		client.get("/api/students/" + s.studentId() + "/fees")
			.andExpect(JsonMoney.at("$.balance.earlierYearsPending", "2000.00"));

		mark(s, cheque, "Bounced").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHEQUE_NOT_CLEARED"));

		client.get("/api/students/" + s.studentId() + "/fees")
			.andExpect(JsonMoney.at("$.balance.earlierYearsPending", "3000.00"));
		assertThat(String.valueOf(entries(cheque).get(0).get("changes"))).contains("\"amountAddedBack\": \"1000.00\"");
	}

	@Test
	void aFailingAuditWriteLeavesNoMark() throws Exception {
		Fx s = student();
		long cheque = FeeTestData.chequeReceipt(jdbc, setup, s, "NC-17", PAID, "1000.00");
		String token = ReceiptApi.token(client, s.studentId());
		doThrow(new IllegalStateException("audit is down")).when(feesAudit)
			.chequeNotCleared(any(), any(), any(), any(), any());

		assertThatThrownBy(() -> markWithToken(s, cheque, token, "Bounced")).hasMessageContaining("audit is down");

		assertThat(markCount(cheque)).isZero();
	}

	@Test
	void thereIsNoWayToUnmarkOrEditAndRequestsWithoutASessionOrCsrfAreRefused() throws Exception {
		Fx s = student();
		long cheque = FeeTestData.chequeReceipt(jdbc, setup, s, "NC-18", PAID, "1000.00");
		String body = "{\"reason\":\"Bounced\",\"balanceVersion\":\"r0.m0.a0.b0\"}";

		mvc.perform(post("/api/receipts/" + cheque + "/not-cleared").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(body)).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/receipts/" + cheque + "/not-cleared").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().is4xxClientError());
		client.delete("/api/receipts/" + cheque + "/not-cleared").andExpect(status().is4xxClientError());
		client.put("/api/receipts/" + cheque + "/not-cleared", body).andExpect(status().is4xxClientError());

		assertThat(markCount(cheque)).isZero();
	}

}
