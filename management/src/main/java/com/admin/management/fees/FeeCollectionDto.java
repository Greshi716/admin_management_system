package com.admin.management.fees;

import java.time.LocalDate;
import java.util.List;

/** What the Collect fee screen needs for one student (contract FeeCollection). */
public record FeeCollectionDto(StudentPart student, YearPart currentYear, boolean canPayCurrentYear,
		boolean canPayPreviousYears, boolean chargesRegistrationFee, List<YearOption> earlierYears, FeeBalance balance,
		String balanceVersion, List<ReceiptSummaryDto> receipts) {

	/** Why a current-year receipt is not possible, or FEE_SET when it is. */
	static final String FEE_SET = "FEE_SET";

	static final String FEE_NOT_SET = "FEE_NOT_SET";

	static final String FEE_NOT_FINALISED = "FEE_NOT_FINALISED";

	static final String NO_CLASS_RECORD = "NO_CLASS_RECORD";

	static final String NO_CURRENT_YEAR = "NO_CURRENT_YEAR";

	public record StudentPart(Long id, String name, String grNo, String status, String stdDiv) {
	}

	public record YearPart(Long id, String name, LocalDate startDate, LocalDate endDate, String state) {
	}

	public record YearOption(Long id, String name) {
	}

}
