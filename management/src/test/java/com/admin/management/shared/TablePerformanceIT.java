package com.admin.management.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.SignedInClient;

/**
 * Speed of the reusable table on 10,000 synthetic rows (SC-006, SC-007). Timing depends on the machine, so this runs
 * only on request, with generous limits: {@code ./mvnw verify -Dtest.groups=performance -Dtest.excludedGroups=none}.
 * The machine it ran on is written to {@code target/performance-report.txt}, next to each timing.
 *
 * <p>
 * The audit log is the data source: it is the widest list (search also looks inside the changes text) and the one
 * that grows fastest.
 */
@Tag("performance")
@Covers("FR-035")
@Covers("FR-033")
class TablePerformanceIT extends IntegrationTest {

	static final int ROWS = 10_000;

	static final long INTERACTIVE_LIMIT_MS = 2_000;

	static final long EXPORT_LIMIT_MS = 30_000;

	/** Heap growth allowed while a 10,000-row export runs; an export that loaded every row would not fit this. */
	static final long HEAP_GROWTH_LIMIT_BYTES = 150L * 1024 * 1024;

	static final List<String> REPORT = new ArrayList<>();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	SignedInClient api;

	@BeforeAll
	void seed() throws Exception {
		jdbc.update("""
				insert into audit_log (at, area, action, entity, changes, reason)
				select timestamptz '2026-01-01 00:00+05:30' + i * interval '47 minutes',
				       (array['Fees', 'Payroll', 'Master data', 'Security'])[i % 4 + 1],
				       (array['Create', 'Edit', 'Delete', 'Login'])[i % 4 + 1],
				       'perf#' || lpad(i::text, 5, '0'),
				       jsonb_build_object('old', jsonb_build_object('note', 'before ' || i),
				                          'new', jsonb_build_object('note', 'after ' || i)),
				       case when i % 10 = 0 then 'checked' end
				from generate_series(1, ?) as i
				""", ROWS);
		api = SignedInClient.of(mvc);
		api.post("/api/audit-log/search", "{}").andReturn(); // warm-up: connection pool, query plan, class loading
	}

	@Test
	void aPageComesBackInUnderTwoSeconds() throws Exception {
		assertFast("page", () -> api.post("/api/audit-log/search", "{\"first\":5000,\"rows\":50}").andReturn());
	}

	@Test
	void aSearchComesBackInUnderTwoSeconds() throws Exception {
		assertFast("search", () -> api.post("/api/audit-log/search", "{\"globalFilter\":\"after 9876\"}").andReturn());
	}

	@Test
	void aColumnFilterComesBackInUnderTwoSeconds() throws Exception {
		assertFast("filter", () -> api
			.post("/api/audit-log/search",
					"{\"filters\":{\"area\":{\"value\":\"Fees\",\"matchMode\":\"equals\"}},\"area\":\"Fees\",\"rows\":100}")
			.andReturn());
	}

	@Test
	void aSortOverTheWholeListComesBackInUnderTwoSeconds() throws Exception {
		assertFast("sort", () -> api.post("/api/audit-log/search", "{\"sortField\":\"changes\",\"sortOrder\":-1}")
			.andReturn());
	}

	@Test
	void aFullExportFinishesInUnderThirtySecondsWithoutHoldingEveryRowAndOtherRequestsStayQuick() throws Exception {
		System.gc();
		long baseline = usedHeap();
		AtomicLong peak = new AtomicLong(baseline);
		AtomicLong slowestOtherRequest = new AtomicLong();
		AtomicLong otherRequests = new AtomicLong();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			long start = System.nanoTime();
			Future<Integer> export = pool.submit(() -> api.post("/api/audit-log/export", "{}")
				.andReturn()
				.getResponse()
				.getContentAsByteArray().length);
			do {
				long begun = System.nanoTime();
				api.post("/api/audit-log/search", "{\"rows\":10}").andReturn();
				slowestOtherRequest.accumulateAndGet(millisSince(begun), Math::max);
				otherRequests.incrementAndGet();
				peak.accumulateAndGet(usedHeap(), Math::max);
			}
			while (!export.isDone());
			int bytes = export.get();
			long exportMs = millisSince(start);

			REPORT.add("export of " + ROWS + " rows: " + exportMs + " ms, " + bytes + " bytes (limit " + EXPORT_LIMIT_MS + " ms)");
			REPORT.add("other requests during the export: " + otherRequests + ", slowest " + slowestOtherRequest + " ms");
			REPORT.add("heap growth during the export: " + (peak.get() - baseline) / (1024 * 1024) + " MB (limit "
					+ HEAP_GROWTH_LIMIT_BYTES / (1024 * 1024) + " MB)");
			assertThat(bytes).isGreaterThan(0);
			assertThat(exportMs).as("export time").isLessThan(EXPORT_LIMIT_MS);
			assertThat(slowestOtherRequest.get()).as("slowest search during the export").isLessThan(INTERACTIVE_LIMIT_MS);
			assertThat(peak.get() - baseline).as("heap growth during the export").isLessThan(HEAP_GROWTH_LIMIT_BYTES);
		}
		finally {
			pool.shutdownNow();
			writeReport();
		}
	}

	private static void assertFast(String what, Callable<?> call) throws Exception {
		long start = System.nanoTime();
		call.call();
		long ms = millisSince(start);
		REPORT.add(what + " on " + ROWS + " rows: " + ms + " ms (limit " + INTERACTIVE_LIMIT_MS + " ms)");
		writeReport();
		assertThat(ms).as(what + " time in ms").isLessThan(INTERACTIVE_LIMIT_MS);
	}

	private static long millisSince(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}

	private static long usedHeap() {
		Runtime runtime = Runtime.getRuntime();
		return runtime.totalMemory() - runtime.freeMemory();
	}

	private static synchronized void writeReport() {
		List<String> lines = new ArrayList<>();
		lines.add("Reference machine: " + Runtime.getRuntime().availableProcessors() + " processors, "
				+ Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB max heap, " + System.getProperty("os.name")
				+ " " + System.getProperty("os.version") + ", Java " + System.getProperty("java.version"));
		lines.addAll(REPORT);
		try {
			Path file = Path.of("target", "performance-report.txt");
			Files.createDirectories(file.getParent());
			Files.write(file, lines);
		}
		catch (IOException ex) {
			throw new IllegalStateException("cannot write the performance report", ex);
		}
	}

}
