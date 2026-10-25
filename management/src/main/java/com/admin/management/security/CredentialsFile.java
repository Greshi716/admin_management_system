package com.admin.management.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The one-time file that holds the generated Admin password. It exists only until the admin changes the
 * password (FR-009); a developer reset writes it again (FR-045).
 */
@Component
public class CredentialsFile {

	public static final String NAME = "credentials-first-run.txt";

	private final Path path;

	CredentialsFile(@Value("${campusflow.data-dir}") String dataDir) {
		this.path = Path.of(dataDir).toAbsolutePath().normalize().resolve(NAME);
	}

	public Path path() {
		return path;
	}

	public void write(String username, String password) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, "CampusFlow first sign-in. This file is deleted when the password is changed.\n"
					+ "Username: " + username + "\nPassword: " + password + "\n");
			try {
				Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
			}
			catch (UnsupportedOperationException ex) {
				// Windows: the file keeps the permissions of its folder.
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not write " + path, ex);
		}
	}

	public void deleteIfExists() {
		try {
			Files.deleteIfExists(path);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not delete " + path, ex);
		}
	}

}
