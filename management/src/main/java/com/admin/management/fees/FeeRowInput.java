package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A fee row to add or save. {@code version} is the row version (needed to edit); {@code structureVersion} is the
 * version of the year's structure the screen loaded, null while the year had no status row. Amounts are read as
 * exact decimals, so a value with too many places can be refused instead of rounded.
 */
public record FeeRowInput(Long version, Long structureVersion, Long standardId, Long categoryId, BigDecimal amount,
		BigDecimal registrationFee, LocalDate dueDate) {
}
