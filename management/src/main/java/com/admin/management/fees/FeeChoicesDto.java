package com.admin.management.fees;

import java.util.List;

/** The standards and categories the row form offers: the active ones, and any inactive one a row of the year holds. */
public record FeeChoicesDto(List<StandardChoice> standards, List<CategoryChoice> categories) {

	public record StandardChoice(Long id, String name, int displayOrder) {
	}

	public record CategoryChoice(Long id, String code, String name) {
	}

}
