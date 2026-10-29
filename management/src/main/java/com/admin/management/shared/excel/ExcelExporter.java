package com.admin.management.shared.excel;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import com.admin.management.shared.masking.SensitiveMasker;
import com.admin.management.shared.table.ColumnDef;
import com.admin.management.shared.time.ClockConfig;

/**
 * Writes a list to an {@code .xlsx} stream (FR-033). The header row has every declared column, including the
 * ones hidden on screen, and sensitive columns stay masked. Rows are written as they are read, with a window of
 * 500 rows in memory, so a 10,000 row export does not grow the heap.
 */
@Component
public class ExcelExporter {

	public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	static final int WINDOW = 500;

	/** Excel's own limit, header included. */
	static final int MAX_ROWS = 1_048_576;

	/** Supplies the rows by calling {@code sink} once per row, in order. */
	@FunctionalInterface
	public interface RowSource {

		void stream(Consumer<Map<String, Object>> sink);

	}

	/**
	 * Looks at every row as it is written and, when the rows are done, supplies a second sheet (for example day-wise
	 * totals). Only what the observer keeps in memory counts against the heap, so it must stay small.
	 */
	public interface Extra {

		void observe(Map<String, Object> row);

		ExtraSheet sheet();

	}

	/** A small second sheet: a name, a header row and the rows of values (same value types as the main sheet). */
	public record ExtraSheet(String name, List<String> headers, List<List<Object>> rows) {
	}

	/**
	 * @param columns every column the list can show; the map of each row is keyed by {@code field}
	 * @return the number of data rows written
	 */
	public long write(String sheetName, List<? extends ColumnDef<?>> columns, RowSource rows, OutputStream out)
			throws IOException {
		return write(sheetName, columns, rows, out, null);
	}

	/** As above; the extra sheet, if any, is written after the main one. */
	public long write(String sheetName, List<? extends ColumnDef<?>> columns, RowSource rows, OutputStream out,
			Extra extra) throws IOException {
		try (SXSSFWorkbook workbook = new SXSSFWorkbook(WINDOW)) {
			Styles styles = new Styles(workbook);
			Sheet sheet = workbook.createSheet(safeSheetName(sheetName));
			writeHeader(sheet, columns, styles);
			long[] written = { 0 };
			rows.stream(row -> {
				int index = (int) written[0] + 1;
				if (index >= MAX_ROWS) {
					throw new IllegalStateException("Too many rows for one Excel sheet; narrow the filters.");
				}
				Row excelRow = sheet.createRow(index);
				for (int c = 0; c < columns.size(); c++) {
					ColumnDef<?> column = columns.get(c);
					Object value = row.get(column.field());
					if (column.sensitive() && value != null) {
						value = SensitiveMasker.mask(value.toString());
					}
					writeCell(excelRow.createCell(c), value, styles);
				}
				if (extra != null) {
					extra.observe(row);
				}
				written[0]++;
			});
			if (extra != null) {
				writeExtra(workbook, extra.sheet(), styles);
			}
			workbook.write(out);
			return written[0];
		}
	}

	private void writeExtra(SXSSFWorkbook workbook, ExtraSheet extra, Styles styles) {
		if (extra == null) {
			return;
		}
		Sheet sheet = workbook.createSheet(safeSheetName(extra.name()));
		Row header = sheet.createRow(0);
		for (int c = 0; c < extra.headers().size(); c++) {
			Cell cell = header.createCell(c);
			cell.setCellValue(extra.headers().get(c));
			cell.setCellStyle(styles.header);
			sheet.setColumnWidth(c, Math.min(Math.max(extra.headers().get(c).length() + 4, 14), 50) * 256);
		}
		sheet.createFreezePane(0, 1);
		int index = 1;
		for (List<Object> values : extra.rows()) {
			Row row = sheet.createRow(index++);
			for (int c = 0; c < values.size(); c++) {
				writeCell(row.createCell(c), values.get(c), styles);
			}
		}
	}

	private void writeHeader(Sheet sheet, List<? extends ColumnDef<?>> columns, Styles styles) {
		Row header = sheet.createRow(0);
		for (int c = 0; c < columns.size(); c++) {
			Cell cell = header.createCell(c);
			cell.setCellValue(columns.get(c).header());
			cell.setCellStyle(styles.header);
			sheet.setColumnWidth(c, Math.min(Math.max(columns.get(c).header().length() + 4, 14), 50) * 256);
		}
		sheet.createFreezePane(0, 1);
	}

	private void writeCell(Cell cell, Object value, Styles styles) {
		if (value == null) {
			cell.setBlank();
		}
		else if (value instanceof Boolean flag) {
			cell.setCellValue(flag);
		}
		else if (value instanceof LocalDate date) {
			cell.setCellValue(date);
			cell.setCellStyle(styles.date);
		}
		else if (value instanceof Instant instant) {
			cell.setCellValue(LocalDateTime.ofInstant(instant, ClockConfig.SCHOOL_ZONE));
			cell.setCellStyle(styles.dateTime);
		}
		else if (value instanceof BigDecimal exact) {
			writeMoney(cell, exact, styles);
		}
		else if (value instanceof Integer || value instanceof Long || value instanceof Short) {
			cell.setCellValue(((Number) value).longValue());
		}
		else if (value instanceof Number number) {
			cell.setCellValue(number.doubleValue());
		}
		else {
			cell.setCellValue(value.toString());
		}
	}

	/**
	 * An amount is a numeric cell with the format 0.00, so the admin can sum it. A numeric cell holds a double, which
	 * Excel itself uses for every number, so the value goes through a double once, here. That is allowed only when it
	 * costs nothing (constitution Principle I): at most two decimals and the double reads back as the same amount.
	 * Every NUMERIC(12,2) value qualifies (at most 12 digits); anything else keeps the exact text.
	 */
	private void writeMoney(Cell cell, BigDecimal amount, Styles styles) {
		double asDouble = amount.doubleValue();
		boolean exact = amount.stripTrailingZeros().scale() <= 2 && BigDecimal.valueOf(asDouble).compareTo(amount) == 0;
		if (exact) {
			cell.setCellValue(asDouble);
			cell.setCellStyle(styles.money);
		}
		else {
			cell.setCellValue(amount.toPlainString());
		}
	}

	/** Sheet names cannot hold some characters and are limited to 31 characters. */
	private static String safeSheetName(String name) {
		String cleaned = name == null || name.isBlank() ? "Sheet1" : name.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
		return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
	}

	private static final class Styles {

		final CellStyle header;

		final CellStyle date;

		final CellStyle dateTime;

		final CellStyle money;

		Styles(SXSSFWorkbook workbook) {
			CreationHelper helper = workbook.getCreationHelper();
			Font bold = workbook.createFont();
			bold.setBold(true);
			header = workbook.createCellStyle();
			header.setFont(bold);
			date = workbook.createCellStyle();
			date.setDataFormat(helper.createDataFormat().getFormat("dd-mm-yyyy"));
			dateTime = workbook.createCellStyle();
			dateTime.setDataFormat(helper.createDataFormat().getFormat("dd-mm-yyyy hh:mm"));
			money = workbook.createCellStyle();
			money.setDataFormat(helper.createDataFormat().getFormat("0.00"));
		}

	}

}
