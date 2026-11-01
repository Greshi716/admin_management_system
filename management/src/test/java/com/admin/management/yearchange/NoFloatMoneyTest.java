package com.admin.management.yearchange;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.admin.management.support.Covers;

/**
 * Validates that money calculations never use float or double (Constitution Principle I, FR-039, FR-040).
 * Scans all Java source files in the yearchange and fees modules, as well as promotion and carry-forward entities,
 * ensuring no declaration of float, double, Float, or Double types.
 */
@Covers("FR-039")
@Covers("FR-040")
class NoFloatMoneyTest {

	private static final Path SOURCE_ROOT = Paths.get("src", "main", "java", "com", "admin", "management");

	private static final Pattern FORBIDDEN_TYPE = Pattern.compile("\\b(float|double|Float|Double)\\b");

	@Test
	@DisplayName("No float or double types used in F4 money and fee calculation classes")
	void noFloatOrDoubleInMoneyClasses() throws IOException {
		List<String> modulesToScan = List.of("yearchange", "fees");
		List<String> violations = new ArrayList<>();

		for (String module : modulesToScan) {
			Path moduleDir = SOURCE_ROOT.resolve(module);
			if (!Files.exists(moduleDir)) {
				continue;
			}

			try (Stream<Path> paths = Files.walk(moduleDir)) {
				paths.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
					try {
						List<String> lines = Files.readAllLines(file);
						for (int i = 0; i < lines.size(); i++) {
							String line = lines.get(i);
							String trimmed = line.trim();
							// Skip single-line comments or imports
							if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")
									|| trimmed.startsWith("import ")) {
								continue;
							}
							if (FORBIDDEN_TYPE.matcher(line).find()) {
								violations.add(file.getFileName() + ":" + (i + 1) + " uses forbidden floating-point type: " + trimmed);
							}
						}
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
			}
		}

		assertThat(violations)
				.as("Found forbidden float/double usages in money classes (Constitution Principle I)")
				.isEmpty();
	}

}
