package com.admin.management.fees;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Which fee applies to a class record (FR-005): a pure rule over the fee rows of one year, with no database and no
 * rounding (FR-011). Build it once per year with {@link #of} and ask it for every class record.
 *
 * <ol>
 * <li>The amount comes from the row with the record's standard and category; else from the row with that standard
 * and no category; else there is no fee ("Fee not set": nothing is invented).</li>
 * <li>The due date comes from the row the amount came from.</li>
 * <li>The registration fee is that of the category row when it has one, else that of the row with no category, else
 * none.</li>
 * <li>A row with amount 0.00 is a fee of zero, not "Fee not set".</li>
 * </ol>
 */
final class FeeSelection {

	/** The fee of one class record. A null amount means "Fee not set"; a null registration fee means none. */
	record Result(BigDecimal amount, BigDecimal registrationFee, LocalDate dueDate) {

		static final Result NOT_SET = new Result(null, null, null);

		boolean isSet() {
			return amount != null;
		}

		/** The yearly fee plus the registration fee, or null when the fee is not set. */
		BigDecimal totalFee() {
			return FeeSelection.totalFee(amount, registrationFee);
		}

	}

	private record Key(Long standardId, Long categoryId) {
	}

	private final Map<Key, FeeRow> byStandardAndCategory = new HashMap<>();

	private FeeSelection(Collection<FeeRow> rows) {
		for (FeeRow row : rows) {
			byStandardAndCategory.put(new Key(row.getStandardId(), row.getCategoryId()), row);
		}
	}

	/** The rule for the rows of one year. */
	static FeeSelection of(Collection<FeeRow> rows) {
		return new FeeSelection(rows);
	}

	/** The fee of a class record with this standard and category; a null category uses the row for all categories. */
	Result select(Long standardId, Long categoryId) {
		FeeRow exact = categoryId == null ? null : byStandardAndCategory.get(new Key(standardId, categoryId));
		FeeRow blank = byStandardAndCategory.get(new Key(standardId, null));
		FeeRow amountRow = exact != null ? exact : blank;
		if (amountRow == null) {
			return Result.NOT_SET;
		}
		BigDecimal registrationFee = exact != null && exact.getRegistrationFee() != null ? exact.getRegistrationFee()
				: blank != null ? blank.getRegistrationFee() : null;
		return new Result(amountRow.getAmount(), registrationFee, amountRow.getDueDate());
	}

	/** The yearly fee plus the registration fee (zero when there is none); null when the yearly fee is not set. */
	static BigDecimal totalFee(BigDecimal amount, BigDecimal registrationFee) {
		if (amount == null) {
			return null;
		}
		return registrationFee == null ? amount : amount.add(registrationFee);
	}

}
