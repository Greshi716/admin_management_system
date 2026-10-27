package com.admin.management.students;

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

import com.admin.management.masterdata.api.MasterDataLookup;
import com.admin.management.shared.excel.ExcelExporter;
import com.admin.management.shared.table.ColumnDef;
import com.admin.management.shared.table.TableDef;
import com.admin.management.shared.table.TablePage;
import com.admin.management.shared.table.TableQueryService;
import com.admin.management.shared.table.TableRequest;
import com.admin.management.support.Covers;
import com.admin.management.support.IntegrationTest;
import com.admin.management.support.StudentTestData;
import com.admin.management.support.StudentTestData.YearSetup;

/**
 * Speed of the Students list (SC-003, plan review S4): page, count, search, filter, sort and export of the final Students list
 * columns on 10,000 synthetic students with 2 to 3 class records each. Timing depends on the machine, so it runs
 * only on request: {@code ./mvnw verify -Dtest.groups=performance -Dtest.excludedGroups=none -Dtest=StudentListPerformanceIT}.
 * The measured times are written to {@code target/student-list-performance.txt}.
 */
@Tag("performance")
@Covers("FR-008")
class StudentListPerformanceIT extends IntegrationTest {

	static final int STUDENTS = 10_000;

	static final long INTERACTIVE_LIMIT_MS = 2_000;

	static final long EXPORT_LIMIT_MS = 30_000;

	static final List<String> REPORT = new ArrayList<>();

	@Autowired
	TableQueryService tables;

	@Autowired
	MasterDataLookup lookup;

	@Autowired
	ExcelExporter exporter;

	@Autowired
	JdbcTemplate jdbc;

	long standardId;

	long categoryId;

	@BeforeAll
	void seed() {
		clock.reset();
		categoryId = jdbc.queryForObject("select min(id) from student_categories", Long.class);
		// 12 standards x 4 divisions x 4 years, so the lookup maps have the size of a real school
		List<Long> years = new ArrayList<>();
		for (int y = 0; y < 4; y++) {
			int from = 2023 + y;
			years.add(jdbc.queryForObject(
					"insert into academic_years (name, start_date, end_date) values (?, ?::date, ?::date) returning id",
					Long.class, from + "-" + (from + 1), from + "-04-01", (from + 1) + "-03-31"));
		}
		List<Long> standards = new ArrayList<>();
		for (int s = 1; s <= 12; s++) {
			standards.add(jdbc.queryForObject("insert into standards (name, display_order) values (?, ?) returning id",
					Long.class, "Standard " + s, s));
		}
		standardId = standards.get(9);
		List<YearSetup> setups = new ArrayList<>();
		for (int y = 0; y < years.size(); y++) {
			for (long standard : standards) {
				for (String division : List.of("A", "B", "C", "D")) {
					long id = jdbc.queryForObject(
							"insert into divisions (academic_year_id, standard_id, name) values (?, ?, ?) returning id",
							Long.class, years.get(y), standard, division);
					if (standard == standardId && division.equals("A") && y > 0) {
						setups.add(new YearSetup(years.get(y), LocalDate.of(2023 + y, 4, 1), standard, id));
					}
				}
			}
		}
		StudentTestData.generate(jdbc, STUDENTS, categoryId, standardId, setups);
		// every class record gets a fee account (F2 FR-010): 20,000.00 to 20,049.00 plus a registration fee of 250.00
		jdbc.update("""
				insert into fee_accounts (enrollment_id, academic_year_id, amount, registration_fee, due_date, updated_at)
				select e.id, e.academic_year_id, 20000 + (e.id % 50), 250.00, null, now() from student_enrollments e
				""");
		jdbc.execute("analyze");
		TableDef<Student> def = StudentTable.build(lookup);
		tables.page(def, TableRequest.firstPage(), StudentTable.statusScope(null)); // warm-up
	}

	@Test
	void everyListOperationIsFastOnTenThousandStudents() throws Exception {
		TableDef<Student> def = StudentTable.build(lookup);
		var active = StudentTable.statusScope(null);
		try {
			fast("page 3 of 50, default order", () -> tables.page(def, new TableRequest(100, 50, null, null, null, null), active));
			fast("search by name", () -> tables.page(def, new TableRequest(0, 25, null, null, "Testsurname Alpha", null), active));
			fast("search by GR No.", () -> tables.page(def, new TableRequest(0, 25, null, null, "GR-9876", null), active));
			fast("search by mobile", () -> tables.page(def, new TableRequest(0, 25, null, null, "9000009876", null), active));
			fast("filter by standard id", () -> tables.page(def, withFilter("standardId", standardId), active));
			fast("filter by category id", () -> tables.page(def, withFilter("categoryId", categoryId), active));
			fast("sort by Std / Div", () -> tables.page(def, new TableRequest(0, 25, "stdDiv", 1, null, null), active));
			fast("sort by Category desc", () -> tables.page(def, new TableRequest(5000, 25, "category", -1, null, null), active));
			fast("sort by name desc", () -> tables.page(def, new TableRequest(0, 25, "name", -1, null, null), active));
			fast("sort by Total fee desc, page 200", () -> tables.page(def, new TableRequest(4975, 25, "totalFee", -1, null, null), active));
			fast("filter by Total fee", () -> tables.page(def, withFilter("totalFee", new java.math.BigDecimal("20275.00")), active));
			fast("filter by Total fee and sort by it", () -> tables.page(def, new TableRequest(0, 25, "totalFee", 1, null, Map.of("totalFee", new TableRequest.Filter(new java.math.BigDecimal("20275.00"), "equals"))), active));
			fast("status All", () -> tables.page(def, TableRequest.firstPage(), StudentTable.statusScope("All")));

			TablePage page = tables.page(def, withFilter("standardId", standardId), active);
			assertThat(page.total()).isEqualTo(STUDENTS);
			Map<String, Object> first = page.rows().get(0);
			assertThat(first.get("stdDiv")).isEqualTo("Standard 10-A");
			assertThat(first.get("aadhaar")).asString().startsWith("********");

			long start = System.nanoTime();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			long rows = exporter.write("Students", def.columns().stream().filter(column -> !column.internal()).toList(),
					sink -> tables.stream(def, new TableRequest(0, 25, null, null, null, null), active, sink), out);
			long exportMs = (System.nanoTime() - start) / 1_000_000;
			REPORT.add("export of " + rows + " students: " + exportMs + " ms, " + out.size() + " bytes (limit "
					+ EXPORT_LIMIT_MS + " ms)");
			assertThat(rows).isEqualTo(STUDENTS);
			assertThat(exportMs).isLessThan(EXPORT_LIMIT_MS);
		}
		finally {
			write();
		}
	}

	private static TableRequest withFilter(String field, Object value) {
		return new TableRequest(0, 25, null, null, null, Map.of(field, new TableRequest.Filter(value, "equals")));
	}

	private static void fast(String what, Callable<?> call) throws Exception {
		long start = System.nanoTime();
		call.call();
		long ms = (System.nanoTime() - start) / 1_000_000;
		REPORT.add(what + ": " + ms + " ms (limit " + INTERACTIVE_LIMIT_MS + " ms)");
		assertThat(ms).as(what + " in ms").isLessThan(INTERACTIVE_LIMIT_MS);
	}

	private static void write() throws IOException {
		List<String> lines = new ArrayList<>();
		lines.add("Machine: " + Runtime.getRuntime().availableProcessors() + " processors, "
				+ Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB max heap, " + System.getProperty("os.name")
				+ " " + System.getProperty("os.version") + ", Java " + System.getProperty("java.version"));
		lines.add(STUDENTS + " students, 2 to 3 class records each");
		lines.addAll(REPORT);
		Path file = Path.of("target", "student-list-performance.txt");
		Files.createDirectories(file.getParent());
		Files.write(file, lines);
	}

}
