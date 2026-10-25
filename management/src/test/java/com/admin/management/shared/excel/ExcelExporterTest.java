package com.admin.management.shared.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.admin.management.shared.table.ColumnDef;
import com.admin.management.support.Covers;

@Covers("FR-033")
class ExcelExporterTest {

	ExcelExporter exporter = new ExcelExporter();

	List<ColumnDef<Object>> columns = List.of(ColumnDef.text("name", "Name"),
			ColumnDef.<Object>text("secret", "Secret").asSensitive().asHidden(), ColumnDef.date("day", "Day"),
			ColumnDef.date("at", "When"), ColumnDef.bool("active", "Active"), ColumnDef.number("count", "Count"),
			ColumnDef.number("amount", "Amount"));

	@Test
	void headerListsEveryColumnIncludingHiddenOnes() throws Exception {
		Sheet sheet = export(List.of());

		Row header = sheet.getRow(0);
		assertThat(header.getLastCellNum()).isEqualTo((short) 7);
		assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Name");
		assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Secret");
		assertThat(sheet.getLastRowNum()).isZero();
	}

	@Test
	void writesTypedCellsAndMasksSensitiveValues() throws Exception {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("name", "Row one");
		row.put("secret", "123456789012");
		row.put("day", LocalDate.of(2026, 4, 1));
		row.put("at", Instant.parse("2026-04-01T06:30:00Z"));
		row.put("active", true);
		row.put("count", 42L);
		row.put("amount", new BigDecimal("1234.50"));

		Sheet sheet = export(List.of(row));

		Row data = sheet.getRow(1);
		assertThat(data.getCell(0).getStringCellValue()).isEqualTo("Row one");
		assertThat(data.getCell(1).getStringCellValue()).isEqualTo("********9012");
		assertThat(data.getCell(2).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 4, 1));
		assertThat(data.getCell(3).getLocalDateTimeCellValue().toString()).isEqualTo("2026-04-01T12:00");
		assertThat(data.getCell(4).getBooleanCellValue()).isTrue();
		assertThat(data.getCell(5).getNumericCellValue()).isEqualTo(42d);
		assertThat(data.getCell(6).getCellType()).isEqualTo(CellType.STRING);
		assertThat(data.getCell(6).getStringCellValue()).isEqualTo("1234.50");
	}

	@Test
	void maskingHoldsEvenWhenTheRowSourceDidNotMask() throws Exception {
		Sheet sheet = export(List.of(Map.of("secret", "ABCDEFGH")));

		assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("****EFGH");
	}

	@Test
	void textStartingWithAFormulaCharacterIsWrittenAsPlainText() throws Exception {
		List<String> typed = List.of("=1+1", "+SUM(A1)", "-5", "@cmd", "=HYPERLINK(\"http://example.invalid\")");

		Sheet sheet = export(typed.stream().map(text -> Map.<String, Object>of("name", text)).toList());

		for (int i = 0; i < typed.size(); i++) {
			var cell = sheet.getRow(i + 1).getCell(0);
			assertThat(cell.getCellType()).isEqualTo(CellType.STRING);
			assertThat(cell.getStringCellValue()).isEqualTo(typed.get(i));
		}
	}

	@Test
	void missingValuesBecomeBlankCells() throws Exception {
		Sheet sheet = export(List.of(Map.of("name", "only name")));

		assertThat(sheet.getRow(1).getCell(2).getCellType()).isEqualTo(CellType.BLANK);
	}

	@Test
	void writesManyRowsInOrderBeyondTheStreamingWindow() throws Exception {
		List<Map<String, Object>> rows = new java.util.ArrayList<>();
		for (int i = 1; i <= 1503; i++) {
			rows.add(Map.of("name", "row " + i));
		}

		Sheet sheet = export(rows);

		assertThat(sheet.getLastRowNum()).isEqualTo(1503);
		assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("row 1");
		assertThat(sheet.getRow(1503).getCell(0).getStringCellValue()).isEqualTo("row 1503");
	}

	@Test
	void returnsTheNumberOfDataRows() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		long written = exporter.write("Test", columns, sink -> {
			sink.accept(Map.of("name", "a"));
			sink.accept(Map.of("name", "b"));
		}, out);

		assertThat(written).isEqualTo(2);
	}

	@Test
	void sheetNameIsCleanedForExcel() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		exporter.write("Audit/log: all the things that happened this year", columns, sink -> {
		}, out);

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
			assertThat(workbook.getSheetName(0)).hasSizeLessThanOrEqualTo(31).doesNotContain("/").doesNotContain(":");
		}
	}

	private Sheet export(List<Map<String, Object>> rows) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		exporter.write("Test", columns, sink -> rows.forEach(sink), out);
		XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()));
		return workbook.getSheetAt(0);
	}

}
