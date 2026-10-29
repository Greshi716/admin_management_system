package com.admin.management.students;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import com.admin.management.support.SignedInClient;
import com.jayway.jsonpath.JsonPath;

/**
 * The Students list columns Paid, Pending, Earlier years pending, Paid up to (with its state) and Fee status, read
 * through the two views of the fees module (FR-015). The year is a 365-day year starting 1 April: 10,000.00 of 36,500.00
 * is 100 days, so 9 July.
 */
@Covers("FR-015")
class StudentListFeeColumnsIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	Setup setup;

	SignedInClient client;

	Fx paidPart; // registration 500.00 and year fee 10,000.00

	Fx unpaid;

	Fx fullyPaid;

	Fx registrationOnly;

	Fx markedCheque;

	Fx noFee;

	Fx earlierOpen; // year fully paid, brought forward 3,000.00 unpaid

	Fx earlierPartly; // year unpaid, brought forward 3,000.00 with 1,000.00 paid

	Fx everythingPaid;

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "ListFee", LocalDate.of(3921, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
		paidPart = FeeTestData.studentWithAccount(jdbc, setup, 392101, "36500.00", "500.00");
		FeeTestData.cashReceipt(jdbc, setup, paidPart, "LF-1", LocalDate.of(3921, 6, 1), "500.00", "10000.00");
		unpaid = FeeTestData.studentWithAccount(jdbc, setup, 392102, "36500.00", "500.00");
		fullyPaid = FeeTestData.studentWithAccount(jdbc, setup, 392103, "36500.00", "500.00");
		FeeTestData.cashReceipt(jdbc, setup, fullyPaid, "LF-2", LocalDate.of(3921, 6, 1), "500.00", "36500.00");
		registrationOnly = FeeTestData.studentWithAccount(jdbc, setup, 392104, "36500.00", "500.00");
		FeeTestData.cashReceipt(jdbc, setup, registrationOnly, "LF-3", LocalDate.of(3921, 6, 1), "500.00", "0");
		markedCheque = FeeTestData.studentWithAccount(jdbc, setup, 392105, "36500.00", "500.00");
		FeeTestData.markNotCleared(jdbc, FeeTestData.chequeReceipt(jdbc, setup, markedCheque, "LF-4",
				LocalDate.of(3921, 6, 1), "5000.00"), "Bounced");
		noFee = FeeTestData.studentWithAccount(jdbc, setup, 392106, "1000.00", null);
		jdbc.update("update fee_accounts set amount = null where enrollment_id = ?", noFee.enrollmentId());
		earlierOpen = FeeTestData.studentWithAccount(jdbc, setup, 392107, "36500.00", "500.00");
		FeeTestData.cashReceipt(jdbc, setup, earlierOpen, "LF-5", LocalDate.of(3921, 6, 1), "500.00", "36500.00");
		broughtForward(earlierOpen, "3000.00");
		earlierPartly = FeeTestData.studentWithAccount(jdbc, setup, 392108, "36500.00", "500.00");
		broughtForward(earlierPartly, "3000.00");
		previousYearsReceipt(earlierPartly, "LF-6", "1000.00");
		everythingPaid = FeeTestData.studentWithAccount(jdbc, setup, 392109, "36500.00", "500.00");
		FeeTestData.cashReceipt(jdbc, setup, everythingPaid, "LF-7", LocalDate.of(3921, 6, 1), "500.00", "36500.00");
		broughtForward(everythingPaid, "3000.00");
		previousYearsReceipt(everythingPaid, "LF-8", "3000.00");
	}

	@BeforeEach
	void today() throws Exception {
		clock.setDate(LocalDate.of(3921, 10, 1));
		client = SignedInClient.of(mvc);
	}

	private void broughtForward(Fx s, String amount) {
		jdbc.update("insert into fee_brought_forward (student_id, amount, updated_at) values (?, ?, now())",
				s.studentId(), new BigDecimal(amount));
	}

	private void previousYearsReceipt(Fx s, String receiptNo, String amount) {
		BigDecimal fee = new BigDecimal(amount);
		jdbc.update("""
				insert into receipts (receipt_no, receipt_date, academic_year_id, student_id, enrollment_id, student_name,
				    gr_no, receipt_type, registration_paid, fee_paid, total, total_words, payment_mode, created_by,
				    created_at)
				values (?, ?, ?, ?, null, 'Test Student', ?, 'PREVIOUS_YEARS', 0, ?, ?, 'Test words', 'CASH', ?, now())
				""", receiptNo, Date.valueOf(LocalDate.of(3921, 6, 1)), setup.yearId(), s.studentId(), s.grNo(), fee,
				fee, FeeTestData.adminId(jdbc));
	}

	private String request(String extra) {
		return "{\"first\":0,\"rows\":25,\"globalFilter\":\"gr-3921\"" + (extra.isEmpty() ? "" : "," + extra) + "}";
	}

	private List<Map<String, Object>> rows(String extra) throws Exception {
		String body = client.post("/api/students/search", request(extra))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.rows");
	}

	private Map<String, Object> row(Fx s) throws Exception {
		return rows("").stream().filter(r -> s.grNo().equals(r.get("grNo"))).findFirst().orElseThrow();
	}

	private static BigDecimal money(Object value) {
		return value == null ? null : new BigDecimal(String.valueOf(value));
	}

	private void assertMoney(Object actual, String expected) {
		assertThat(money(actual)).isEqualByComparingTo(expected);
	}

	@Test
	void aPartlyPaidStudentShowsPaidPendingAndTheLastPaidDay() throws Exception {
		Map<String, Object> r = row(paidPart);

		assertMoney(r.get("paid"), "10500.00");
		assertMoney(r.get("pending"), "26500.00");
		assertMoney(r.get("earlierPending"), "0.00");
		assertThat(r.get("paidUpTo")).isEqualTo("3921-07-09");
		assertThat(r.get("paidUpToState")).isEqualTo("DATE");
		assertThat(r.get("feeStatus")).isEqualTo("Partly paid");
	}

	@Test
	void anUnpaidStudentIsNothingPaidAndAFullyPaidOneIsFullOnTheLastDayOfTheYear() throws Exception {
		Map<String, Object> none = row(unpaid);
		assertMoney(none.get("paid"), "0.00");
		assertMoney(none.get("pending"), "37000.00");
		assertThat(none.get("paidUpToState")).isEqualTo("NOTHING");
		assertThat(none.get("paidUpTo")).isNull();
		assertThat(none.get("feeStatus")).isEqualTo("Unpaid");

		Map<String, Object> full = row(fullyPaid);
		assertMoney(full.get("pending"), "0.00");
		assertThat(full.get("paidUpToState")).isEqualTo("FULL");
		assertThat(full.get("paidUpTo")).isEqualTo("3922-03-31");
		assertThat(full.get("feeStatus")).isEqualTo("Paid");
	}

	@Test
	void theRegistrationFeeAloneIsNothingPaidForPaidUpToButStillPartlyPaid() throws Exception {
		Map<String, Object> r = row(registrationOnly);

		assertMoney(r.get("paid"), "500.00");
		assertThat(r.get("paidUpToState")).isEqualTo("NOTHING");
		assertThat(r.get("feeStatus")).isEqualTo("Partly paid");
	}

	@Test
	void aChequeNotClearedIsNotCounted() throws Exception {
		Map<String, Object> r = row(markedCheque);

		assertMoney(r.get("paid"), "0.00");
		assertMoney(r.get("pending"), "37000.00");
		assertThat(r.get("feeStatus")).isEqualTo("Unpaid");
	}

	@Test
	void aStudentWithNoFeeAmountShowsNoAmountAndNoStatus() throws Exception {
		Map<String, Object> r = row(noFee);

		assertThat(r.get("paid")).isNull();
		assertThat(r.get("pending")).isNull();
		assertThat(r.get("paidUpTo")).isNull();
		assertThat(r.get("paidUpToState")).isNull();
		assertThat(r.get("feeStatus")).isNull();
		assertThat(r.get("feeState")).isEqualTo("Fee not set");
	}

	@Test
	void earlierYearsPendingIsTheBroughtForwardAmountMinusThePreviousYearsReceipts() throws Exception {
		Map<String, Object> open = row(earlierOpen);
		assertMoney(open.get("earlierPending"), "3000.00");
		assertMoney(open.get("pending"), "0.00");
		assertThat(open.get("feeStatus")).isEqualTo("Partly paid");

		Map<String, Object> partly = row(earlierPartly);
		assertMoney(partly.get("earlierPending"), "2000.00");
		assertThat(partly.get("feeStatus")).isEqualTo("Partly paid");

		Map<String, Object> done = row(everythingPaid);
		assertMoney(done.get("earlierPending"), "0.00");
		assertThat(done.get("feeStatus")).isEqualTo("Paid");
	}

	@Test
	void sortingByPaidAndByPendingOrdersTheAmounts() throws Exception {
		List<Object> paid = rows("\"sortField\":\"paid\",\"sortOrder\":-1").stream()
			.map(r -> r.get("paid"))
			.filter(v -> v != null)
			.toList();
		for (int i = 1; i < paid.size(); i++) {
			assertThat(money(paid.get(i - 1))).isGreaterThanOrEqualTo(money(paid.get(i)));
		}
		List<Object> pending = rows("\"sortField\":\"pending\",\"sortOrder\":1").stream()
			.map(r -> r.get("pending"))
			.filter(v -> v != null)
			.toList();
		for (int i = 1; i < pending.size(); i++) {
			assertThat(money(pending.get(i - 1))).isLessThanOrEqualTo(money(pending.get(i)));
		}
		assertThat(rows("\"sortField\":\"paidUpTo\",\"sortOrder\":1")).hasSize(9);
	}

	@Test
	void theFeeStatusFilterKeepsOnlyThatStatus() throws Exception {
		List<Map<String, Object>> unpaidRows = rows("\"filters\":{\"feeStatus\":{\"value\":\"Unpaid\",\"matchMode\":\"equals\"}}");
		assertThat(unpaidRows).extracting(r -> r.get("grNo")).containsExactlyInAnyOrder(unpaid.grNo(), markedCheque.grNo());

		List<Map<String, Object>> paidRows = rows("\"filters\":{\"feeStatus\":{\"value\":\"Paid\",\"matchMode\":\"equals\"}}");
		assertThat(paidRows).extracting(r -> r.get("grNo")).containsExactlyInAnyOrder(fullyPaid.grNo(), everythingPaid.grNo());

		List<Map<String, Object>> partly = rows("\"filters\":{\"feeStatus\":{\"value\":\"Partly paid\",\"matchMode\":\"equals\"}}");
		assertThat(partly).extracting(r -> r.get("grNo"))
			.containsExactlyInAnyOrder(paidPart.grNo(), registrationOnly.grNo(), earlierOpen.grNo(), earlierPartly.grNo());
	}

	@Test
	void theExportHoldsNumericMoneyCellsAndABlankWhereThereIsNoFee() throws Exception {
		byte[] file = client.post("/api/students/export", request(""))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
			Sheet sheet = workbook.getSheetAt(0);
			List<String> headers = new ArrayList<>();
			sheet.getRow(0).forEach(cell -> headers.add(cell.getStringCellValue()));
			assertThat(headers).contains("Total fee", "Paid", "Pending", "Earlier years pending", "Paid up to",
					"Paid up to state", "Fee status");
			int grColumn = headers.indexOf("GR No.");
			for (Row row : sheet) {
				if (row.getRowNum() == 0) {
					continue;
				}
				String grNo = row.getCell(grColumn).getStringCellValue();
				if (grNo.equals(paidPart.grNo())) {
					assertThat(row.getCell(headers.indexOf("Paid")).getCellType()).isEqualTo(CellType.NUMERIC);
					assertThat(row.getCell(headers.indexOf("Paid")).getNumericCellValue()).isEqualTo(10500.00);
					assertThat(row.getCell(headers.indexOf("Pending")).getCellType()).isEqualTo(CellType.NUMERIC);
					assertThat(row.getCell(headers.indexOf("Earlier years pending")).getCellType())
						.isEqualTo(CellType.NUMERIC);
					assertThat(row.getCell(headers.indexOf("Fee status")).getStringCellValue()).isEqualTo("Partly paid");
				}
				if (grNo.equals(noFee.grNo())) {
					var paid = row.getCell(headers.indexOf("Paid"));
					assertThat(paid == null || paid.getCellType() == CellType.BLANK).isTrue();
				}
			}
		}
	}

}
