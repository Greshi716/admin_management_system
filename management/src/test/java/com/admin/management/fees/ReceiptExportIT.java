package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
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

/**
 * The register's Excel export (FR-020, FR-021, quickstart 17 and 18): the same rows as the list for the same filter,
 * money in numeric cells with two decimals, a "Day-wise totals" sheet that adds up the valid receipts of the filter
 * only, and exactly one Export entry in area Fees, none when the export fails.
 */
@Covers("FR-020")
@Covers("FR-021")
class ReceiptExportIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	Setup setup;

	SignedInClient client;

	@BeforeAll
	void seed() {
		setup = FeeTestData.year(jdbc, "ExpIT", LocalDate.of(3962, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
		Fx a = FeeTestData.studentWithAccount(jdbc, setup, 396201, "36500.00", "500.00");
		Fx b = FeeTestData.studentWithAccount(jdbc, setup, 396202, "36500.00", "500.00");
		FeeTestData.registerReceipt(jdbc, setup, a, "EX-1", LocalDate.of(3962, 10, 1), "CASH", "CURRENT_YEAR", "500.00",
				"10000.00");
		FeeTestData.registerReceipt(jdbc, setup, b, "EX-2", LocalDate.of(3962, 10, 1), "CASH", "CURRENT_YEAR", "0",
				"2000.50");
		FeeTestData.registerReceipt(jdbc, setup, a, "EX-3", LocalDate.of(3962, 10, 2), "CHEQUE", "CURRENT_YEAR", "0",
				"5000.00");
		long bounced = FeeTestData.registerReceipt(jdbc, setup, b, "EX-4", LocalDate.of(3962, 10, 2), "CHEQUE",
				"CURRENT_YEAR", "0", "3000.00");
		FeeTestData.markNotCleared(jdbc, bounced, "Bounced");
		FeeTestData.registerReceipt(jdbc, setup, a, "EX-5", LocalDate.of(3962, 10, 3), "CASH", "PREVIOUS_YEARS", "0",
				"1500.00");
	}

	@BeforeEach
	void signIn() throws Exception {
		client = SignedInClient.of(mvc);
	}

	private static String request(String filters) {
		return "{\"first\":0,\"rows\":25,\"globalFilter\":\"EX-\"" + (filters.isEmpty() ? "" : ",\"filters\":{" + filters + "}")
				+ "}";
	}

	private int exportEntries() {
		return jdbc.queryForObject("select count(*) from audit_log where area = 'Fees' and action = 'Export'",
				Integer.class);
	}

	private XSSFWorkbook export(String filters) throws Exception {
		byte[] file = client.post("/api/receipts/export", request(filters))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		return new XSSFWorkbook(new ByteArrayInputStream(file));
	}

	private static List<String> headers(Sheet sheet) {
		List<String> headers = new ArrayList<>();
		sheet.getRow(0).forEach(cell -> headers.add(cell.getStringCellValue()));
		return headers;
	}

	private static List<String> column(Sheet sheet, String header) {
		int at = headers(sheet).indexOf(header);
		List<String> values = new ArrayList<>();
		for (Row row : sheet) {
			if (row.getRowNum() > 0) {
				values.add(row.getCell(at).getStringCellValue());
			}
		}
		return values;
	}

	@Test
	void theExportHoldsTheSameRowsAsTheListInTheSameOrder() throws Exception {
		try (XSSFWorkbook workbook = export("")) {
			assertThat(column(workbook.getSheetAt(0), "Receipt no.")).containsExactly("EX-5", "EX-4", "EX-3", "EX-2",
					"EX-1");
			assertThat(headers(workbook.getSheetAt(0))).contains("Serial no.", "Date", "Student", "GR No.", "Std / Div",
					"Type", "Mode", "Registration fee", "Year fee", "Previous years' fee", "Total", "Status",
					"Cheque no.", "Cheque date", "Bank");
			assertThat(headers(workbook.getSheetAt(0))).doesNotContain("Student id", "Standard id");
		}
	}

	@Test
	void moneyCellsAreNumbersWithTwoDecimals() throws Exception {
		try (XSSFWorkbook workbook = export("")) {
			Sheet sheet = workbook.getSheetAt(0);
			List<String> headers = headers(sheet);
			Row ex2 = null;
			for (Row candidate : sheet) {
				if (candidate.getRowNum() > 0 && "EX-2".equals(candidate.getCell(headers.indexOf("Receipt no.")).getStringCellValue())) {
					ex2 = candidate;
				}
			}
			assertThat(ex2).isNotNull();
			for (String header : List.of("Registration fee", "Year fee", "Previous years' fee", "Total")) {
				Cell cell = ex2.getCell(headers.indexOf(header));
				assertThat(cell.getCellType()).as(header).isEqualTo(CellType.NUMERIC);
				assertThat(cell.getCellStyle().getDataFormatString()).as(header).contains("0.00");
			}
			assertThat(ex2.getCell(headers.indexOf("Year fee")).getNumericCellValue()).isEqualTo(2000.50);
			assertThat(ex2.getCell(headers.indexOf("Total")).getNumericCellValue()).isEqualTo(2000.50);
		}
	}

	@Test
	void theDayWiseTotalsSheetAddsUpTheValidReceiptsOfTheFilterAndLeavesOutTheNotCleared() throws Exception {
		try (XSSFWorkbook workbook = export("")) {
			Sheet totals = workbook.getSheet("Day-wise totals");
			assertThat(totals).isNotNull();
			assertThat(headers(totals)).containsExactly("Date", "Receipts", "Cash", "Cheque", "Total");
			assertThat(totals.getLastRowNum()).isEqualTo(3);
			// 1 Oct: 10,500.00 + 2,000.50 cash.
			assertThat(totals.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(2);
			assertThat(totals.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(12500.50);
			assertThat(totals.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(0.00);
			assertThat(totals.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(12500.50);
			// 2 Oct: only the cheque that is still valid.
			assertThat(totals.getRow(2).getCell(1).getNumericCellValue()).isEqualTo(1);
			assertThat(totals.getRow(2).getCell(3).getNumericCellValue()).isEqualTo(5000.00);
			assertThat(totals.getRow(2).getCell(4).getNumericCellValue()).isEqualTo(5000.00);
			// 3 Oct: the previous-years receipt counts in the total like any other valid receipt.
			assertThat(totals.getRow(3).getCell(4).getNumericCellValue()).isEqualTo(1500.00);
			BigDecimal grand = BigDecimal.ZERO;
			for (int i = 1; i <= totals.getLastRowNum(); i++) {
				grand = grand.add(BigDecimal.valueOf(totals.getRow(i).getCell(4).getNumericCellValue()));
			}
			assertThat(grand).isEqualByComparingTo("19000.50");
		}
	}

	@Test
	void theTotalsFollowSeveralFiltersAndThePageAndTheExportAgree() throws Exception {
		String cheques = "\"mode\":{\"value\":\"Cheque\",\"matchMode\":\"equals\"}";
		String october2 = "\"receiptDate\":{\"value\":[\"3962-10-02\",\"3962-10-03\"],\"matchMode\":\"dateRange\"}";
		String notCleared = "\"status\":{\"value\":\"Cheque not cleared\",\"matchMode\":\"equals\"}";

		try (XSSFWorkbook workbook = export(cheques + "," + october2)) {
			assertThat(column(workbook.getSheetAt(0), "Receipt no.")).containsExactly("EX-4", "EX-3");
			Sheet totals = workbook.getSheet("Day-wise totals");
			assertThat(totals.getLastRowNum()).isEqualTo(1);
			assertThat(totals.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(5000.00);
		}
		try (XSSFWorkbook workbook = export(notCleared)) {
			assertThat(column(workbook.getSheetAt(0), "Receipt no.")).containsExactly("EX-4");
			assertThat(workbook.getSheet("Day-wise totals").getLastRowNum()).isZero();
		}
	}

	@Test
	void anExportWritesExactlyOneFeesExportEntry() throws Exception {
		int before = exportEntries();

		try (XSSFWorkbook workbook = export("")) {
			assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
		}

		assertThat(exportEntries()).isEqualTo(before + 1);
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_log where area = 'Fees' and action = 'Export' and entity = 'Receipts'",
				Integer.class)).isEqualTo(before + 1);
	}

	@Test
	void anExportThatFailsWritesNoEntry() throws Exception {
		int before = exportEntries();

		client.post("/api/receipts/export", "{\"first\":0,\"rows\":7,\"globalFilter\":\"EX-\"}")
			.andExpect(status().is4xxClientError());

		assertThat(exportEntries()).isEqualTo(before);
	}

}
