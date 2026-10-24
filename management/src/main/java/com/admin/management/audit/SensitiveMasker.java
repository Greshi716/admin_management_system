package com.admin.management.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Keeps sensitive values out of audit entries (FR-025, constitution Principle VI). */
public final class SensitiveMasker {

	/** Fields that are never stored in an audit entry, not even masked. */
	public static final Set<String> NEVER_STORE = Set.of("password", "passwordHash");

	private static final int VISIBLE = 4;

	private SensitiveMasker() {
	}

	/** Keeps the last 4 characters and replaces the rest with {@code *}; shorter values are fully masked. */
	public static String mask(String value) {
		if (value == null) {
			return null;
		}
		int length = value.length();
		if (length <= VISIBLE) {
			return "*".repeat(length);
		}
		return "*".repeat(length - VISIBLE) + value.substring(length - VISIBLE);
	}

	/** Returns a copy of {@code changes} without the {@link #NEVER_STORE} fields. */
	public static Map<String, Object> withoutNeverStored(Map<String, Object> changes) {
		Map<String, Object> result = new LinkedHashMap<>();
		if (changes == null) {
			return result;
		}
		changes.forEach((key, value) -> {
			if (!NEVER_STORE.contains(key)) {
				result.put(key, value);
			}
		});
		return result;
	}

}
