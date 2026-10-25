package com.admin.management.masterdata.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request and response body of a student category (contracts/openapi.yaml {@code StudentCategory}).
 * {@code affectsFees} has no default: the admin has to choose Yes or No.
 */
public record StudentCategoryDto(Long id, @NotBlank(message = "Enter the category code.") String code,
		@NotBlank(message = "Enter the category name.") String name,
		@NotNull(message = "Choose whether this category affects fees.") Boolean affectsFees, Long version,
		Boolean active) {

	static StudentCategoryDto of(StudentCategory category) {
		return new StudentCategoryDto(category.getId(), category.getCode(), category.getName(),
				category.isAffectsFees(), category.getVersion(), category.isActive());
	}

}
