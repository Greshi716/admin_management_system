package com.admin.management.yearchange;

/**
 * Result of a promotion run (FR-016).
 */
public record PromoteResult(
		long runId,
		int promoted,
		int detained,
		int left,
		String carriedTotal) {
}
