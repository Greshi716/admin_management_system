package com.admin.management.fees;

import java.util.List;

/**
 * The standards and categories the row form lists: the active ones, and any inactive one a row of the year holds. An
 * inactive one is flagged so the form offers it only in the row that holds it (FR-001).
 */
public record FeeChoicesDto(List<StandardChoice> standards, List<CategoryChoice> categories) {

	public record StandardChoice(Long id, String name, int displayOrder, boolean active) {
	}

	public record CategoryChoice(Long id, String code, String name, boolean active) {
	}

}
