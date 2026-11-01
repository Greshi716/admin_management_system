package com.admin.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.admin.management.support.Covers;

/**
 * Validates module boundaries and ensures acyclic dependencies (constitution VII, FR-039):
 * yearchange and admissions must depend only on *.api packages of other modules,
 * and existing modules must not depend on yearchange or admissions.
 */
@Covers("FR-039")
class ModuleBoundaryTest {

	private static final Path SOURCE_ROOT = Paths.get("src", "main", "java", "com", "admin", "management");

	@Test
	void yearchangeAndAdmissionsDependOnlyOnApiPackages() throws IOException {
		List<String> violations = new ArrayList<>();

		for (String module : List.of("yearchange", "admissions")) {
			Path moduleDir = SOURCE_ROOT.resolve(module);
			if (!Files.exists(moduleDir)) {
				continue;
			}

			try (Stream<Path> files = Files.walk(moduleDir)) {
				files.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
					try {
						List<String> lines = Files.readAllLines(file);
						for (String line : lines) {
							String trimmed = line.trim();
							if (!trimmed.startsWith("import com.admin.management.")) {
								continue;
							}
							String imported = trimmed.substring("import ".length(), trimmed.indexOf(';'));

							// Disallowed: direct imports of internal packages of students, fees, masterdata
							if (imported.startsWith("com.admin.management.students.")
									&& !imported.startsWith("com.admin.management.students.api.")) {
								violations.add(file.getFileName() + " imports internal: " + imported);
							}
							if (imported.startsWith("com.admin.management.fees.")
									&& !imported.startsWith("com.admin.management.fees.api.")) {
								violations.add(file.getFileName() + " imports internal: " + imported);
							}
							if (imported.startsWith("com.admin.management.masterdata.")
									&& !imported.startsWith("com.admin.management.masterdata.api.")) {
								violations.add(file.getFileName() + " imports internal: " + imported);
							}
						}
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
			}
		}

		assertThat(violations).as("Module boundary violations in yearchange/admissions").isEmpty();
	}

	@Test
	void existingModulesDoNotDependOnYearChangeOrAdmissions() throws IOException {
		List<String> violations = new ArrayList<>();
		List<String> coreModules = List.of("students", "fees", "masterdata", "audit", "security", "shared");

		for (String module : coreModules) {
			Path moduleDir = SOURCE_ROOT.resolve(module);
			if (!Files.exists(moduleDir)) {
				continue;
			}

			try (Stream<Path> files = Files.walk(moduleDir)) {
				files.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
					try {
						List<String> lines = Files.readAllLines(file);
						for (String line : lines) {
							String trimmed = line.trim();
							if (!trimmed.startsWith("import com.admin.management.")) {
								continue;
							}
							String imported = trimmed.substring("import ".length(), trimmed.indexOf(';'));

							if (imported.startsWith("com.admin.management.yearchange.")
									|| imported.startsWith("com.admin.management.admissions.")) {
								violations.add(file.getFileName() + " imports from new module: " + imported);
							}
						}
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
			}
		}

		assertThat(violations).as("Inverted dependencies on yearchange/admissions").isEmpty();
	}

}
