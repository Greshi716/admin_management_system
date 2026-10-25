package com.admin.management.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
 * Guards the modular monolith (constitution VII, research R7): reads the {@code import com.admin.management.*}
 * lines of the main sources, then fails on a dependency cycle between modules or on an import of another module's
 * internals. A module is the first package below {@code com.admin.management}.
 */
@Covers("FR-016")
class ModuleDependencyTest {

	private static final String BASE = "com.admin.management.";

	private static final Path SOURCES = Path.of("src/main/java/com/admin/management");

	private static final Pattern IMPORT = Pattern
		.compile("^import\\s+(?:static\\s+)?com\\.admin\\.management\\.([\\w.]+?)(?:\\.\\*)?;", Pattern.MULTILINE);

	/** What any module may import from another module. Everything else of another module is internal. */
	private static final List<String> PUBLIC = List.of("shared.", "audit.AuditService", "audit.AuditExportRecorder",
			"masterdata.api.");

	/** Edges the architecture allows today (research R7). */
	private static final Map<String, Set<String>> ALLOWED = Map.of("audit", Set.of("shared"), "masterdata",
			Set.of("audit", "shared"), "security", Set.of("audit", "shared"), "students",
			Set.of("shared", "audit", "masterdata"), "shared", Set.of());

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
				String text = Files.readString(file);
				List<String> imports = new ArrayList<>();
				Matcher matcher = IMPORT.matcher(text);
				while (matcher.find()) {
					imports.add(matcher.group(1));
				}
				result.add(new Source(relative.toString().replace('\\', '/'), relative.getName(0).toString(), imports));
			}
		}
		return result;
	}

}
