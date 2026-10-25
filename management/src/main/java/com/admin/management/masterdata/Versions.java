package com.admin.management.masterdata;

import java.util.function.Supplier;

import com.admin.management.shared.web.StaleVersionException;
import com.admin.management.shared.web.ValidationException;

/** The explicit version check every master data edit makes before changing a record (FR-046). */
public final class Versions {

	private Versions() {
	}

	/**
	 * Refuses an edit made on an older copy of the record. {@code latest} builds the response that goes back to
	 * the screen, so the admin sees the current values.
	 */
	public static void check(Long sent, long actual, Supplier<Object> latest) {
		if (sent == null) {
			throw new ValidationException("version", "The record version is required when saving an edit.");
		}
		if (sent != actual) {
			throw new StaleVersionException(latest.get());
		}
	}

}
