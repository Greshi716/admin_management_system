package com.admin.management.fees;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The "fees paid up to" date of a class record (FR-012). It counts from the academic year's first day: the days paid
 * are the year fee paid divided by the fee of one day. The division is done once, as
 * {@code paid * days / fee} rounded half up to a whole day, so no rounded per-day rate enters the result. The
 * registration fee is not part of it. A payment too small for one whole day still shows the start date (plan Q11,
 * the default until the owner answers), and the date never goes past the year's last day.
 */
public record PaidUpTo(State state, LocalDate lastPaidDay) {

	public enum State {

		NOTHING_PAID, DATE, FULLY_PAID

	}

	private static final PaidUpTo NOTHING = new PaidUpTo(State.NOTHING_PAID, null);

	private static final PaidUpTo FULL = new PaidUpTo(State.FULLY_PAID, null);

	/**
	 * @param yearFee the year fee of the class record, without the registration fee
	 * @param paidYearFee the year fee paid by valid receipts
	 * @throws IllegalArgumentException for a missing value, an end before the start or a negative amount
	 */
	public static PaidUpTo of(LocalDate yearStart, LocalDate yearEnd, BigDecimal yearFee, BigDecimal paidYearFee) {
		if (yearStart == null || yearEnd == null || yearFee == null || paidYearFee == null) {
			throw new IllegalArgumentException("Year dates, year fee and paid amount are required.");
		}
		if (yearEnd.isBefore(yearStart)) {
			throw new IllegalArgumentException("The year ends before it starts.");
		}
		if (yearFee.signum() < 0 || paidYearFee.signum() < 0) {
			throw new IllegalArgumentException("Amounts cannot be negative.");
		}
		if (yearFee.signum() == 0 || paidYearFee.compareTo(yearFee) >= 0) {
			return FULL;
		}
		if (paidYearFee.signum() == 0) {
			return NOTHING;
		}
		long days = ChronoUnit.DAYS.between(yearStart, yearEnd) + 1;
		long daysPaid = paidYearFee.multiply(BigDecimal.valueOf(days))
			.divide(yearFee, 0, RoundingMode.HALF_UP)
			.longValueExact();
		daysPaid = Math.min(Math.max(daysPaid, 1), days);
		return new PaidUpTo(State.DATE, yearStart.plusDays(daysPaid - 1));
	}

}
