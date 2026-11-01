package com.admin.management.admissions;

/**
 * Payload for rejecting an admission request (FR-033, FR-034).
 */
public record RejectInput(
		String remarks,
		Long version
) {
}
