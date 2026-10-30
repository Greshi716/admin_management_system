package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import com.jayway.jsonpath.JsonPath;

/**
 * Boundary and failure cases from the F3 test plan that the feature tests do not reach: the school-time date at
 * midnight, cheque and mode edge values, money text that is not a number, a one-character reason, the brought-forward
 * edit at exactly the paid total, an empty export and register, and Gujarati text. Synthetic data only; amounts are
 * checked as exact decimals. Cases the plan leaves open (Q1 to Q17) are not asserted here.
 */
@Covers("FR-002")
@Covers("FR-006")
@Covers("FR-012")
@Covers("FR-013")
@Covers("FR-019")
@Covers("FR-020")
@Covers("FR-023")
@Covers("FR-025")
class ReceiptEdgeCasesIT extends IntegrationTest {

	static final String TODAY = "3801-10-01";

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
		earlierYearId = FeeTestData.year(jdbc, "EdgeEarlier", LocalDate.of(3800, 4, 1), 0).yearId();
		setup = FeeTestData.year(jdbc, "EdgeCases", LocalDate.of(3801, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
	}

	@BeforeEach
	void today() throws Exception {
		clock.setDate(LocalDate.of(3801, 10, 1));
		client = SignedInClient.of(mvc);
	}

	Fx student() {
		return FeeTestData.studentWithAccount(jdbc, setup, 958000 + (++students), "36500.00", "500.00");
	}

	private long receipts(Fx s) {
		return jdbc.queryForObject("select count(*) from receipts where student_id = ?", Long.class, s.studentId());
	}

	private static String cheque(String no, String chequeNo, String bank, String chequeDate) {
		return "\"receiptNo\":\"" + no + "\",\"receiptDate\":\"" + TODAY + "\",\"feePaid\":100.00,"
				+ "\"paymentMode\":\"CHEQUE\",\"chequeNo\":\"" + chequeNo + "\",\"bankName\":\"" + bank
				+ "\",\"chequeDate\":\"" + chequeDate + "\"";
	}

	// FR-002 and XC-DATE1: "today" is the school date in Asia/Kolkata, not the UTC date.

	@Test
	void todayIsTheSchoolDateJustAfterMidnightInIndia() throws Exception {
		Fx s = student();
		clock.set(Instant.parse("3801-09-30T20:00:00Z")); // 01:30 on 1 October in Porbandar

		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("ED-1", "3801-10-01", "100.00"))
			.andExpect(status().isCreated());
		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("ED-2", "3801-10-02", "100.00"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("receiptDate"));
		assertThat(receipts(s)).isEqualTo(1);
	}

	@Test
	void todayIsStillYesterdayJustBeforeMidnightInIndia() throws Exception {
		Fx s = student();
		clock.set(Instant.parse("3801-09-30T18:29:00Z")); // 23:59 on 30 September in Porbandar

		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("ED-3", "3801-09-30", "100.00"))
			.andExpect(status().isCreated());
		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("ED-4", "3801-10-01", "100.00"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("receiptDate"));
	}

	@Test
	void aDateThatIsNotARealDateOrNotInIsoFormIsRefusedAndNothingIsSaved() throws Exception {
		Fx s = student();
		for (String date : List.of("3801-02-30", "01-10-3801")) {
			ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("ED-5", date, "100.00"))
				.andExpect(status().is4xxClientError());
		}
		assertThat(receipts(s)).isZero();
	}

	// FR-006: cheque and mode edges.

	@Test
	void aChequeDatedInTheFutureOrLongAgoAndAOneLetterBankAreAccepted() throws Exception {
		Fx s = student();

		ReceiptApi.create(client, s.studentId(), cheque("ED-6", "000123", "X", "3801-12-01"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.chequeNo").value("000123"))
			.andExpect(jsonPath("$.bankName").value("X"))
			.andExpect(jsonPath("$.chequeDate").value("3801-12-01"));
		ReceiptApi.create(client, s.studentId(), cheque("ED-7", "000124", "Test Bank", "3800-01-01"))
			.andExpect(status().isCreated());
	}

	@Test
	void aBlankChequeNumberIsRefusedBesideTheChequeNumber() throws Exception {
		Fx s = student();

		ReceiptApi.create(client, s.studentId(), cheque("ED-8", "   ", "Test Bank", "3801-10-01"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("chequeNo"));
		assertThat(receipts(s)).isZero();
	}

	@Test
	void aPaymentModeThatIsNotExactlyCashOrChequeIsRefusedAndNothingIsSaved() throws Exception {
		Fx s = student();
		for (String mode : List.of("UPI", "cash", "")) {
			ReceiptApi.create(client, s.studentId(),
					"\"receiptNo\":\"ED-9\",\"receiptDate\":\"" + TODAY + "\",\"feePaid\":100.00,\"paymentMode\":\"" + mode
							+ "\"")
				.andExpect(status().is4xxClientError());
		}
		assertThat(receipts(s)).isZero();
	}

	// FR-001: an empty number.

	@Test
	void anEmptyReceiptNumberIsRefusedBesideTheField() throws Exception {
		Fx s = student();

		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("", TODAY, "100.00"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("receiptNo"));
		assertThat(receipts(s)).isZero();
	}

	// FR-023: money that is not a plain two-decimal number is refused, never rounded.

	@Test
	void anAmountThatIsTextOrHasHalfAPaisaIsRefusedAndNothingIsSaved() throws Exception {
		Fx s = student();
		String base = "\"receiptNo\":\"ED-10\",\"receiptDate\":\"" + TODAY + "\",\"paymentMode\":\"CASH\",";

		for (String amount : List.of("\"feePaid\":100.005", "\"feePaid\":\"abc\"", "\"feePaid\":-0.01",
				"\"feePaid\":100.123")) {
			ReceiptApi.create(client, s.studentId(), base + amount).andExpect(status().is4xxClientError());
		}
		assertThat(receipts(s)).isZero();
	}

	/** Plan FR023-F: an amount in exponent notation is refused beside its field and nothing is saved. */
	@Test
	void anAmountSentAsExponentTextIsRefusedAndNothingIsSaved() throws Exception {
		Fx s = student();
		String base = "\"receiptNo\":\"ED-18\",\"receiptDate\":\"" + TODAY + "\",\"paymentMode\":\"CASH\",";

		ReceiptApi.create(client, s.studentId(), base + "\"feePaid\":\"1e3\"")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("feePaid"));
		ReceiptApi.create(client, s.studentId(), base + "\"feePaid\":1E3")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("feePaid"));
		ReceiptApi.create(client, s.studentId(), base + "\"registrationPaid\":\"1e2\",\"feePaid\":100.00")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("registrationPaid"));
		client.post("/api/students/" + s.studentId() + "/fee-collection/preview",
				"{\"receiptType\":\"CURRENT_YEAR\",\"feePaid\":\"1e3\"}")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("feePaid"));
		assertThat(receipts(s)).isZero();
	}

	@Test
	void anAmountWithTwoDecimalsIsStoredExactlyAndShownWithTwoDecimals() throws Exception {
		Fx s = student();

		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("ED-11", TODAY, "100.12"))
			.andExpect(status().isCreated())
			.andExpect(JsonMoney.at("$.feePaid", "100.12"))
			.andExpect(JsonMoney.at("$.total", "100.12"));
		ReceiptApi.create(client, s.studentId(), ReceiptApi.cash("ED-12", TODAY, "0.01"))
			.andExpect(status().isCreated())
			.andExpect(JsonMoney.at("$.total", "0.01"));
	}

	// FR-013 and FR-012 (EC-11): a one-character reason, and paid up to after an earlier cheque is marked.

	@Test
	void aOneCharacterReasonIsAccepted() throws Exception {
		Fx s = student();
		long serial = FeeTestData.chequeReceipt(jdbc, setup, s, "ED-13", LocalDate.of(3801, 6, 1), "5000.00");

		client.post("/api/receipts/" + serial + "/not-cleared",
				"{\"reason\":\"x\",\"balanceVersion\":\"" + ReceiptApi.token(client, s.studentId()) + "\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CHEQUE_NOT_CLEARED"));
	}

	@Test
	void markingAnEarlierChequeAfterALaterCashReceiptMovesPaidUpToToTheRemainingAmount() throws Exception {
		Fx s = student();
		long cheque = FeeTestData.chequeReceipt(jdbc, setup, s, "ED-14", LocalDate.of(3801, 6, 1), "10000.00");
		FeeTestData.cashReceipt(jdbc, setup, s, "ED-15", LocalDate.of(3801, 6, 2), "0", "8250.00");
		client.get("/api/students/" + s.studentId() + "/fees")
			.andExpect(JsonMoney.at("$.balance.pending", "18750.00"));

		client.post("/api/receipts/" + cheque + "/not-cleared",
				"{\"reason\":\"Test returned by bank\",\"balanceVersion\":\"" + ReceiptApi.token(client, s.studentId())
						+ "\"}")
			.andExpect(status().isOk());

		// 8,250.00 of 36,500.00 is 82.5 days, rounded half up to 83: the 83rd day is 22 June.
		client.get("/api/students/" + s.studentId() + "/fees")
			.andExpect(JsonMoney.at("$.balance.pending", "28750.00"))
			.andExpect(jsonPath("$.balance.paidUpTo.date").value("3801-06-22"));
	}

	// FR-025: the edit limit at exactly the paid total.

	@Test
	void broughtForwardCannotGoOneNinetyNinePaiseBelowThePreviousYearsPaidTotalButCanEqualIt() throws Exception {
		Fx s = student();
		jdbc.update("insert into fee_brought_forward (student_id, amount, updated_at) values (?, 3000.00, now())",
				s.studentId());
		client.post("/api/receipts", "{\"studentId\":" + s.studentId() + ",\"receiptType\":\"PREVIOUS_YEARS\","
				+ "\"previousYearId\":" + earlierYearId + ",\"receiptNo\":\"ED-16\",\"receiptDate\":\"" + TODAY
				+ "\",\"feePaid\":3000.00,\"paymentMode\":\"CASH\",\"balanceVersion\":\""
				+ ReceiptApi.token(client, s.studentId()) + "\"}")
			.andExpect(status().isCreated());

		edit(s, "2999.99").andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("BELOW_PREVIOUS_YEARS_PAID"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
		edit(s, "3000.00").andExpect(status().isOk()).andExpect(JsonMoney.at("$.amount", "3000.00"));
	}

	private ResultActions edit(Fx s, String amount) throws Exception {
		String body = client.get("/api/students/" + s.studentId() + "/fees").andReturn().getResponse()
			.getContentAsString();
		long version = ((Number) JsonPath.read(body, "$.broughtForward.version")).longValue();
		return client.put("/api/students/" + s.studentId() + "/brought-forward",
				"{\"amount\":" + amount + ",\"note\":null,\"version\":" + version + "}");
	}

	// FR-019 and FR-020: nothing found.

	@Test
	void aRegisterSearchWithNoMatchIsAnEmptyPageNotAnError() throws Exception {
		client.post("/api/receipts/search", "{\"first\":0,\"rows\":25,\"globalFilter\":\"NO-SUCH-RECEIPT-ZZ\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rows.length()").value(0));
	}

	@Test
	void aStatusFilterThatIsNotAStatusNeverCausesAServerError() throws Exception {
		client.post("/api/receipts/search", "{\"first\":0,\"rows\":25,\"globalFilter\":\"ED-\",\"filters\":"
				+ "{\"status\":{\"value\":\"BAD\",\"matchMode\":\"equals\"}}}")
			.andExpect(result -> assertThat(result.getResponse().getStatus()).isLessThan(500));
	}

	@Test
	void anExportWithNoRowsHasTheHeadersOnTheMainSheetAndAnEmptyDayWiseSheet() throws Exception {
		byte[] file = client.post("/api/receipts/export",
				"{\"first\":0,\"rows\":25,\"globalFilter\":\"NO-SUCH-RECEIPT-ZZ\"}")
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
			assertThat(workbook.getSheetAt(0).getLastRowNum()).isZero();
			assertThat(workbook.getSheetAt(0).getRow(0).getLastCellNum()).isGreaterThan((short) 5);
			assertThat(workbook.getSheet("Day-wise totals")).isNotNull();
			assertThat(workbook.getSheet("Day-wise totals").getLastRowNum()).isLessThanOrEqualTo(0);
		}
	}

	// XC-GU1: Gujarati text is stored without loss (checked in the database, not through a response charset).

	@Test
	void gujaratiBankNameAndReasonAreStoredUnchanged() throws Exception {
		Fx s = student();
		String bank = "ટેસ્ટ બેંક";
		String reason = "ટેસ્ટ કારણ";

		long serial = ReceiptApi
			.serial(ReceiptApi.create(client, s.studentId(), cheque("ED-17", "000200", bank, "3801-10-01"))
				.andExpect(status().isCreated()));
		client.post("/api/receipts/" + serial + "/not-cleared",
				"{\"reason\":\"" + reason + "\",\"balanceVersion\":\"" + ReceiptApi.token(client, s.studentId()) + "\"}")
			.andExpect(status().isOk());

		assertThat(jdbc.queryForObject("select bank_name from receipts where serial_no = ?", String.class, serial))
			.isEqualTo(bank);
		assertThat(jdbc.queryForObject("select reason from receipt_marks where receipt_serial_no = ?", String.class,
				serial))
			.isEqualTo(reason);
	}

}
