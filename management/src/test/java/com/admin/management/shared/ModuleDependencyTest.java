package com.admin.management.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.admin.management.support.Covers;

/**
 * Guards the modular monolith (constitution VII, research R7): reads every {@code com.admin.management.*} reference
 * of the main sources (imports and fully qualified names in the code alike, not the {@code package} line), then
 * fails on a dependency cycle between modules or on a use of another module's internals. A module is the first
 * package below {@code com.admin.management}.
 */
@Covers("FR-016")
@Covers("FR-014")
@Covers("FR-015")
class ModuleDependencyTest {

	private static final String BASE = "com.admin.management.";

	private static final Path SOURCES = Path.of("src/main/java/com/admin/management");

	private static final Pattern PACKAGE_LINE = Pattern.compile("^package\\s[^;]*;", Pattern.MULTILINE);

	private static final Pattern REFERENCE = Pattern
		.compile("(?<![\\w.])com\\.admin\\.management\\.(\\w+(?:\\.\\w+)*)");

	/** What any module may import from another module. Everything else of another module is internal. */
	private static final List<String> PUBLIC = List.of("shared.", "audit.AuditService", "audit.AuditExportRecorder",
			"masterdata.api.", "students.api.", "fees.api.", "yearchange.api.", "admissions.api.");

	/**
	 * Edges the architecture allows today (research R7, F2 research R2): students reaches fees only through the small
	 * interface in shared, so the order is shared, audit, masterdata, students, fees and there is no cycle.
	 */
	private static final Map<String, Set<String>> ALLOWED = Map.of(
			"audit", Set.of("shared"),
			"masterdata", Set.of("audit", "shared"),
			"security", Set.of("audit", "shared"),
			"students", Set.of("shared", "audit", "masterdata"),
			"fees", Set.of("shared", "audit", "masterdata", "students"),
			"yearchange", Set.of("shared", "audit", "masterdata", "students", "fees"),
			"admissions", Set.of("shared", "audit", "masterdata", "students"),
			"shared", Set.of());

	/** The module that owns each table. A module reads only its own tables, apart from the list below. */
	private static final Map<String, String> TABLE_OWNER = Map.ofEntries(
			Map.entry("fee_year_status", "fees"),
			Map.entry("fee_rows", "fees"),
			Map.entry("fee_accounts", "fees"),
			Map.entry("fee_brought_forward", "fees"),
			Map.entry("students", "students"),
			Map.entry("student_enrollments", "students"),
			Map.entry("student_gr_numbers", "students"),
			Map.entry("student_photos", "students"),
			Map.entry("users", "security"),
			Map.entry("audit_log", "audit"),
			Map.entry("academic_years", "masterdata"),
			Map.entry("standards", "masterdata"),
			Map.entry("divisions", "masterdata"),
			Map.entry("student_categories", "masterdata"),
			Map.entry("receipts", "fees"),
			Map.entry("receipt_marks", "fees"),
			Map.entry("v_valid_receipts", "fees"),
			Map.entry("v_fee_position", "fees"),
			Map.entry("v_earlier_pending", "fees"),
			Map.entry("admission_requests", "admissions"),
			Map.entry("admission_import_batches", "admissions"),
			Map.entry("promotion_runs", "yearchange"),
			Map.entry("promotion_run_items", "yearchange"),
			Map.entry("promotion_corrections", "yearchange"));

	/**
	 * Reads of another module's table that were approved, as "module:table": the audit log shows the user's name, and
	 * the Students list shows the Total fee of the shown class record (constitution VII 1.1.0, F2 plan Complexity
	 * Tracking). Adding to this list is a decision for the project owner.
	 */
	private static final Set<String> ALLOWED_TABLE_READS = Set.of("audit:users", "students:fee_accounts",
			"students:v_fee_position", "students:v_earlier_pending");

	private static final Pattern SQL_USE = Pattern.compile("(?i:\\b(?:from|join|into|update|truncate|table))\\s+\"?("
			+ String.join("|", TABLE_OWNER.keySet().stream().sorted(Comparator.reverseOrder()).toList())
			+ ")(?![\\w])");

	private static final Pattern TABLE_ANNOTATION = Pattern.compile("@Table\\s*\\(\\s*name\\s*=\\s*\"(\\w+)\"");

	private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

	@Test
	void modulesOnlyImportAnotherModulesPublicApi() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Source source : sources()) {
			for (String imported : source.imports()) {
				String target = module(imported);
				if (target == null || target.equals(source.module()) || isPublic(imported)) {
					continue;
				}
				violations.add(source.file() + " imports " + BASE + imported);
			}
		}
		assertThat(violations).as("imports of another module's internals").isEmpty();
	}

	@Test
	void moduleGraphHasNoCycleAndOnlyAllowedEdges() throws IOException {
		Map<String, Set<String>> graph = graph();
		List<String> unexpected = new ArrayList<>();
		graph.forEach((from, targets) -> targets.stream()
			.filter(to -> !ALLOWED.getOrDefault(from, Set.of()).contains(to))
			.forEach(to -> unexpected.add(from + " -> " + to)));
		assertThat(unexpected).as("module dependencies that are not in the allowed graph").isEmpty();
		assertThat(findCycle(graph)).as("cycle between modules").isEmpty();
	}

	/** Test-profile seeding endpoints that must write another module's tables to build fixtures. */
	private static final Set<String> TABLE_SCAN_EXEMPT_FILES = Set.of("fees/FeeTestSupportController.java");

	@Test
	void aModuleReadsOnlyItsOwnTablesApartFromTheApprovedReads() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path file : javaFiles()) {
			Path relative = SOURCES.relativize(file);
			if (relative.getNameCount() < 2 || TABLE_SCAN_EXEMPT_FILES.contains(relative.toString().replace('\\', '/'))) {
				continue;
			}
			String module = relative.getName(0).toString();
			for (String table : foreignTables(module, Files.readString(file))) {
				if (!ALLOWED_TABLE_READS.contains(module + ":" + table)) {
					violations.add(relative.toString().replace('\\', '/') + " uses table " + table + " of module "
							+ TABLE_OWNER.get(table));
				}
			}
		}
		assertThat(violations).as("SQL on another module's table").isEmpty();
	}

	@Test
	void theTableScannerFindsSqlOnAnotherModulesTableAndIgnoresComments() {
		String students = "String a = \"select * from fee_accounts f join student_enrollments e on e.id = f.enrollment_id\";\n"
				+ "// delete from fee_rows\n/* update users set x = 1 */\nString b = \"DELETE FROM student_photos\";\n";

		assertThat(foreignTables("students", students)).containsExactly("fee_accounts");
		assertThat(foreignTables("fees", students)).containsExactlyInAnyOrder("student_enrollments", "student_photos");
		assertThat(foreignTables("fees", "@Table(name = \"fee_rows\") class A {}")).isEmpty();
		assertThat(foreignTables("students", "@Table(name = \"fee_rows\") class A {}")).containsExactly("fee_rows");
		assertThat(foreignTables("students", "String m = \"These students need an update from the office\";")).isEmpty();
		assertThat(foreignTables("masterdata", "\"from student_categories sc join standards s\"")).isEmpty();
		assertThat(foreignTables("security", "\"insert into users\" + \"select 1 from students_view\"")).isEmpty();
	}

	@Test
	void theStudentsListMayReadOnlyTheFeeAccountAndTheTwoFeeViews() {
		assertThat(ALLOWED_TABLE_READS).containsExactlyInAnyOrder("audit:users", "students:fee_accounts",
				"students:v_fee_position", "students:v_earlier_pending");
		for (String table : new String[] { "receipts", "receipt_marks", "fee_brought_forward", "v_valid_receipts" }) {
			assertThat(ALLOWED_TABLE_READS).doesNotContain("students:" + table);
			assertThat(foreignTables("students", "String s = \"select * from " + table + "\";")).containsExactly(table);
		}
		assertThat(foreignTables("students", "\"select 1 from v_fee_position f join v_earlier_pending p on 1=1\""))
			.containsExactlyInAnyOrder("v_fee_position", "v_earlier_pending");
		assertThat(foreignTables("fees", "\"select 1 from receipts r join receipt_marks m on 1=1\"")).isEmpty();
	}

	@Test
	void aFullyQualifiedNameInTheCodeCountsAsAReference() {
		String code = "package com.admin.management.students;\nimport com.admin.management.shared.web.ApiProblem;\n"
				+ "class A { Object o = new com.admin.management.audit.AuditRepository(); }\n";

		assertThat(references(code)).containsExactly("shared.web.ApiProblem", "audit.AuditRepository");
	}

	@Test
	void cycleDetectionFindsACycle() {
		Map<String, Set<String>> cyclic = Map.of("a", Set.of("b"), "b", Set.of("c"), "c", Set.of("a"));
		assertThat(findCycle(cyclic)).isNotEmpty();
		assertThat(findCycle(Map.of("a", Set.of("b"), "b", Set.of()))).isEmpty();
	}

	private static boolean isPublic(String imported) {
		// a package entry ends with a dot; a class entry also allows the types nested in it
		return PUBLIC.stream().anyMatch(prefix -> prefix.endsWith(".") ? imported.startsWith(prefix)
				: imported.equals(prefix) || imported.startsWith(prefix + "."));
	}

	private static String module(String imported) {
		int dot = imported.indexOf('.');
		return dot < 0 ? null : imported.substring(0, dot);
	}

	private static Map<String, Set<String>> graph() throws IOException {
		Map<String, Set<String>> graph = new TreeMap<>();
		for (Source source : sources()) {
			Set<String> targets = graph.computeIfAbsent(source.module(), key -> new TreeSet<>());
			for (String imported : source.imports()) {
				String target = module(imported);
				if (target != null && !target.equals(source.module())) {
					targets.add(target);
				}
			}
		}
		return graph;
	}

	/** A path that closes a cycle, or an empty list. */
	private static List<String> findCycle(Map<String, Set<String>> graph) {
		Set<String> done = new TreeSet<>();
		for (String start : graph.keySet()) {
			List<String> found = visit(start, graph, new ArrayList<>(), done);
			if (!found.isEmpty()) {
				return found;
			}
		}
		return Collections.emptyList();
	}

	private static List<String> visit(String node, Map<String, Set<String>> graph, List<String> path, Set<String> done) {
		if (path.contains(node)) {
			List<String> cycle = new ArrayList<>(path.subList(path.indexOf(node), path.size()));
			cycle.add(node);
			return cycle;
		}
		if (done.contains(node)) {
			return Collections.emptyList();
		}
		path.add(node);
		for (String next : graph.getOrDefault(node, Set.of())) {
			List<String> found = visit(next, graph, path, done);
			if (!found.isEmpty()) {
				return found;
			}
		}
		path.remove(path.size() - 1);
		done.add(node);
		return Collections.emptyList();
	}

	/** The part after {@code com.admin.management.} of every reference in the text, whether imported or spelled out. */
	static List<String> references(String text) {
		List<String> found = new ArrayList<>();
		Matcher matcher = REFERENCE.matcher(PACKAGE_LINE.matcher(text).replaceAll(""));
		while (matcher.find()) {
			found.add(matcher.group(1));
		}
		return found;
	}

	/**
	 * The tables of other modules that the source text uses in SQL ({@code from}, {@code join}, {@code into},
	 * {@code update}, {@code truncate}, {@code table} followed by the name) or names in {@code @Table}. Comments are
	 * left out, and a bare word such as "students" in a message does not count.
	 */
	static List<String> foreignTables(String module, String text) {
		String code = COMMENTS.matcher(text).replaceAll("");
		Set<String> found = new TreeSet<>();
		Matcher use = SQL_USE.matcher(code);
		while (use.find()) {
			found.add(use.group(1));
		}
		Matcher annotation = TABLE_ANNOTATION.matcher(code);
		while (annotation.find()) {
			if (TABLE_OWNER.containsKey(annotation.group(1))) {
				found.add(annotation.group(1));
			}
		}
		return found.stream().filter(table -> !TABLE_OWNER.get(table).equals(module)).toList();
	}

	private static List<Path> javaFiles() throws IOException {
		assertThat(SOURCES).as("run the test from the management folder").isDirectory();
		try (Stream<Path> files = Files.walk(SOURCES)) {
			return files.filter(path -> path.toString().endsWith(".java")).toList();
		}
	}

	private record Source(String file, String module, List<String> imports) {
	}

	private static List<Source> sources() throws IOException {
		assertThat(SOURCES).as("run the test from the management folder").isDirectory();
		List<Source> result = new ArrayList<>();
		try (Stream<Path> files = Files.walk(SOURCES)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				Path relative = SOURCES.relativize(file);
				if (relative.getNameCount() < 2) {
					continue; // the application class in the base package belongs to no module
				}
				List<String> imports = references(Files.readString(file));
				result.add(new Source(relative.toString().replace('\\', '/'), relative.getName(0).toString(), imports));
			}
		}
		return result;
	}

}
