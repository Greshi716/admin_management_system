package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.admin.management.shared.excel.ExcelExporter;

/**
 * Day-wise totals of the receipts a register filter matches (FR-020, FR-021): per date the number of receipts and the
 * Cash, Cheque and overall total. Only Valid receipts count, so a receipt marked Cheque not cleared (or voided) is
 * left out, as it is in every balance. It sees each row as the export or the on-screen panel streams it and keeps one
 * small line per date.
 */
final class DayTotals implements ExcelExporter.Extra {

	static final String SHEET = "Day-wise totals";

	/** One date of the totals. */
	record Day(LocalDate date, long count, BigDecimal cash, BigDecimal cheque, BigDecimal total) {
	}

	private final Map<LocalDate, long[]> counts = new TreeMap<>();

	private final Map<LocalDate, BigDecimal[]> sums = new TreeMap<>();

	@Override
	public void observe(Map<String, Object> row) {
		if (!ReceiptTable.STATUS_VALID.equals(row.get("status"))) {
			return;
		}
		LocalDate date = (LocalDate) row.get("receiptDate");
		BigDecimal total = Money.scale2((BigDecimal) row.get("total"));
		boolean cash = "Cash".equals(row.get("mode"));
		counts.computeIfAbsent(date, (d) -> new long[1])[0]++;
		BigDecimal[] sum = sums.computeIfAbsent(date, (d) -> new BigDecimal[] { Money.scale2(BigDecimal.ZERO), Money.scale2(BigDecimal.ZERO) });
		int at = cash ? 0 : 1;
		sum[at] = sum[at].add(total);
	}

	/** The dates in order, oldest first. */
	List<Day> days() {
		List<Day> days = new ArrayList<>();
		counts.forEach((date, count) -> {
			BigDecimal[] sum = sums.get(date);
			days.add(new Day(date, count[0], sum[0], sum[1], sum[0].add(sum[1])));
		});
		return days;
	}

	@Override
	public ExcelExporter.ExtraSheet sheet() {
		List<List<Object>> rows = new ArrayList<>();
		for (Day day : days()) {
			rows.add(List.of(day.date(), day.count(), day.cash(), day.cheque(), day.total()));
		}
		return new ExcelExporter.ExtraSheet(SHEET, List.of("Date", "Receipts", "Cash", "Cheque", "Total"), rows);
	}

}
