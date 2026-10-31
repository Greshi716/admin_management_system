package com.admin.management.yearchange;

import java.util.List;

/**
 * Screen data for promoting students of one standard (FR-006 to FR-012, FR-019).
 */
public record PromoteScreenDto(
		String fromYear,
		String toYear,
		StandardRef nextStandard,
		List<String> streamChoices,
		List<DivisionRef> divisions,
		List<CategoryRef> categories,
		List<StudentItemDto> students) {

	public record StandardRef(long id, String name, boolean usesStream) {
	}

	public record DivisionRef(long id, String name, long standardId) {
	}

	public record CategoryRef(long id, String name) {
	}

	public record StudentItemDto(
			long studentId,
			long enrollmentId,
			String grNo,
			String name,
			String division,
			String category,
			String unpaidYearFee,
			String defaultOutcome,
			Long defaultDivisionId,
			String defaultStream,
			long version) {
	}

}
