package com.admin.management.shared.masking;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Keeps sensitive values out of audit entries (FR-025, constitution Principle VI). */
public final class SensitiveMasker {

	/** Fields that are never stored in an audit entry, not even masked. */
	public static final Set<String> NEVER_STORE = Set.of("password", "passwordHash");

	/** Fields whose values are stored masked in an audit entry (FR-025): last 4 characters visible. */
	public static final Set<String> MASKED = Set.of("aadhaar", "aadhaarNumber", "bankAccount", "accountNumber");

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

	/** Returns a copy of {@code changes} without the {@link #NEVER_STORE} fields, at any depth. */
	public static Map<String, Object> withoutNeverStored(Map<String, Object> changes) {
		return sanitize(changes, Set.of());
	}

	/**
	 * Returns a copy of {@code changes} for the audit log: {@link #NEVER_STORE} fields are dropped and string
	 * values of {@code sensitiveKeys} are masked, at any depth of nested maps and lists. Masking is idempotent,
	 * so a value a caller already masked stays as it is.
	 */
	public static Map<String, Object> sanitize(Map<String, Object> changes, Set<String> sensitiveKeys) {
		Map<String, Object> result = new LinkedHashMap<>();
		if (changes == null) {
			return result;
		}
		changes.forEach((key, value) -> {
			if (!NEVER_STORE.contains(key)) {
				result.put(key, sanitizeValue(key, value, sensitiveKeys));
			}
		});
		return result;
	}

	private static Object sanitizeValue(String key, Object value, Set<String> sensitiveKeys) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> copy = new LinkedHashMap<>();
			map.forEach((k, v) -> copy.put(String.valueOf(k), v));
			return sanitize(copy, sensitiveKeys);
		}
		if (value instanceof List<?> list) {
			return list.stream().map((item) -> sanitizeValue(key, item, sensitiveKeys)).toList();
		}
		if (value instanceof String text && sensitiveKeys.contains(key)) {
			return mask(text);
		}
		return value;
	}

}
