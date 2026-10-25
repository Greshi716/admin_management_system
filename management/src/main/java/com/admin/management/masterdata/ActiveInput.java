package com.admin.management.masterdata;

import jakarta.validation.constraints.NotNull;

/** Body of {@code PUT /{id}/active}: mark a standard, division or category active or inactive (FR-020). */
public record ActiveInput(@NotNull(message = "Choose active or inactive.") Boolean active,
		@NotNull(message = "The record version is required.") Long version) {
}
