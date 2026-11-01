package com.admin.management.yearchange;

import java.util.List;

/**
 * Status of year change: whether promotion warning is active and standards still to promote (FR-006, FR-014).
 */
public record YearChangeStatusDto(
		boolean active,
		String newYear,
		String oldYear,
		List<PendingStandardDto> standards) {

	public record PendingStandardDto(long standardId, String name, int pending) {
	}

}
