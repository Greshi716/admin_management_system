package com.admin.management.yearchange;

import java.util.List;

/**
 * Input for promoting students of one standard (FR-006 to FR-012).
 */
public record PromoteInput(List<Line> lines) {

	public record Line(
			Long enrollmentId,
			String outcome,
			Long divisionId,
			Long categoryId,
			String stream,
			Long version) {
	}

}
