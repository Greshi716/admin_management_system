package com.admin.management.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.masterdata.standard.Standard;
import com.admin.management.shared.excel.ExcelExporter;
import com.admin.management.shared.table.ColumnDef;
import com.admin.management.shared.table.TableDef;
import com.admin.management.shared.table.TableEndpoints;
import com.admin.management.shared.table.TableRequest;
import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/** The Excel export of a list: everything that matches, in the asked order, every declared column (FR-033). */
@Covers("FR-033")
@Covers("FR-022")
class TableExportIT extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TableEndpoints tables;

	@BeforeEach
	void thirtyStandards() {
		jdbc.update("delete from divisions");
		jdbc.update("delete from standards");
		for (int i = 1; i <= 30; i++) {
			jdbc.update("insert into standards (name, display_order, active) values (?, ?, ?)",
					"Std " + String.format("%02d", i), i, i % 2 == 1);
		}
	}

	@Test
	void theFileHoldsEveryMatchingRowInTheAskedOrderNotJustThePage() throws Exception {
		byte[] file = SignedInClient.of(mvc)
			.post("/api/standards/export",
					"{\"first\":20,\"rows\":10,\"sortField\":\"name\",\"sortOrder\":-1,"
							+ "\"filters\":{\"active\":{\"value\":true,\"matchMode\":\"boolean\"}}}")
			.andReturn()
			.getResponse()
			.getContentAsByteArray();

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
			Sheet sheet = workbook.getSheetAt(0);
			assertThat(sheet.getLastRowNum()).isEqualTo(15); // 15 odd standards, all of them, not 10
			assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Std 29");
			assertThat(sheet.getRow(15).getCell(0).getStringCellValue()).isEqualTo("Std 01");
		}
	}

	@Test
	void everyDeclaredColumnIsExportedIncludingHiddenOnesAndSensitiveValuesAreMasked() throws Exception {
		TableDef<Standard> def = TableDef.of(Standard.class,
				List.of(ColumnDef.<Standard>text("name", "Name").asSensitive().asHidden(),
						ColumnDef.<Standard>number("displayOrder", "Order"), ColumnDef.<Standard>text("stream", "Stream")));
		MockHttpServletResponse response = new MockHttpServletResponse();

		tables.export("Master data", "Standards (all columns)", def,
				new TableRequest(0, 10, "displayOrder", 1, null, null), null, response);

		assertThat(response.getContentType()).isEqualTo(ExcelExporter.CONTENT_TYPE);
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
			Sheet sheet = workbook.getSheetAt(0);
			assertThat(cells(sheet, 0)).containsExactly("Name", "Order", "Stream");
			assertThat(sheet.getLastRowNum()).isEqualTo(30);
			String maskedName = sheet.getRow(1).getCell(0).getStringCellValue();
			assertThat(maskedName).startsWith("**").endsWith("d 01").doesNotContain("Std");
		}
	}

	@Test
	void anEmptyResultGivesAFileWithHeadingsOnly() throws Exception {
		byte[] file = SignedInClient.of(mvc)
			.post("/api/standards/export", "{\"globalFilter\":\"nothing matches this\"}")
			.andReturn()
			.getResponse()
			.getContentAsByteArray();

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
			Sheet sheet = workbook.getSheetAt(0);
			assertThat(sheet.getLastRowNum()).isZero();
			assertThat(cells(sheet, 0)).containsExactly("Name", "Display order", "Stream", "Active");
		}
	}

	@Test
	void everyExportWritesOneExportEntryWithTheRowCountAndTheSearch() throws Exception {
		long before = exportEntries();

		SignedInClient.of(mvc).post("/api/standards/export", "{\"globalFilter\":\"Std 2\"}").andReturn();

		assertThat(exportEntries()).isEqualTo(before + 1);
		Map<String, Object> entry = jdbc.queryForMap(
				"select * from audit_log where action = 'Export' and entity = 'Standards' order by id desc limit 1");
		assertThat(entry.get("area")).isEqualTo("Master data");
		assertThat(entry.get("user_id")).isNotNull();
		assertThat(entry.get("changes").toString()).contains("\"rows\": 10").contains("Std 2");
	}

	@Test
	void anExtraSheetIsAddedAndTheExportIsStillAuditedOnce() throws Exception {
		TableDef<Standard> def = TableDef.of(Standard.class,
				List.of(ColumnDef.<Standard>text("name", "Name"), ColumnDef.<Standard>number("displayOrder", "Order")));
		MockHttpServletResponse response = new MockHttpServletResponse();
		long before = exportEntries();
		int[] seen = { 0 };

		tables.export("Master data", "Standards with totals", def, new TableRequest(0, 10, "displayOrder", 1, null, null),
				null, response, new ExcelExporter.Extra() {
					@Override
					public void observe(Map<String, Object> row) {
						seen[0]++;
					}

					@Override
					public ExcelExporter.ExtraSheet sheet() {
						return new ExcelExporter.ExtraSheet("Totals", List.of("Rows"), List.of(List.of(seen[0])));
					}
				});

		assertThat(seen[0]).isEqualTo(30);
		assertThat(exportEntries()).isEqualTo(before + 1);
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
			assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
			assertThat(workbook.getSheetAt(1).getRow(1).getCell(0).getNumericCellValue()).isEqualTo(30.0);
		}
	}

	private long exportEntries() {
		return jdbc.queryForObject("select count(*) from audit_log where action = 'Export'", Long.class);
	}

	private static List<String> cells(Sheet sheet, int rowIndex) {
		List<String> values = new ArrayList<>();
		sheet.getRow(rowIndex).forEach(cell -> values.add(cell.getStringCellValue()));
		return values;
	}

}
