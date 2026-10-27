package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One fee row with the names of its standard and category; {@code categoryId} null means all categories. */
public record FeeRowDto(Long id, Long version, Long standardId, String standardName, Long categoryId,
		String categoryName, BigDecimal amount, BigDecimal registrationFee, LocalDate dueDate) {

	/** What the screen shows for a row that has no category. */
	static final String ALL_CATEGORIES = "All categories";

}
