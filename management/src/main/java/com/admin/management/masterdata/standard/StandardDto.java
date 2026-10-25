package com.admin.management.masterdata.standard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Request and response body of a standard (contracts/openapi.yaml {@code Standard}). */
public record StandardDto(Long id, @NotBlank(message = "Enter the standard name.") @Size(max = 100, message = "The name can have at most 100 characters.") String name,
		@NotNull(message = "Enter the display order.") Integer displayOrder,
		@Pattern(regexp = "Science|Commerce", message = "The stream must be Science or Commerce.") String stream,
		Long version, Boolean active) {

	static StandardDto of(Standard standard) {
		return new StandardDto(standard.getId(), standard.getName(), standard.getDisplayOrder(), standard.getStream(),
				standard.getVersion(), standard.isActive());
	}

}
