package com.admin.management.masterdata.academicyear;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request and response body of an academic year (contracts/openapi.yaml {@code AcademicYear}). {@code version}
 * is required when editing; {@code current} is worked out from today's date and ignored on input.
 */
public record AcademicYearDto(Long id, @NotBlank(message = "Enter the academic year name.") String name,
		@NotNull(message = "Enter the start date.") LocalDate startDate,
		@NotNull(message = "Enter the end date.") LocalDate endDate, Long version,
		@JsonProperty(access = JsonProperty.Access.READ_ONLY) Boolean current) {

	static AcademicYearDto of(AcademicYear year, LocalDate today) {
		boolean current = !today.isBefore(year.getStartDate()) && !today.isAfter(year.getEndDate());
		return new AcademicYearDto(year.getId(), year.getName(), year.getStartDate(), year.getEndDate(),
				year.getVersion(), current);
	}

}
