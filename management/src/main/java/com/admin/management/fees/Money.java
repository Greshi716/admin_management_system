package com.admin.management.fees;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money is BigDecimal with exactly two decimals; a value with more decimals is refused, never rounded (F2 FR-011, F3 FR-023). */
final class Money {

	/** NUMERIC(12,2) holds ten digits before the decimal point. */
	static final int MAX_INTEGER_DIGITS = 10;

	static final String TOO_LARGE_MESSAGE = "The amount is too large. Use at most 10 digits before the decimal point.";

	private Money() {
	}

	/**
	 * The amount with scale 2, or null for null.
	 *
	 * @throws ArithmeticException when the amount has a non-zero digit beyond the second decimal
	 */
	static BigDecimal scale2(BigDecimal amount) {
		return amount == null ? null : amount.setScale(2, RoundingMode.UNNECESSARY);
	}

	/**
	 * Why an amount typed by the admin is not acceptable (negative, more than two decimals, more than ten digits before
	 * the point), or null when it is. Nothing is rounded (FR-023).
	 */
	static String problem(BigDecimal value) {
		if (value.signum() < 0) {
			return "The amount cannot be negative.";
		}
		if (value.stripTrailingZeros().scale() > 2) {
			return "Use at most two decimal places.";
		}
		if (value.precision() - value.scale() > MAX_INTEGER_DIGITS) {
			return TOO_LARGE_MESSAGE;
		}
		return null;
	}

	/** The amount as the plain text kept in the audit log, such as 28000.00; null stays null. */
	static String text(BigDecimal amount) {
		return amount == null ? null : scale2(amount).toPlainString();
	}

}
