package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The Fees block of a student's profile for one year (FR-009). {@code state} is FEE_SET, FEE_NOT_SET (the year is
 * finalised but no row fits) or NOT_FINALISED (no account yet). {@code totalDue} is the total fee plus the
 * brought-forward amount, or the brought-forward amount alone when there is no fee amount. {@code balance} (F3) holds paid, pending,
 * the total fee with the brought-forward amount, excess paid and "Paid up to", and {@code receipts} lists the student's
 * receipts with their status (FR-014).
 */
public record StudentFeesDto(Long yearId, String yearName, String state, BigDecimal yearFee,
		BigDecimal registrationFee, BigDecimal totalFee, LocalDate dueDate, BroughtForwardDto broughtForward,
		BigDecimal totalDue, FeeBalance balance, java.util.List<ReceiptSummaryDto> receipts,
		String broughtForwardMarker) {

	public StudentFeesDto(Long yearId, String yearName, String state, BigDecimal yearFee,
			BigDecimal registrationFee, BigDecimal totalFee, LocalDate dueDate, BroughtForwardDto broughtForward,
			BigDecimal totalDue, FeeBalance balance, java.util.List<ReceiptSummaryDto> receipts) {
		this(yearId, yearName, state, yearFee, registrationFee, totalFee, dueDate, broughtForward, totalDue, balance,
				receipts, "brought forward");
	}

	static final String FEE_SET = "FEE_SET";

	static final String FEE_NOT_SET = "FEE_NOT_SET";

	static final String NOT_FINALISED = "NOT_FINALISED";

	/** {@code version} is null while the student has no brought-forward amount; the amount is then 0.00. */
	public record BroughtForwardDto(Long version, BigDecimal amount, String note, String marker) {
		public BroughtForwardDto(Long version, BigDecimal amount, String note) {
			this(version, amount, note, "brought forward");
		}
	}

}
