package com.admin.management.fees;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.shared.excel.ExcelExporter;
import com.admin.management.shared.table.TableDef;
import com.admin.management.shared.table.TablePage;
import com.admin.management.shared.table.TableQueryService;
import com.admin.management.shared.table.TableRequest;
import com.admin.management.support.Covers;
import com.admin.management.support.FeeTestData;
import com.admin.management.support.FeeTestData.Setup;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/**
 * Speed of the receipt register (SC-006, plan spike 4): page, search, filter and sort on 10,000 receipts of 2,000
 * synthetic students, the Excel export with its day-wise totals sheet, and the time to open one receipt for printing.
 * Timing depends on the machine, so it runs only on request:
 * {@code ./mvnw verify -Dtest.groups=performance -Dtest.excludedGroups=none -Dtest=ReceiptRegisterPerformanceIT}.
 * The measured times are written to {@code target/receipt-register-performance.txt} for quickstart-results.md.
 */
@Tag("performance")
@Covers("FR-019")
class ReceiptRegisterPerformanceIT extends IntegrationTest {

	static final int STUDENTS = 2_000;

	static final int RECEIPTS = 10_000;

	static final long INTERACTIVE_LIMIT_MS = 2_000;

	static final long TOTALS_LIMIT_MS = 5_000;

	static final long EXPORT_LIMIT_MS = 30_000;

	static final List<String> REPORT = new ArrayList<>();

	@Autowired
	TableQueryService tables;

	@Autowired
	ExcelExporter exporter;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MockMvc mvc;

	Setup setup;

	@BeforeAll
	void seed() {
		clock.reset();
		setup = FeeTestData.year(jdbc, "RegPerf", LocalDate.of(3963, 4, 1), 1);
		FeeTestData.setStatus(jdbc, setup.yearId(), true);
		FeeTestData.bulkStudentsAndReceipts(jdbc, setup, "RGP", STUDENTS, RECEIPTS);
		jdbc.execute("analyze");
		tables.page(ReceiptTable.build(), TableRequest.firstPage()); // warm-up
	}

	@Test
	void everyRegisterOperationIsFastOnTenThousandReceipts() throws Exception {
		TableDef<Receipt> def = ReceiptTable.build();
		try {
			fast("page 3 of 50, default order", () -> tables.page(def, new TableRequest(100, 50, null, null, "RGP-R", null)));
			fast("search by receipt no.", () -> tables.page(def, new TableRequest(0, 25, null, null, "RGP-R9876", null)));
			fast("search by student name", () -> tables.page(def, new TableRequest(0, 25, null, null, "Testsurname Alpha", null)));
			fast("filter by mode Cash", () -> tables.page(def, withFilter("mode", "Cash")));
			fast("filter by status Valid", () -> tables.page(def, withFilter("status", "Valid")));
			fast("filter by status Cheque not cleared", () -> tables.page(def, withFilter("status", "Cheque not cleared")));
			fast("filter by standard", () -> tables.page(def, withFilter("standardId", setup.standard(0))));
			fast("filter by date range", () -> tables.page(def, new TableRequest(0, 25, null, null, null,
					Map.of("receiptDate", new TableRequest.Filter(List.of("3000-01-01", "3999-12-31"), "dateRange")))));
			fast("sort by Total desc, page 200", () -> tables.page(def, new TableRequest(4975, 25, "total", -1, null, null)));
			fast("sort by Student", () -> tables.page(def, new TableRequest(0, 25, "student", 1, null, null)));
			fast("sort by Status", () -> tables.page(def, new TableRequest(0, 25, "status", 1, null, null)));
			// Reads every receipt of the filter like the export does, so it has its own limit.
			timed("day-wise totals of all 10,000 receipts", TOTALS_LIMIT_MS, () -> {
				DayTotals totals = new DayTotals();
				tables.stream(def, new TableRequest(0, 25, null, null, "RGP-R", null), null, totals::observe);
				return totals.days();
			});

			TablePage page = tables.page(def, new TableRequest(0, 25, null, null, "RGP-R", null));
			assertThat(page.total()).isEqualTo(RECEIPTS);

			long start = System.nanoTime();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			DayTotals totals = new DayTotals();
			long rows = exporter.write("Receipts", def.columns().stream().filter(column -> !column.internal()).toList(),
					sink -> tables.stream(def, new TableRequest(0, 25, null, null, "RGP-R", null), null, sink), out,
					totals);
			long exportMs = (System.nanoTime() - start) / 1_000_000;
			REPORT.add("export of " + rows + " receipts with day-wise totals: " + exportMs + " ms, " + out.size()
					+ " bytes (limit " + EXPORT_LIMIT_MS + " ms)");
			assertThat(rows).isEqualTo(RECEIPTS);
			assertThat(exportMs).isLessThan(EXPORT_LIMIT_MS);
			assertThat(totals.days()).hasSize(1);
			assertThat(totals.days().get(0).count()).isEqualTo(RECEIPTS);

			SignedInClient client = SignedInClient.of(mvc);
			long serial = jdbc.queryForObject("select max(serial_no) from receipts where receipt_no like 'RGP-R%'",
					Long.class);
			fast("open one receipt for printing (GET /api/receipts/{serialNo})", () -> client.get("/api/receipts/" + serial));
		}
		finally {
			write();
		}
	}

	private static TableRequest withFilter(String field, Object value) {
		return new TableRequest(0, 25, null, null, "RGP-R", Map.of(field, new TableRequest.Filter(value, "equals")));
	}

	private static void fast(String what, Callable<?> call) throws Exception {
		timed(what, INTERACTIVE_LIMIT_MS, call);
	}

	private static void timed(String what, long limitMs, Callable<?> call) throws Exception {
		long start = System.nanoTime();
		call.call();
		long ms = (System.nanoTime() - start) / 1_000_000;
		REPORT.add(what + ": " + ms + " ms (limit " + limitMs + " ms)");
		assertThat(ms).as(what + " in ms").isLessThan(limitMs);
	}

	private static void write() throws IOException {
		List<String> lines = new ArrayList<>();
		lines.add("Machine: " + Runtime.getRuntime().availableProcessors() + " processors, "
				+ Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB max heap, " + System.getProperty("os.name")
				+ " " + System.getProperty("os.version") + ", Java " + System.getProperty("java.version"));
		lines.add(STUDENTS + " students, " + RECEIPTS + " receipts");
		lines.addAll(REPORT);
		Path file = Path.of("target", "receipt-register-performance.txt");
		Files.createDirectories(file.getParent());
		Files.write(file, lines);
	}

}
