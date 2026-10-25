package com.admin.management.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Temporary application data directory for tests that read or write the credentials file. */
final class BootstrapTestFiles {

	private BootstrapTestFiles() {
	}

	static Path newDataDir() {
		try {
			Path dir = Files.createTempDirectory("campusflow-test-data");
			dir.toFile().deleteOnExit();
			return dir;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
