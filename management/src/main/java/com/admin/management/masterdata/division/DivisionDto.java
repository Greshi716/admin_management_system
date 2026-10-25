package com.admin.management.masterdata.division;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

/**
 * Request and response body of a division (contracts/openapi.yaml {@code Division}). The year and standard names
 * and the active flag are only sent back, for display.
 */
public record DivisionDto(Long id, @NotNull(message = "Choose the academic year.") Long academicYearId,
		@NotNull(message = "Choose the standard.") Long standardId,
		@NotBlank(message = "Enter the division name.") @Size(max = 100, message = "The name can have at most 100 characters.") String name, Long version,
		@JsonProperty(access = JsonProperty.Access.READ_ONLY) String academicYearName,
		@JsonProperty(access = JsonProperty.Access.READ_ONLY) String standardName,
		@JsonProperty(access = JsonProperty.Access.READ_ONLY) Boolean active) {

	static DivisionDto of(Division division) {
		return new DivisionDto(division.getId(), division.getAcademicYear().getId(), division.getStandard().getId(),
				division.getName(), division.getVersion(), division.getAcademicYear().getName(),
				division.getStandard().getName(), division.isActive());
	}

}
