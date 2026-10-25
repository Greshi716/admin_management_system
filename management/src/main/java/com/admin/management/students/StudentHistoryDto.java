package com.admin.management.students;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The History block of the profile (contracts/openapi.yaml {@code HistoryResult}). {@code truncated} is true when
 * older entries exist; they stay in the Audit log. Sensitive values are masked by the audit service.
 */
public record StudentHistoryDto(List<Entry> entries, boolean truncated) {

	/** {@code record} names the changed record, for example {@code Student#12} or {@code StudentEnrollment#40}. */
	public record Entry(Instant at, String userName, String action, String record, Map<String, Object> changes) {
	}

}
