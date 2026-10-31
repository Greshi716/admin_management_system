package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What a student owes and has paid, worked out from the valid receipts every time and never stored (FR-016). Built
 * from a {@link Position}: the amounts of the fee account of one class record, the brought-forward amount and the
 * paid sums read from the views. Pure, so the preview can add a receipt that is not saved yet.
 */
public record FeeBalance(LocalDate dueDate, BigDecimal yearFee, BigDecimal registrationFee, BigDecimal broughtForward,
		BigDecimal totalFee, BigDecimal paid, BigDecimal pending, BigDecimal yearFeeDue, BigDecimal registrationDue,
		BigDecimal earlierYearsPending, BigDecimal excessPaid, PaidUpToView paidUpTo, BigDecimal previousYearsPaid) {

	static final BigDecimal ZERO = new BigDecimal("0.00");

	/** The "Fees paid up to" value for the screen: NOTHING, DATE (the last paid day) or FULL (the year's last day). */
	public record PaidUpToView(String state, LocalDate date) {

		static final String NOTHING = "NOTHING";

		static final String DATE = "DATE";

		static final String FULL = "FULL";

		/** The value as text for the audit entry: the last paid day, or the words for fully paid and nothing paid. */
		String text() {
			return switch (state) {
				case NOTHING -> "Nothing paid";
				case FULL -> "Fully paid for the year";
				default -> String.valueOf(date);
			};
		}

	}

	/**
	 * The raw figures. {@code feeSet} is true when the class record has a fee account with an amount; then the year
	 * dates are present. Paid sums count valid receipts only.
	 */
	public record Position(boolean feeSet, BigDecimal yearFee, BigDecimal registrationFee, LocalDate dueDate,
			LocalDate yearStart, LocalDate yearEnd, BigDecimal paidYearFee, BigDecimal paidRegistration,
			BigDecimal broughtForward, BigDecimal paidPrevious) {

		/** The position after a receipt that is not saved yet. A previous-years receipt has no registration part. */
		public Position withReceipt(Receipt.Type type, BigDecimal registrationPaid, BigDecimal feePaid) {
			if (type == Receipt.Type.PREVIOUS_YEARS) {
				return new Position(feeSet, yearFee, registrationFee, dueDate, yearStart, yearEnd, paidYearFee,
						paidRegistration, broughtForward, paidPrevious.add(feePaid));
			}
			return new Position(feeSet, yearFee, registrationFee, dueDate, yearStart, yearEnd,
					paidYearFee.add(feePaid), paidRegistration.add(registrationPaid), broughtForward, paidPrevious);
		}

	}

	public static FeeBalance of(Position p) {
		BigDecimal yearFee = p.feeSet() ? p.yearFee() : ZERO;
		BigDecimal registrationFee = p.feeSet() && p.registrationFee() != null ? p.registrationFee() : ZERO;
		BigDecimal yearFeeDue = floor(yearFee.subtract(p.paidYearFee()));
		BigDecimal registrationDue = floor(registrationFee.subtract(p.paidRegistration()));
		BigDecimal earlier = floor(p.broughtForward().subtract(p.paidPrevious()));
		BigDecimal excess = floor(p.paidYearFee().subtract(yearFee)).add(floor(p.paidRegistration().subtract(registrationFee)));
		BigDecimal total = yearFee.add(registrationFee).add(p.broughtForward());
		BigDecimal paid = p.paidYearFee().add(p.paidRegistration()).add(p.paidPrevious());
		BigDecimal pending = yearFeeDue.add(registrationDue).add(earlier);
		return new FeeBalance(p.dueDate(), two(yearFee), two(registrationFee), two(p.broughtForward()), two(total),
				two(paid), two(pending), two(yearFeeDue), two(registrationDue), two(earlier), two(excess),
				paidUpTo(p), two(p.paidPrevious()));
	}

	private static PaidUpToView paidUpTo(Position p) {
		if (!p.feeSet()) {
			return null;
		}
		PaidUpTo rule = PaidUpTo.of(p.yearStart(), p.yearEnd(), p.yearFee(), p.paidYearFee());
		return switch (rule.state()) {
			case NOTHING_PAID -> new PaidUpToView(PaidUpToView.NOTHING, null);
			case DATE -> new PaidUpToView(PaidUpToView.DATE, rule.lastPaidDay());
			case FULLY_PAID -> new PaidUpToView(PaidUpToView.FULL, p.yearEnd());
		};
	}

	private static BigDecimal floor(BigDecimal value) {
		return value.signum() < 0 ? BigDecimal.ZERO : value;
	}

	private static BigDecimal two(BigDecimal value) {
		return value.setScale(2, java.math.RoundingMode.UNNECESSARY);
	}

}
