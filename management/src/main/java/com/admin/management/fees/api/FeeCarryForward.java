package com.admin.management.fees.api;

import java.math.BigDecimal;

/**
 * Public interface for carrying forward unpaid year fees into the student's single brought forward amount
 * (FR-020, FR-021, FR-022, FR-024).
 */
public interface FeeCarryForward {

	/**
	 * Previews the unpaid year fee for an enrollment from {@code v_fee_position}.
	 * Returns 0 if fee is not set or fully paid. Writes nothing.
	 */
	BigDecimal preview(long enrollmentId);

	/**
	 * Carries forward the unpaid year fee for a promoted or detained student from {@code oldYearId} into {@code newYearId}.
	 * Locks the student, upserts {@code fee_brought_forward}, and records a Fees audit entry.
	 */
	CarryForwardResult carryForward(long studentId, long oldYearId, long newYearId, long fromEnrollmentId);

	record CarryForwardResult(BigDecimal carriedAmount, BigDecimal broughtForwardBefore, BigDecimal broughtForwardAfter) {
	}

}
