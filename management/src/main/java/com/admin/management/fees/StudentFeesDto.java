package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The Fees block of a student's profile for one year (FR-009). {@code state} is FEE_SET, FEE_NOT_SET (the year is
 * finalised but no row fits) or NOT_FINALISED (no account yet). {@code totalDue} is the total fee plus the
 * brought-forward amount, or the brought-forward amount alone when there is no fee amount. It holds no receipt, paid
 * or pending figure: those arrive with receipts.
 */
public record StudentFeesDto(Long yearId, String yearName, String state, BigDecimal yearFee,
		BigDecimal registrationFee, BigDecimal totalFee, LocalDate dueDate, BroughtForwardDto broughtForward,
		BigDecimal totalDue) {

	static final String FEE_SET = "FEE_SET";

	static final String FEE_NOT_SET = "FEE_NOT_SET";

	static final String NOT_FINALISED = "NOT_FINALISED";

	/** {@code version} is null while the student has no brought-forward amount; the amount is then 0.00. */
	public record BroughtForwardDto(Long version, BigDecimal amount, String note) {
	}

}
