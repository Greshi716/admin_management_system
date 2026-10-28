package com.admin.management.fees;

import java.util.List;

/**
 * The fee structure of one academic year as the screen shows it. {@code version} is null while the year has no
 * status row yet; {@code studentCount} is the number of class records of the year, which is what a revise would
 * recalculate.
 */
public record FeeStructureDto(Long yearId, String yearName, String status, Long version, long studentCount,
		List<FeeRowDto> rows) {
}
